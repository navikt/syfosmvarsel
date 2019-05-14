package no.nav.syfo.syfosmvarsel.avvistsykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_MOTTATT
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.objectMapper
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.Collections.singletonMap

private val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

fun opprettVarselForAvvisteSykmeldinger(
        cr: ConsumerRecord<String, String>,
        kafkaproducer: KafkaProducer<String, OppgaveVarsel>,
        oppgavevarselTopic: String,
        tjenesterUrl: String
) {
    try {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(cr.value())
        val logValues = arrayOf(
                StructuredArguments.keyValue("mottakId", receivedSykmelding.navLogId),
                StructuredArguments.keyValue("organizationNumber", receivedSykmelding.legekontorOrgNr),
                StructuredArguments.keyValue("msgId", receivedSykmelding.msgId),
                StructuredArguments.keyValue("sykmeldingId", receivedSykmelding.sykmelding.id)
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        log.info("Mottatt avvist sykmelding med id {}, $logKeys", receivedSykmelding.sykmelding.id, *logValues)
        AVVIST_SM_MOTTATT.inc()

        val oppgaveVarsel = receivedSykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)
        kafkaproducer.send(ProducerRecord(oppgavevarselTopic, oppgaveVarsel))
        AVVIST_SM_VARSEL_OPPRETTET.inc()
        log.info("Opprettet oppgavevarsel for avvist sykmelding med {}, $logKeys", receivedSykmelding.sykmelding.id, *logValues)
    } catch (e: Exception) {
        log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
        throw e
    }
}

fun receivedSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
    val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
    return OppgaveVarsel(
            "SYKMELDING_AVVIST",
            receivedSykmelding.sykmelding.id,
            receivedSykmelding.personNrPasient,
            parameterListe(receivedSykmelding.sykmelding.id, tjenesterUrl),
            utsendelsestidspunkt.plusDays(5),// utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
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
