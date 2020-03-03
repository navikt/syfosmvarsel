package no.nav.syfo.syfosmvarsel

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.time.Duration
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
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.application.ApplicationServer
import no.nav.syfo.syfosmvarsel.application.RenewVaultService
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentialService
import no.nav.syfo.syfosmvarsel.avvistsykmelding.AvvistSykmeldingService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.nysykmelding.NySykmeldingService
import no.nav.syfo.syfosmvarsel.statusendring.StatusendringService
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getAvvistKafkaConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getKafkaStatusConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getNyKafkaConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getVarselProducer
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import org.apache.kafka.clients.consumer.KafkaConsumer
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

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()

    val diskresjonskodeService = createPort<DiskresjonskodePortType>(env.diskresjonskodeEndpointUrl) {
        port { withSTS(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenServiceURL) }
    }

    val varselProducer = getVarselProducer(kafkaBaseConfig, env, diskresjonskodeService)
    val avvistKafkaConsumer = getAvvistKafkaConsumer(kafkaBaseConfig, env)
    val nyKafkaConsumer = getNyKafkaConsumer(kafkaBaseConfig, env)
    val kafkaStatusConsumer = getKafkaStatusConsumer(vaultSecrets, env)

    val brukernotifikasjonService = BrukernotifikasjonService(database)

    val nySykmeldingService = NySykmeldingService(varselProducer, brukernotifikasjonService)
    val avvistSykmeldingService = AvvistSykmeldingService(varselProducer, brukernotifikasjonService, env.cluster)
    val statusendringService = StatusendringService(brukernotifikasjonService)

    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    launchListeners(
        env = env,
        applicationState = applicationState,
        avvistKafkaConsumer = avvistKafkaConsumer,
        nyKafkaConsumer = nyKafkaConsumer,
        nySykmeldingService = nySykmeldingService,
        avvistSykmeldingService = avvistSykmeldingService,
        kafkaStatusConsumer = kafkaStatusConsumer,
        statusendringService = statusendringService
    )
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
    avvistKafkaConsumer: KafkaConsumer<String, String>,
    nyKafkaConsumer: KafkaConsumer<String, String>,
    nySykmeldingService: NySykmeldingService,
    avvistSykmeldingService: AvvistSykmeldingService,
    kafkaStatusConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService
) {
    createListener(applicationState) {
        blockingApplicationLogicAvvistSykmelding(applicationState, avvistKafkaConsumer, avvistSykmeldingService, env)
    }

    createListener(applicationState) {
        blockingApplicationLogicNySykmelding(applicationState, nyKafkaConsumer, nySykmeldingService, env)
    }

    createListener(applicationState) {
        blockingApplicationLogicStatusendring(applicationState, kafkaStatusConsumer, statusendringService, env)
    }
}

suspend fun blockingApplicationLogicAvvistSykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    avvistSykmeldingService: AvvistSykmeldingService,
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
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(receivedSykmelding, env.tjenesterUrl, loggingMeta)
            }
        }
        delay(100)
    }
}

suspend fun blockingApplicationLogicNySykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    nySykmeldingService: NySykmeldingService,
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
                    nySykmeldingService.opprettVarselForNySykmelding(receivedSykmelding, env.tjenesterUrl, loggingMeta)
                }
            } else {
                log.info("Oppretter ikke varsel for ny sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            }
        }
        delay(100)
    }
}

suspend fun blockingApplicationLogicStatusendring(
    applicationState: ApplicationState,
    kafkaStatusConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService,
    env: Environment
) {
    while (applicationState.ready) {
        kafkaStatusConsumer.poll(Duration.ofMillis(0)).forEach {
            val sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO = it.value()

            if (env.cluster == "dev-fss") {
                try {
                    statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved behandling av statusendring for sykmelding med id {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId)
                    throw e
                }
            } else {
                log.info("Behandler ikke statusendring for sykmelding med id {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId)
            }
        }
        delay(100)
    }
}
