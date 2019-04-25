package no.nav.syfo.avvistsykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.metrics.AVVIST_SYKMELDING_MOTTATT
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

private val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

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
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            log.info("Mottatt avvist sykmelding med id ${receivedSykmelding.msgId}")
            AVVIST_SYKMELDING_MOTTATT.inc()

            val oppgaveVarsel = receivedSykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)

            kafkaproducer.send(ProducerRecord(oppgavevarselTopic, oppgaveVarsel))
            log.info("Opprettet oppgavevarsel for avvist sykmelding med id ${receivedSykmelding.msgId}")
        }
        delay(100)
    }
}

fun receivedSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
    val utsendelsestidspunkt = now().plusDays(1).atTime(9, 0)
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
