package no.nav.syfo.syfosmvarsel.util

import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object DateUtilsKtTest : Spek({

    describe("DateUtils") {

        val iMorgen = LocalDate.now().plusDays(1)

        it("klokkeslett før 9 blir satt mellom 9 og 16") {
            val dato = LocalDate.now().atTime(0, 50).innenforArbeidstidEllerPaafolgendeDag()
            dato shouldBeAfter LocalDate.now().atTime(8,59)
            dato shouldBeBefore LocalDate.now().atTime(16,0)
        }

        it("klokkeslett mellom 9 og 16 blir ikke endret") {
            val dato = LocalDate.now().atTime(9, 50).innenforArbeidstidEllerPaafolgendeDag()
            dato shouldEqual  LocalDate.now().atTime(9,50)
        }

        it("klokkslett etter 16 blir innenfor arbeidstid påfølgende dag") {
            val dato = LocalDate.now().atTime(18, 50).innenforArbeidstidEllerPaafolgendeDag()
            dato shouldBeAfter iMorgen.atTime(8,49)
            dato shouldBeBefore iMorgen.atTime(16,0)
        }

        describe("Edgecaser") {
            it( "Rett før 9 blir innenfor arbeidstid") {
                val rettFor9 = LocalDate.now().atTime(8,59).innenforArbeidstidEllerPaafolgendeDag()
                rettFor9 shouldBeAfter LocalDate.now().atTime(8,59)
                rettFor9 shouldBeBefore LocalDate.now().atTime(16,0)
            }

            it( "Nøyaktig 9 blir nøyaktig 9") {
                val noyaktig9 = LocalDate.now().atTime(9,0).innenforArbeidstidEllerPaafolgendeDag()
                noyaktig9 shouldEqual  LocalDate.now().atTime(9,0)
            }

            it( "Rett etter 9 blir innenfor arbeidstid") {
                val rettEtter9 = LocalDate.now().atTime(9,0, 1).innenforArbeidstidEllerPaafolgendeDag()
                rettEtter9 shouldEqual  LocalDate.now().atTime(9,0,1)
            }

            it( "Rett for 4 blir rett før 4") {
                val rettFor16 = LocalDate.now().atTime(15,59, 59).innenforArbeidstidEllerPaafolgendeDag()
                rettFor16 shouldEqual  LocalDate.now().atTime(15,59, 59)
            }

            it( "Nøyaktig 4 blir neste dag innenfor arbeidstid") {
                val noyaktig16 = LocalDate.now().atTime(16,0).innenforArbeidstidEllerPaafolgendeDag()
                noyaktig16 shouldBeAfter iMorgen.atTime(8,59, 0)
                noyaktig16 shouldBeBefore iMorgen.atTime(16,0, 0)
            }

            it( "Rett etter 4 blir neste dag innenfor arbeidstid") {
                val rettEtter16 = LocalDate.now().atTime(16,0, 1).innenforArbeidstidEllerPaafolgendeDag()
                rettEtter16 shouldBeAfter iMorgen.atTime(8,59, 0)
                rettEtter16 shouldBeBefore iMorgen.atTime(16,0, 0)
            }
        }
    }
})
