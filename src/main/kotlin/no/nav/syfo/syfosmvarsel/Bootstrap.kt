package no.nav.syfo.syfosmvarsel

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.api.registerNaisApi
import no.nav.syfo.syfosmvarsel.avvistsykmelding.opprettVarselForAvvisteSykmeldinger
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.nysykmelding.opprettVarselForNySykmelding
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smvarsel")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

fun main() = runBlocking(coroutineContext) {
    val env = Environment()
    val vaultSecrets =
            objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
            .envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
    )

    val producerProperties = kafkaBaseConfig.toProducerConfig(
            "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        install(MicrometerMetrics) {
            registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)
            meterBinders = listOf(
                ClassLoaderMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                ProcessorMetrics(),
                JvmThreadMetrics(),
                LogbackMetrics()
            )
        }
        initRouting(applicationState)
    }.start(wait = false)

    launchListeners(env, vaultSecrets, applicationState, consumerProperties, producerProperties)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun CoroutineScope.createListener(applicationState: ApplicationState, applicationLogic: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                applicationLogic()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
    env: Environment,
    vaultSecrets: VaultSecrets,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    producerProperties: Properties
) {
    val diskresjonskodeService = createPort<DiskresjonskodePortType>(env.diskresjonskodeEndpointUrl) {
        port { withSTS(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenServiceURL) }
    }

    val kafkaProducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)
    val varselProducer = VarselProducer(diskresjonskodeService, kafkaProducer, env.oppgavevarselTopic)

    val avvistSykmeldingListeners = 0.until(env.applicationThreads).map {
        val avvistKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
        avvistKafkaConsumer.subscribe(listOf(env.avvistSykmeldingTopic))

        createListener(applicationState) {
            blockingApplicationLogicAvvistSykmelding(applicationState, avvistKafkaConsumer, varselProducer, env)
        }
    }.toList()

    val nySykmeldingListeners = 0.until(env.applicationThreads).map {
        val nyKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
        nyKafkaConsumer.subscribe(listOf(env.sykmeldingAutomatiskBehandlingTopic, env.sykmeldingManuellBehandlingTopic))

        createListener(applicationState) {
            blockingApplicationLogicNySykmelding(applicationState, nyKafkaConsumer, varselProducer, env)
        }
    }.toList()

    applicationState.initialized = true
    runBlocking { (avvistSykmeldingListeners + nySykmeldingListeners).forEach { it.join() } }
}

suspend fun blockingApplicationLogicAvvistSykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    varselProducer: VarselProducer,
    env: Environment
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
            )
            wrapExceptions(loggingMeta) {
                opprettVarselForAvvisteSykmeldinger(receivedSykmelding, varselProducer, env.tjenesterUrl, loggingMeta)
            }
        }
        delay(100)
    }
}

suspend fun blockingApplicationLogicNySykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    varselProducer: VarselProducer,
    env: Environment
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id
            )

            if (env.cluster == "dev-fss") {
                wrapExceptions(loggingMeta) {
                opprettVarselForNySykmelding(receivedSykmelding, varselProducer, env.tjenesterUrl, loggingMeta)
                }
            } else {
                log.info("Oppretter ikke varsel for ny sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            }
        }
        delay(100)
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}