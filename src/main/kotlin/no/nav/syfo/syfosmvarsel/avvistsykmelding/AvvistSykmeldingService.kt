package no.nav.syfo.syfosmvarsel.avvistsykmelding

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.AVVIST_SM_VARSEL_OPPRETTET

@KtorExperimentalAPI
class AvvistSykmeldingService(private val brukernotifikasjonService: BrukernotifikasjonService) {

    suspend fun opprettVarselForAvvisteSykmeldinger(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ) {
        try {
            log.info("Mottatt avvist sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            brukernotifikasjonService.opprettBrukernotifikasjon(sykmeldingId = receivedSykmelding.sykmelding.id, mottattDato = receivedSykmelding.mottattDato, tekst = "Du har mottatt en sykmelding som har blitt avvist automatisk av NAV", fnr = receivedSykmelding.personNrPasient, loggingMeta = loggingMeta)
            AVVIST_SM_VARSEL_OPPRETTET.inc()
            log.info("Opprettet oppgavevarsel for avvist sykmelding med {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved oppretting av varsel for avvist sykmelding")
            throw e
        }
    }

    fun tombstoneSykmelding(sykmeldingId: String) {
        log.info("Mottatt tombstone-event for avvist sykmelding med id $sykmeldingId")
        brukernotifikasjonService.ferdigstillBrukernotifikasjonVedTombstone(sykmeldingId)
    }
}
