package no.nav.syfo.syfosmvarsel.statusendring

import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_UTGATT
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO

class StatusendringService(private val brukernotifikasjonService: BrukernotifikasjonService) {

    fun handterStatusendring(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO) {
        if (skalFerdigstilleBrukernotifkasjon(sykmeldingStatusKafkaMessageDTO.event.statusEvent)) {
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)
        } else {
            log.info(
                "Ignorerer statusendring for sykmelding {}, status {}",
                sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId,
                sykmeldingStatusKafkaMessageDTO.event.statusEvent
            )
        }
    }

    private fun skalFerdigstilleBrukernotifkasjon(statusEvent: String): Boolean =
        when (statusEvent) {
            STATUS_AVBRUTT -> true
            STATUS_BEKREFTET -> true
            STATUS_SENDT -> true
            STATUS_UTGATT -> true
            STATUS_SLETTET -> true
            STATUS_APEN -> false
            else -> throw IllegalArgumentException("Unnsupported status $statusEvent")
        }
}
