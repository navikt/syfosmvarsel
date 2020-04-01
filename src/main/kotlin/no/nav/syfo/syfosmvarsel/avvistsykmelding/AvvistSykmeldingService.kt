package no.nav.syfo.syfosmvarsel.avvistsykmelding

import java.time.LocalDateTime
import java.util.Collections.singletonMap
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

class AvvistSykmeldingService(private val varselProducer: VarselProducer, private val brukernotifikasjonService: BrukernotifikasjonService) {

    suspend fun opprettVarselForAvvisteSykmeldinger(
        receivedSykmelding: ReceivedSykmelding,
        tjenesterUrl: String,
        loggingMeta: LoggingMeta
    ) {
        try {
            log.info("Mottatt avvist sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            val oppgaveVarsel = receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding, tjenesterUrl)
            varselProducer.sendVarsel(oppgaveVarsel)
            brukernotifikasjonService.opprettBrukernotifikasjon(sykmeldingId = receivedSykmelding.sykmelding.id, mottattDato = receivedSykmelding.mottattDato, tekst = "Du har mottatt en sykmelding som har blitt avvist automatisk av NAV", fnr = receivedSykmelding.personNrPasient, loggingMeta = loggingMeta)
            AVVIST_SM_VARSEL_OPPRETTET.inc()
            log.info("Opprettet oppgavevarsel for avvist sykmelding med {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
            throw e
        }
    }

    fun receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding, tjenesterUrl: String): OppgaveVarsel {
        val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
        return OppgaveVarsel(
            type = "SYKMELDING_AVVIST",
            ressursId = receivedSykmelding.sykmelding.id,
            mottaker = receivedSykmelding.personNrPasient,
            parameterListe = parameterListe(receivedSykmelding.sykmelding.id, tjenesterUrl),
            utlopstidspunkt = utsendelsestidspunkt.plusDays(5), // utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
            utsendelsestidspunkt = utsendelsestidspunkt,
            varseltypeId = "NySykmelding",
            oppgavetype = OPPGAVETYPE,
            oppgaveUrl = lagOppgavelenke(tjenesterUrl),
            repeterendeVarsel = false
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
}
