package no.nav.syfo.avvistsykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.metrics.AVVIST_SM_MOTTATT
import no.nav.syfo.metrics.AVVIST_SM_VARSEL_OPPRETTET
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.Collections.singletonMap
import java.util.concurrent.ThreadLocalRandom

private val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

suspend fun opprettVarselForAvvisteSykmeldinger(
        applicationState: ApplicationState,
        kafkaconsumer: KafkaConsumer<String, String>,
        kafkaproducer: KafkaProducer<String, OppgaveVarsel>,
        oppgavevarselTopic: String,
        tjenesterUrl: String
) {
    while (applicationState.running) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            try {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

                log.info("Mottatt avvist sykmelding med id ${receivedSykmelding.msgId}")
                AVVIST_SM_MOTTATT.inc()

                val oppgaveVarsel = receivedSykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)

                kafkaproducer.send(ProducerRecord(oppgavevarselTopic, oppgaveVarsel))
                AVVIST_SM_VARSEL_OPPRETTET.inc()
                log.info("Opprettet oppgavevarsel for avvist sykmelding med id ${receivedSykmelding.msgId}")
            } catch (e: Exception) {
                log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
                throw e
            }
        }
        delay(100)
    }
}

fun receivedSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
    val utsendelsestidspunkt = now().plusDays(1).atTime(ThreadLocalRandom.current().nextInt(9, 14), ThreadLocalRandom.current().nextInt(0, 59))
    return OppgaveVarsel(
            "SYKMELDING_AVVIST",
            receivedSykmelding.sykmelding.id,
            receivedSykmelding.personNrPasient,
            parameterListe(receivedSykmelding.sykmelding.id, tjenesterUrl),
            utsendelsestidspunkt.plusDays(10),
            utsendelsestidspunkt,
            "NySykmelding",
            OPPGAVETYPE,
            lagOppgavelenke(tjenesterUrl),
            false
    )
}

private fun parameterListe(sykmeldingId: String, tjenesterUrl: String): Map<String, String> {
    return singletonMap<String, String>("url", lagHenvendelselenke(sykmeldingId, tjenesterUrl))
}

private fun lagHenvendelselenke(sykmeldingId: String, tjenesterUrl: String): String {
    return "$tjenesterUrl/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/$sykmeldingId"
}

private fun lagOppgavelenke(tjenesterUrl: String): String {
    return "$tjenesterUrl/sykefravaer"
}

data class OppgaveVarsel(
        val type: String,
        val ressursId: String,
        val mottaker: String,
        val parameterListe: Map<String, String>,
        val utlopstidspunkt: LocalDateTime,
        val utsendelsestidspunkt: LocalDateTime,
        val varseltypeId: String,
        val oppgavetype: String,
        val oppgaveUrl: String,
        val repeterendeVarsel: Boolean
)
