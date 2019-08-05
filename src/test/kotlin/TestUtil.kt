package no.nav.syfo.syfosmvarsel

import org.mockito.Mockito

// Hjelpemetoder for å brygge bro mellom nullbare argumenter i Mockitos argumentMatcher og Kotlin
fun <T> any(): T {
    Mockito.any<T>()
    return uninitialized()
}

private fun <T> uninitialized(): T = null as T