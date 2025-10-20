package no.nav.syfo.syfosmvarsel

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.syfosmvarsel.application.ApplicationServer
import no.nav.syfo.syfosmvarsel.application.ApplicationState
import no.nav.syfo.syfosmvarsel.application.createApplicationEngine
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.Brukernotifikasjon
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.model.Status
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.statusendring.StatusendringService
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getBrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getKafkaStatusConsumerAiven
import no.nav.syfo.syfosmvarsel.util.KafkaFactory.Companion.getSykmeldingnotifikasjonKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smvarsel")

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

data class Sykmeldingnotifikasjon(
    val sykmeldingId: String,
    val status: Status,
    val mottattDato: LocalDateTime,
    val fnr: String,
)

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val database = Database(env)

    val applicationState = ApplicationState()
    val applicationEngine =
        createApplicationEngine(
            env,
            applicationState,
        )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    val kafkaStatusConsumerAiven = getKafkaStatusConsumerAiven(env)
    val sykmeldingnotifikasjonConsumer = getSykmeldingnotifikasjonKafkaConsumer(env)
    val brukernotifikasjonKafkaProducer = getBrukernotifikasjonKafkaProducer(env)

    val brukernotifikasjonService =
        BrukernotifikasjonService(
            database = database,
            brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
            dittSykefravaerUrl = env.dittSykefravaerUrl,
            env.cluster
        )

    val statusendringService = StatusendringService(brukernotifikasjonService)

    launchListeners(
        applicationState = applicationState,
        brukernotifikasjonService = brukernotifikasjonService,
        kafkaStatusConsumerAiven = kafkaStatusConsumerAiven,
        statusendringService = statusendringService,
        sykmeldingnotifikasjonConsumer = sykmeldingnotifikasjonConsumer,
    )
    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    applicationLogic: suspend CoroutineScope.() -> Unit
): Job =
    GlobalScope.launch(Dispatchers.IO) {
        try {
            applicationLogic()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta),
                e.cause,
            )
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    brukernotifikasjonService: BrukernotifikasjonService,
    kafkaStatusConsumerAiven: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService,
    sykmeldingnotifikasjonConsumer: KafkaConsumer<String, String>,
) {
    createListener(applicationState) {
        blockingApplicationLogicSykmeldingnotifikasjon(
            applicationState,
            sykmeldingnotifikasjonConsumer,
            brukernotifikasjonService,
        )
    }

    createListener(applicationState) {
        blockingApplicationLogicStatusendringAiven(
            applicationState,
            statusendringService,
            kafkaStatusConsumerAiven,
        )
    }
}

suspend fun blockingApplicationLogicSykmeldingnotifikasjon(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    brukernotifikasjonService: BrukernotifikasjonService,
) {
    while (applicationState.ready) {
        kafkaConsumer
            .poll(Duration.ofMillis(1000))
            .filterNot { it.value() == null }
            .forEach { handleSykmeldingnotifikasjon(it, brukernotifikasjonService) }
    }
}

private fun getBrukernotifikasjon(
    record: ConsumerRecord<String, String>,
): Brukernotifikasjon {

    val sykmeldingnotifikasjon: Sykmeldingnotifikasjon = objectMapper.readValue(record.value())
    val brukernotifikasjon =
        Brukernotifikasjon(
            sykmeldingId = sykmeldingnotifikasjon.sykmeldingId,
            mottattDato = sykmeldingnotifikasjon.mottattDato,
            tekst = getNotifikasjonsTekst(sykmeldingnotifikasjon.status),
            fnr = sykmeldingnotifikasjon.fnr,
        )
    log.info(
        "creating brukernotfikasjon from ${record.topic()} for status: ${sykmeldingnotifikasjon.status} brukernotfikasjon: ${brukernotifikasjon.copy(fnr = "xxxxxxxxxxx")}"
    )
    return brukernotifikasjon
}

private fun getNotifikasjonsTekst(status: Status) =
    when (status) {
        Status.INVALID -> "Du har mottatt en sykmelding som har blitt avvist automatisk av Nav"
        else -> "Du har mottatt en ny sykmelding"
    }

@WithSpan
private suspend fun handleSykmeldingnotifikasjon(
    message: ConsumerRecord<String, String>,
    brukernotifikasjonService: BrukernotifikasjonService
) {
    val brukernotifikasjon = getBrukernotifikasjon(message)
    brukernotifikasjonService.opprettBrukernotifikasjon(brukernotifikasjon)
}

fun blockingApplicationLogicStatusendringAiven(
    applicationState: ApplicationState,
    statusendringService: StatusendringService,
    kafkaStatusConsumerAiven: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
) {
    while (applicationState.ready) {
        kafkaStatusConsumerAiven
            .poll(Duration.ofMillis(1000))
            .filter { it.value() != null }
            .filter { it.key().erUuid() }
            .forEach { handleStatusEndring(it, statusendringService) }
    }
}

@WithSpan
private fun handleStatusEndring(
    it: ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>,
    statusendringService: StatusendringService
) {
    val sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO = it.value()

    val currentSpan = Span.current()
    currentSpan.setAttribute("sykmeldingId", sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

    try {
        log.info(
            "Mottatt statusmelding fra aiven ${sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId}",
        )
        statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)
    } catch (e: Exception) {
        log.error(
            "Noe gikk galt ved behandling av statusendring fra aiven for sykmelding med id {}",
            sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId,
        )
        throw e
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
