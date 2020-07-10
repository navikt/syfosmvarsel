package no.nav.syfo.syfosmvarsel.pdl.service

import io.mockk.coEvery
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.GetPersonResponse
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.ResponseData
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.service.PdlPersonService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)
    coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    describe("PdlService") {
        it("hente person fra pdl uten fortrolig dresse") {
            runBlocking {
                coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("UGRADERT"))
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual false
            }
        }
        it("skal gå gjennom om adressebeskyttelse er null") {
            runBlocking {
                coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(null)
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual false
            }
        }
        it("hente person fra pdl fortrolig dresse") {
            runBlocking {
                coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("FORTROLIG"))
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
            runBlocking {
                coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("STRENGT_FORTROLIG_UTLAND"))
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldEqual true
            }
        }

        it("Skal feile når person ikke finnes") {
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null), null)
                    pdlService.harDiskresjonskode("01245678901", "123")
                }
            }
            exception.message shouldEqual "Fant ikke person i PDL"
        }
    }

})
