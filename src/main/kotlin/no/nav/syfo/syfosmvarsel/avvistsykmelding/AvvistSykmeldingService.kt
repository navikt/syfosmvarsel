package no.nav.syfo.syfosmvarsel.avvistsykmelding

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import no.nav.syfo.syfosmvarsel.varselutsending.VarselService

@KtorExperimentalAPI
class AvvistSykmeldingService(private val varselService: VarselService, private val brukernotifikasjonService: BrukernotifikasjonService) {

    suspend fun opprettVarselForAvvisteSykmeldinger(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ) {
        try {
            log.info("Mottatt avvist sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            val oppgaveVarsel = receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding)
            varselService.sendVarsel(oppgaveVarsel, receivedSykmelding.sykmelding.id)
            brukernotifikasjonService.opprettBrukernotifikasjon(sykmeldingId = receivedSykmelding.sykmelding.id, mottattDato = receivedSykmelding.mottattDato, tekst = "Du har mottatt en sykmelding som har blitt avvist automatisk av NAV", fnr = receivedSykmelding.personNrPasient, loggingMeta = loggingMeta)
            AVVIST_SM_VARSEL_OPPRETTET.inc()
            log.info("Opprettet oppgavevarsel for avvist sykmelding med {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
            throw e
        }
    }

    fun receivedAvvistSykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding): OppgaveVarsel {
        val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
        return OppgaveVarsel(
            ressursId = receivedSykmelding.sykmelding.id,
            mottaker = receivedSykmelding.personNrPasient,
            utlopstidspunkt = utsendelsestidspunkt.plusDays(5), // utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
            utsendelsestidspunkt = utsendelsestidspunkt,
            varseltypeId = "NySykmeldingUtenLenke",
            varselbestillingId = UUID.randomUUID()
        )
    }
}
