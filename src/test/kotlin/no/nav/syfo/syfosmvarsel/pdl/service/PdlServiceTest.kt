package no.nav.syfo.syfosmvarsel.pdl.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.syfosmvarsel.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.pdl.client.model.GetPersonResponse
import no.nav.syfo.syfosmvarsel.pdl.client.model.ResponseData
import no.nav.syfo.syfosmvarsel.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)

    beforeEachTest {
        clearAllMocks()
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("PdlService") {
        it("hente person fra pdl uten fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("UGRADERT"))
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual false
            }
        }
        it("skal gå gjennom om adressebeskyttelse er null") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(null)
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual false
            }
        }
        it("hente person fra pdl fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("FORTROLIG"))
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual false
            }
        }
        it("hente person fra pdl strengt fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("STRENGT_FORTROLIG"))
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual true
            }
        }
        it("hente person fra pdl strengt fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("STRENGT_FORTROLIG_UTLAND"))
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual true
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null), null)
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.harDiskresjonskode("01245678901", "123")
                }
            }
            exception.message shouldEqual "Fant ikke person i PDL"
        }
    }
})
