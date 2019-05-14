package no.nav.syfo.syfosmvarsel.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

fun LocalDateTime.innenforArbeidstidEllerPaafolgendeDag(): LocalDateTime {
    return when (this.hour) {
        in 0..8 -> this.toLocalDate().mellom9og16()
        in 9..15 -> this
        else -> LocalDate.now().plusDays(1).mellom9og16()
    }
}

fun LocalDate.mellom9og16(): LocalDateTime {
    return this.atTime(ThreadLocalRandom.current().nextInt(9, 15), ThreadLocalRandom.current().nextInt(0, 59))
}
