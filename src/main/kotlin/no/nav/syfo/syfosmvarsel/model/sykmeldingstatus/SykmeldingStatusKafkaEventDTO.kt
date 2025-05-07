package no.nav.syfo.syfosmvarsel.model.sykmeldingstatus

import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
)
