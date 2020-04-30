package no.nav.syfo.syfosmvarsel.statusendring

import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_STOPPET
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarsel
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarselProducer

class StatusendringService(private val brukernotifikasjonService: BrukernotifikasjonService, private val stoppRevarselProducer: StoppRevarselProducer, private val cluster: String) {

    fun handterStatusendring(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO) {
        if (skalFerdigstilleBrukernotifkasjonOgStoppeRevarsel(sykmeldingStatusKafkaMessageDTO.event.statusEvent)) {
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)
            if (cluster == "dev-fss") {
                stoppRevarselProducer.sendStoppRevarsel(StoppRevarsel(type = "NY_SYKMELDING", ressursId = sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId))
                SM_VARSEL_STOPPET.inc()
            }
        } else {
            log.info("Ignorerer statusendring for sykmelding {}, status {}", sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId, sykmeldingStatusKafkaMessageDTO.event.statusEvent.name)
        }
    }

    private fun skalFerdigstilleBrukernotifkasjonOgStoppeRevarsel(statusEventDTO: StatusEventDTO): Boolean =
        when (statusEventDTO) {
            StatusEventDTO.AVBRUTT -> true
            StatusEventDTO.BEKREFTET -> true
            StatusEventDTO.SENDT -> true
            StatusEventDTO.UTGATT -> true
            StatusEventDTO.APEN -> false
        }
}
