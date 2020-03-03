package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_FERDIG
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_OPPRETTET

class BrukernotifikasjonService(private val database: DatabaseInterface) {

    fun opprettBrukernotifikasjon(sykmeldingId: String, mottattDato: LocalDateTime, tekst: String) {
        val brukernotifikasjon = database.hentBrukernotifikasjon(sykmeldingId = UUID.fromString(sykmeldingId), event = "APEN")
        if (brukernotifikasjon != null) {
            log.info("Notifikasjon for ny sykmelding med id $sykmeldingId finnes fra før, ignorerer")
        } else {
            database.registrerBrukernotifikasjon(mapTilOpprettetBrukernotifikasjon(sykmeldingId, mottattDato))
            log.info("Opprettet brukernotifikasjon for sykmelding med id $sykmeldingId")
            BRUKERNOT_OPPRETTET.inc()
            // skriv til kafka
        }
    }

    fun ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId
        val brukernotifikasjon = database.hentBrukernotifikasjon(sykmeldingId = UUID.fromString(sykmeldingId), event = sykmeldingStatusKafkaMessageDTO.event.statusEvent.name)
        if (brukernotifikasjon == null) {
            log.info("Fant ingen notifikasjon for sykmelding med id $sykmeldingId")
        } else {
            database.registrerBrukernotifikasjon(mapTilFerdigstiltBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO, brukernotifikasjon))
            log.info("Ferdigstilt brukernotifikasjon for sykmelding med id $sykmeldingId")
            BRUKERNOT_FERDIG.inc()
            // skriv til kafka
        }
    }

    private fun mapTilOpprettetBrukernotifikasjon(sykmeldingId: String, mottattDato: LocalDateTime): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(sykmeldingId),
            timestamp = getAdjustedOffsetDateTime(mottattDato),
            event = "APEN",
            grupperingsId = UUID.randomUUID(),
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.OPPRETTET
        )

    private fun mapTilFerdigstiltBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO, opprettetBrukernotifikasjonDB: BrukernotifikasjonDB): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId),
            timestamp = sykmeldingStatusKafkaMessageDTO.event.timestamp,
            event = sykmeldingStatusKafkaMessageDTO.event.statusEvent.name,
            grupperingsId = opprettetBrukernotifikasjonDB.grupperingsId,
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.FERDIG
        )
}
