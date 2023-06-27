package no.nav.syfo.syfosmvarsel.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

fun LocalDateTime.innenforArbeidstidEllerPaafolgendeDag(): LocalDateTime {
    return when (this.hour) {
        in 0..8 -> this.toLocalDate().mellom9og17()
        in 9..16 -> this
        else -> LocalDate.now().plusDays(1).mellom9og17()
    }
}

fun LocalDate.mellom9og17(): LocalDateTime {
    return this.atTime(
        ThreadLocalRandom.current().nextInt(9, 16),
        ThreadLocalRandom.current().nextInt(0, 59)
    )
}
