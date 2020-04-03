package no.nav.syfo.syfosmvarsel.statusendring.kafka

data class StoppRevarsel(
    val type: String,
    val ressursId: String
)
