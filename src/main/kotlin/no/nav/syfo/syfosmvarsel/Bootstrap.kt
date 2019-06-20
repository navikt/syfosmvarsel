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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.syfosmvarsel.api.registerNaisApi
import no.nav.syfo.syfosmvarsel.avvistsykmelding.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.avvistsykmelding.opprettVarselForAvvisteSykmeldinger
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

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

    launchListeners(env, applicationState, consumerProperties, producerProperties)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    producerProperties: Properties
) {
    val listeners = 0.until(env.applicationThreads).map {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.avvistSykmeldingTopic))

                val kafkaproducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)

                createListener(applicationState) {
                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, env)
            }
        }.toList()

        applicationState.initialized = true
        listeners.forEach { it.join() }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    kafkaproducer: KafkaProducer<String, OppgaveVarsel>,
    env: Environment
) {
    while (applicationState.running) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            opprettVarselForAvvisteSykmeldinger(it, kafkaproducer,
                    env.oppgavevarselTopic, env.tjenesterUrl)
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
