package no.nav.syfo.syfosmvarsel

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.application.ApplicationServer
import no.nav.syfo.syfosmvarsel.application.RenewVaultService
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentialService
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

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smvarsel")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets =
            objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
            .envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
    )

    val producerProperties = kafkaBaseConfig.toProducerConfig(
            "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)

    val diskresjonskodeService = createPort<DiskresjonskodePortType>(env.diskresjonskodeEndpointUrl) {
        port { withSTS(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenServiceURL) }
    }

    val kafkaProducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)
    val varselProducer = VarselProducer(diskresjonskodeService, kafkaProducer, env.oppgavevarselTopic)

    applicationState.ready = true
    launchListeners(env, applicationState, consumerProperties, varselProducer)
}

fun createListener(applicationState: ApplicationState, applicationLogic: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                applicationLogic()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.alive = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    varselProducer: VarselProducer
) {

    val avvistKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    avvistKafkaConsumer.subscribe(listOf(env.avvistSykmeldingTopic))

    createListener(applicationState) {
        blockingApplicationLogicAvvistSykmelding(applicationState, avvistKafkaConsumer, varselProducer, env)
    }

    val nyKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    nyKafkaConsumer.subscribe(listOf(env.sykmeldingAutomatiskBehandlingTopic, env.sykmeldingManuellBehandlingTopic))

    createListener(applicationState) {
        blockingApplicationLogicNySykmelding(applicationState, nyKafkaConsumer, varselProducer, env)
    }
}

suspend fun blockingApplicationLogicAvvistSykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    varselProducer: VarselProducer,
    env: Environment
) {
    while (applicationState.ready) {
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
    while (applicationState.ready) {
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
