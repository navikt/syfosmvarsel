package no.nav.syfo.syfosmvarsel.domain

import java.time.LocalDateTime
import java.util.UUID

data class OppgaveVarsel(
    val type: String,
    val ressursId: String,
    val mottaker: String,
    val utlopstidspunkt: LocalDateTime,
    val utsendelsestidspunkt: LocalDateTime,
    val varseltypeId: String,
    val varselbestillingId: UUID
)
