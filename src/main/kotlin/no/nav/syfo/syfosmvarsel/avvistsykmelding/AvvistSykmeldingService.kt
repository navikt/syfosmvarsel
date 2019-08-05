package no.nav.syfo.syfosmvarsel.avvistsykmelding

import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_MOTTATT
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import java.time.LocalDateTime
import java.util.Collections.singletonMap

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

fun opprettVarselForAvvisteSykmeldinger(
    receivedSykmelding: ReceivedSykmelding,
    varselProducer: VarselProducer,
    tjenesterUrl: String,
    loggingMeta: LoggingMeta
) {
    try {
        log.info("Mottatt avvist sykmelding med id {}, $loggingMeta", receivedSykmelding.sykmelding.id, *loggingMeta.logValues)
        AVVIST_SM_MOTTATT.inc()

        val oppgaveVarsel = receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)
        varselProducer.sendVarsel(oppgaveVarsel)
        AVVIST_SM_VARSEL_OPPRETTET.inc()
        log.info("Opprettet oppgavevarsel for avvist sykmelding med {}, $loggingMeta", receivedSykmelding.sykmelding.id, *loggingMeta.logValues)
    } catch (e: Exception) {
        log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
        throw e
    }
}

fun receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
    val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
    return OppgaveVarsel(
            "SYKMELDING_AVVIST",
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
    return singletonMap<String, String>("url", lagHenvendelselenke(sykmeldingId, tjenesterUrl))
}

private fun lagHenvendelselenke(sykmeldingId: String, tjenesterUrl: String): String {
    return "$tjenesterUrl/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/$sykmeldingId"
}

private fun lagOppgavelenke(tjenesterUrl: String): String {
    return "$tjenesterUrl/sykefravaer"
}
