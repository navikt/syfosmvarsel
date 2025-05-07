package no.nav.syfo.syfosmvarsel

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String,
    val sykmeldingId: String,
)

class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) :
    RuntimeException()
