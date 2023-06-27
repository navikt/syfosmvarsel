package no.nav.syfo.syfosmvarsel.util

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo

class DateUtilsKtTest :
    FunSpec({
        context("DateUtils") {
            val iMorgen = LocalDate.now().plusDays(1)

            test("klokkeslett før 9 blir satt mellom 9 og 17") {
                val dato = LocalDate.now().atTime(0, 50).innenforArbeidstidEllerPaafolgendeDag()
                dato shouldBeAfter LocalDate.now().atTime(8, 59)
                dato shouldBeBefore LocalDate.now().atTime(17, 0)
            }

            test("klokkeslett mellom 9 og 17 blir ikke endret") {
                val dato = LocalDate.now().atTime(9, 50).innenforArbeidstidEllerPaafolgendeDag()
                dato shouldBeEqualTo LocalDate.now().atTime(9, 50)
            }

            test("klokkslett etter 17 blir innenfor arbeidstid påfølgende dag") {
                val dato = LocalDate.now().atTime(18, 50).innenforArbeidstidEllerPaafolgendeDag()
                dato shouldBeAfter iMorgen.atTime(8, 49)
                dato shouldBeBefore iMorgen.atTime(17, 0)
            }

            context("Edgecaser") {
                test("Rett før 9 blir innenfor arbeidstid") {
                    val rettFor9 =
                        LocalDate.now().atTime(8, 59).innenforArbeidstidEllerPaafolgendeDag()
                    rettFor9 shouldBeAfter LocalDate.now().atTime(8, 59)
                    rettFor9 shouldBeBefore LocalDate.now().atTime(17, 0)
                }

                test("Nøyaktig 9 blir nøyaktig 9") {
                    val noyaktig9 =
                        LocalDate.now().atTime(9, 0).innenforArbeidstidEllerPaafolgendeDag()
                    noyaktig9 shouldBeEqualTo LocalDate.now().atTime(9, 0)
                }

                test("Rett etter 9 blir innenfor arbeidstid") {
                    val rettEtter9 =
                        LocalDate.now().atTime(9, 0, 1).innenforArbeidstidEllerPaafolgendeDag()
                    rettEtter9 shouldBeEqualTo LocalDate.now().atTime(9, 0, 1)
                }

                test("Rett før 5 blir rett før 5") {
                    val rettFor17 =
                        LocalDate.now().atTime(16, 59, 59).innenforArbeidstidEllerPaafolgendeDag()
                    rettFor17 shouldBeEqualTo LocalDate.now().atTime(16, 59, 59)
                }

                test("Nøyaktig 5 blir neste dag innenfor arbeidstid") {
                    val noyaktig17 =
                        LocalDate.now().atTime(17, 0).innenforArbeidstidEllerPaafolgendeDag()
                    noyaktig17 shouldBeAfter iMorgen.atTime(8, 59, 0)
                    noyaktig17 shouldBeBefore iMorgen.atTime(17, 0, 0)
                }

                test("Rett etter 5 blir neste dag innenfor arbeidstid") {
                    val rettEtter17 =
                        LocalDate.now().atTime(17, 0, 1).innenforArbeidstidEllerPaafolgendeDag()
                    rettEtter17 shouldBeAfter iMorgen.atTime(8, 59, 0)
                    rettEtter17 shouldBeBefore iMorgen.atTime(17, 0, 0)
                }
            }
        }
    })
