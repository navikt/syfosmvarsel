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
import java.net.ProxySelector
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.application.ApplicationServer
import no.nav.syfo.syfosmvarsel.application.ApplicationState
import no.nav.syfo.syfosmvarsel.application.RenewVaultService
import no.nav.syfo.syfosmvarsel.application.createApplicationEngine
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentialService
import no.nav.syfo.syfosmvarsel.avvistsykmelding.AvvistSykmeldingService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.client.AccessTokenClientV2
import no.nav.syfo.syfosmvarsel.nysykmelding.NySykmeldingService
import no.nav.syfo.syfosmvarsel.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.pdl.service.PdlPersonService
import no.nav.syfo.syfosmvarsel.statusendring.StatusendringService
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getBrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getKafkaStatusConsumer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getNyKafkaConsumer
import no.nav.syfo.util.util.Unbounded
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
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
    val vaultServiceUser = VaultServiceUser()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    val kafkaBaseConfig = loadBaseConfig(env, vaultServiceUser).envOverrides()
    kafkaBaseConfig["auto.offset.reset"] = "none"

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

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClient = HttpClient(Apache, config)
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithProxy)

    val pdlClient = PdlClient(httpClient,
            env.pdlGraphqlPath,
            PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), ""))

    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, env.pdlScope)

    val nyKafkaConsumer = getNyKafkaConsumer(kafkaBaseConfig, env)
    val kafkaStatusConsumer = getKafkaStatusConsumer(kafkaBaseConfig, env)
    val brukernotifikasjonKafkaProducer = getBrukernotifikasjonKafkaProducer(kafkaBaseConfig, env)

    val brukernotifikasjonService = BrukernotifikasjonService(database = database, brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
        servicebruker = vaultServiceUser.serviceuserUsername, dittSykefravaerUrl = env.dittSykefravaerUrl, pdlPersonService = pdlService)

    val nySykmeldingService = NySykmeldingService(brukernotifikasjonService)
    val avvistSykmeldingService = AvvistSykmeldingService(brukernotifikasjonService)
    val statusendringService = StatusendringService(brukernotifikasjonService)

    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    launchListeners(
        applicationState = applicationState,
        nyKafkaConsumer = nyKafkaConsumer,
        nySykmeldingService = nySykmeldingService,
        avvistSykmeldingService = avvistSykmeldingService,
        kafkaStatusConsumer = kafkaStatusConsumer,
        statusendringService = statusendringService,
        environment = env
    )
}

fun createListener(applicationState: ApplicationState, applicationLogic: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Unbounded) {
            try {
                applicationLogic()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.alive = false
                applicationState.ready = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    nyKafkaConsumer: KafkaConsumer<String, String>,
    nySykmeldingService: NySykmeldingService,
    avvistSykmeldingService: AvvistSykmeldingService,
    kafkaStatusConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService,
    environment: Environment
) {
    createListener(applicationState) {
        blockingApplicationLogicNySykmelding(applicationState, nyKafkaConsumer, nySykmeldingService, avvistSykmeldingService, environment)
    }

    createListener(applicationState) {
        blockingApplicationLogicStatusendring(applicationState, kafkaStatusConsumer, statusendringService)
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogicNySykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    nySykmeldingService: NySykmeldingService,
    avvistSykmeldingService: AvvistSykmeldingService,
    environment: Environment
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofMillis(1000)).filterNot { it.value() == null }.forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id
            )
            wrapExceptions(loggingMeta) {
                when (it.topic()) {
                    environment.avvistSykmeldingTopic -> avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(receivedSykmelding, loggingMeta)
                    else -> nySykmeldingService.opprettVarselForNySykmelding(receivedSykmelding, loggingMeta)
                }
            }
        }
    }
}

fun blockingApplicationLogicStatusendring(
    applicationState: ApplicationState,
    kafkaStatusConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService
) {
    while (applicationState.ready) {
        kafkaStatusConsumer.poll(Duration.ofMillis(1000)).filterNot { it.value() == null }.forEach {
            val sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO = it.value()
            try {
                statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved behandling av statusendring for sykmelding med id {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId)
                throw e
            }
        }
    }
}
