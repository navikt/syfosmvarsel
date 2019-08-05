package no.nav.syfo.syfosmvarsel.nysykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.metrics.NY_SM_MOTTATT
import no.nav.syfo.syfosmvarsel.metrics.NY_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.objectMapper
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

fun opprettVarselForNySykmelding(
    cr: ConsumerRecord<String, String>,
    varselProducer: VarselProducer,
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

        log.info("Mottatt ny sykmelding med id {}, $logKeys", receivedSykmelding.sykmelding.id, *logValues)
        NY_SM_MOTTATT.inc()

        val oppgaveVarsel = receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)
        varselProducer.sendVarsel(oppgaveVarsel)
        NY_SM_VARSEL_OPPRETTET.inc()
        log.info("Opprettet oppgavevarsel for ny sykmelding med {}, $logKeys", receivedSykmelding.sykmelding.id, *logValues)
    } catch (e: Exception) {
        log.error("Det skjedde en feil ved oppretting av varsel for ny sykmelding")
        throw e
    }
}

fun receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
    val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
    return OppgaveVarsel(
            "NY_SYKMELDING",
            receivedSykmelding.sykmelding.id,
            receivedSykmelding.personNrPasient,
            parameterListe(receivedSykmelding.sykmelding.id, tjenesterUrl),
            utsendelsestidspunkt.plusDays(5), // utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
            utsendelsestidspunkt,
            "NySykmelding",
            OPPGAVETYPE,
            lagOppgavelenke(tjenesterUrl),
            false
    )
}

private fun parameterListe(sykmeldingId: String, tjenesterUrl: String): Map<String, String> {
    return Collections.singletonMap<String, String>("url", lagHenvendelselenke(sykmeldingId, tjenesterUrl))
}

private fun lagHenvendelselenke(sykmeldingId: String, tjenesterUrl: String): String {
    return "$tjenesterUrl/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/$sykmeldingId"
}

private fun lagOppgavelenke(tjenesterUrl: String): String {
    return "$tjenesterUrl/sykefravaer"
}