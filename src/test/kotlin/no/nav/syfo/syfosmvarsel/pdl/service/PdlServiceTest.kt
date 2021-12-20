package no.nav.syfo.syfosmvarsel.pdl.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.syfosmvarsel.client.AccessTokenClientV2
import no.nav.syfo.syfosmvarsel.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.pdl.client.model.GetPersonResponse
import no.nav.syfo.syfosmvarsel.pdl.client.model.ResponseData
import no.nav.syfo.syfosmvarsel.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

class PdlServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    beforeEachTest {
        clearAllMocks()
    }

    describe("PdlService") {
        it("hente person fra pdl uten fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("UGRADERT"))
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldBeEqualTo false
            }
        }
        it("skal gå gjennom om adressebeskyttelse er null") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(null)
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldBeEqualTo false
            }
        }
        it("hente person fra pdl fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("FORTROLIG"))
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldBeEqualTo false
            }
        }
        it("hente person fra pdl strengt fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("STRENGT_FORTROLIG"))
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldBeEqualTo true
            }
        }
        it("hente person fra pdl strengt fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse(listOf("STRENGT_FORTROLIG_UTLAND"))
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            runBlocking {
                val diskresjonskode = pdlService.harDiskresjonskode("01245678901", "123")
                diskresjonskode shouldBeEqualTo true
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null), null)
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.harDiskresjonskode("01245678901", "123")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke person i PDL"
        }
    }
})
