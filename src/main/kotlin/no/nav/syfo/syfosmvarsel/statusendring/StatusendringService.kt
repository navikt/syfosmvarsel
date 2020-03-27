package no.nav.syfo.syfosmvarsel.statusendring

import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.objectMapper

class StatusendringService(private val brukernotifikasjonService: BrukernotifikasjonService) {

    fun handterStatusendring(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO) {
        log.info("Statusendring for melding {}", objectMapper.writeValueAsString(sykmeldingStatusKafkaMessageDTO))
        if (skalFerdigstilleBrukernotifkasjon(sykmeldingStatusKafkaMessageDTO.event.statusEvent)) {
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)
        } else {
            log.info("Ignorerer statusendring for sykmelding {}, status {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId, sykmeldingStatusKafkaMessageDTO.event.statusEvent.name)
        }
    }

    private fun skalFerdigstilleBrukernotifkasjon(statusEventDTO: StatusEventDTO): Boolean =
        when (statusEventDTO) {
            StatusEventDTO.AVBRUTT -> true
            StatusEventDTO.BEKREFTET -> true
            StatusEventDTO.SENDT -> true
            StatusEventDTO.UTGATT -> true
            StatusEventDTO.APEN -> false
        }
}
