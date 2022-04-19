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
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.exception.ServiceUnavailableException
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
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getKafkaStatusConsumerAiven
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getNyKafkaAivenConsumer
import no.nav.syfo.util.util.Unbounded
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.time.Duration
import java.util.UUID

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smvarsel")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
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

    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )

    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, env.pdlScope)

    val kafkaStatusConsumerAiven = getKafkaStatusConsumerAiven(env)
    val nySykmeldingConsumerAiven = getNyKafkaAivenConsumer(env)
    val brukernotifikasjonKafkaProducer = getBrukernotifikasjonKafkaProducer(env)

    val brukernotifikasjonService = BrukernotifikasjonService(
        database = database,
        brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
        dittSykefravaerUrl = env.dittSykefravaerUrl,
        pdlPersonService = pdlService
    )

    val nySykmeldingService = NySykmeldingService(brukernotifikasjonService)
    val avvistSykmeldingService = AvvistSykmeldingService(brukernotifikasjonService)
    val statusendringService = StatusendringService(brukernotifikasjonService)

    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    launchListeners(
        applicationState = applicationState,
        nySykmeldingService = nySykmeldingService,
        avvistSykmeldingService = avvistSykmeldingService,
        kafkaStatusConsumerAiven = kafkaStatusConsumerAiven,
        statusendringService = statusendringService,
        environment = env,
        nyKafkaConsumerAiven = nySykmeldingConsumerAiven
    )
}

@DelicateCoroutinesApi
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

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    nySykmeldingService: NySykmeldingService,
    avvistSykmeldingService: AvvistSykmeldingService,
    kafkaStatusConsumerAiven: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService,
    environment: Environment,
    nyKafkaConsumerAiven: KafkaConsumer<String, String>
) {
    createListener(applicationState) {
        blockingApplicationLogicNySykmelding(applicationState, nyKafkaConsumerAiven, nySykmeldingService, avvistSykmeldingService, environment, "aiven")
    }

    createListener(applicationState) {
        blockingApplicationLogicStatusendringAiven(applicationState, statusendringService, kafkaStatusConsumerAiven)
    }
}

suspend fun blockingApplicationLogicNySykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    nySykmeldingService: NySykmeldingService,
    avvistSykmeldingService: AvvistSykmeldingService,
    environment: Environment,
    source: String
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofMillis(1000)).filterNot { it.value() == null }.forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id,
                source = source
            )
            wrapExceptions(loggingMeta) {
                when (it.topic()) {
                    environment.avvistSykmeldingTopicAiven -> avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(receivedSykmelding, loggingMeta)
                    else -> nySykmeldingService.opprettVarselForNySykmelding(receivedSykmelding, loggingMeta)
                }
            }
        }
    }
}

fun blockingApplicationLogicStatusendringAiven(
    applicationState: ApplicationState,
    statusendringService: StatusendringService,
    kafkaStatusConsumerAiven: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>
) {
    while (applicationState.ready) {
        kafkaStatusConsumerAiven.poll(Duration.ofMillis(1000))
            .filter { it.value() != null }
            .filter { it.key().erUuid() }
            .forEach {
                val sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO = it.value()
                try {
                    log.info("Mottatt statusmelding fra aiven ${sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId}")
                    statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved behandling av statusendring fra aiven for sykmelding med id {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId)
                    throw e
                }
            }
    }
}

fun String.erUuid(): Boolean {
    return try {
        UUID.fromString(this)
        true
    } catch (e: Exception) {
        log.warn("Sykmeldingid $this er ikke uuid, ignorerer melding")
        false
    }
}
