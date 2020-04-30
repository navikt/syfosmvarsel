package no.nav.syfo.syfosmvarsel.varselutsending.dkif

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.syfosmvarsel.varselutsending.VarselService
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeResponse
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class VarselServiceSpek : Spek({
    val diskresjonskodeServiceMock = mockk<DiskresjonskodePortType>()
    val dkifClientMock = mockk<DkifClient>()
    val varselService = VarselService(diskresjonskodeServiceMock, dkifClientMock)

    beforeEachTest {
        clearAllMocks()
    }

    describe("Test av skalSendeVarsel") {
        it("Skal sende varsel hvis bruker ikke er reservert eller har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
            coEvery { dkifClientMock.erReservert(any(), any()) } returns false

            var skalSendeVarsel: Boolean? = null
            runBlocking {
                skalSendeVarsel = varselService.skalSendeVarsel("mottaker", "id")
            }

            skalSendeVarsel shouldEqual true
        }

        it("Skal ikke sende varsel hvis bruker har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            coEvery { dkifClientMock.erReservert(any(), any()) } returns false

            var skalSendeVarsel: Boolean? = null
            runBlocking {
                skalSendeVarsel = varselService.skalSendeVarsel("mottaker", "id")
            }

            skalSendeVarsel shouldEqual false
        }

        it("Skal ikke sende varsel hvis bruker er reservert") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
            coEvery { dkifClientMock.erReservert(any(), any()) } returns true

            var skalSendeVarsel: Boolean? = null
            runBlocking {
                skalSendeVarsel = varselService.skalSendeVarsel("mottaker", "id")
            }

            skalSendeVarsel shouldEqual false
        }
    }
})
