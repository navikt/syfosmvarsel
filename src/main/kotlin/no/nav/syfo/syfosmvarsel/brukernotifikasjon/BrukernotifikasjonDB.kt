package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.time.OffsetDateTime
import java.util.UUID

data class BrukernotifikasjonDB(
    val sykmeldingId: UUID,
    val timestamp: OffsetDateTime,
    val event: String,
    val grupperingsId: UUID,
    val eventId: UUID,
    val notifikasjonstatus: Notifikasjonstatus,
)

enum class Notifikasjonstatus {
    OPPRETTET, FERDIG
}
