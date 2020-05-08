package no.nav.syfo.syfosmvarsel.varselutsending.database

import java.time.OffsetDateTime
import java.util.UUID

data class VarselDB(
    val sykmeldingId: UUID,
    val opprettet: OffsetDateTime,
    val mottakerFnr: String,
    val varselbestillingId: UUID
)
