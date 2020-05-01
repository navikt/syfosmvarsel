package no.nav.syfo.syfosmvarsel

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.time.Duration
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.syfosmvarsel.application.ApplicationServer
import no.nav.syfo.syfosmvarsel.application.RenewVaultService
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentialService
import no.nav.syfo.syfosmvarsel.avvistsykmelding.AvvistSykmeldingService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.nysykmelding.NySykmeldingService
import no.nav.syfo.syfosmvarsel.statusendring.StatusendringService
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getAvvistKafkaConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getBrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getKafkaStatusConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getNyKafkaConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getStoppRevarselProducer
import no.nav.syfo.syfosmvarsel.varselutsending.BestillVarselMHandlingMqProducer
import no.nav.syfo.syfosmvarsel.varselutsending.VarselService
import no.nav.syfo.syfosmvarsel.varselutsending.dkif.DkifClient
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

    val connection = connectionFactory(env).createConnection(vaultSecrets.mqUsername, vaultSecrets.mqPassword)
    connection.start()
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val bestillVarselMHandlingProducer = session.producerForQueue(env.bestvarselmhandlingQueueName)
    val bestillVarselMHandlingMqProducer = BestillVarselMHandlingMqProducer(session, bestillVarselMHandlingProducer)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }
    val httpClient = HttpClient(Apache, config)

    val dkifClient = DkifClient(stsClient = oidcClient, httpClient = httpClient)

    val diskresjonskodeService = createPort<DiskresjonskodePortType>(env.diskresjonskodeEndpointUrl) {
        port { withSTS(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenServiceURL) }
    }

    val varselService = VarselService(diskresjonskodeService, dkifClient, database, bestillVarselMHandlingMqProducer)

    val stoppRevarselProducer = getStoppRevarselProducer(kafkaBaseConfig, env)
    val avvistKafkaConsumer = getAvvistKafkaConsumer(kafkaBaseConfig, env)
    val nyKafkaConsumer = getNyKafkaConsumer(kafkaBaseConfig, env)
    val kafkaStatusConsumer = getKafkaStatusConsumer(vaultSecrets, env)
    val brukernotifikasjonKafkaProducer = getBrukernotifikasjonKafkaProducer(kafkaBaseConfig, env)

    val brukernotifikasjonService = BrukernotifikasjonService(database = database, brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer, servicebruker = vaultSecrets.serviceuserUsername, tjenesterUrl = env.tjenesterUrl)

    val nySykmeldingService = NySykmeldingService(varselService, brukernotifikasjonService)
    val avvistSykmeldingService = AvvistSykmeldingService(varselService, brukernotifikasjonService)
    val statusendringService = StatusendringService(brukernotifikasjonService, stoppRevarselProducer)

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
        blockingApplicationLogicStatusendring(applicationState, kafkaStatusConsumer, statusendringService)
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
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(receivedSykmelding, loggingMeta)
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
            wrapExceptions(loggingMeta) {
                nySykmeldingService.opprettVarselForNySykmelding(receivedSykmelding, loggingMeta)
            }
        }
        delay(100)
    }
}

suspend fun blockingApplicationLogicStatusendring(
    applicationState: ApplicationState,
    kafkaStatusConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService
) {
    while (applicationState.ready) {
        kafkaStatusConsumer.poll(Duration.ofMillis(0)).forEach {
            val sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO = it.value()
            try {
                statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved behandling av statusendring for sykmelding med id {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId)
                throw e
            }
        }
        delay(100)
    }
}
