package no.nav.syfo.syfosmvarsel.varselutsending

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.varselutsending.database.VarselDB
import no.nav.syfo.syfosmvarsel.varselutsending.database.hentVarsel
import no.nav.syfo.syfosmvarsel.varselutsending.database.registrerVarsel
import no.nav.syfo.syfosmvarsel.varselutsending.dkif.DkifClient
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeResponse
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class VarselServiceSpek : Spek({
    val diskresjonskodeServiceMock = mockk<DiskresjonskodePortType>()
    val dkifClientMock = mockk<DkifClient>()
    val database = TestDB()
    val bestillVarselMHandlingMqProducerMock = mockk<BestillVarselMHandlingMqProducer>()
    val varselService = VarselService(diskresjonskodeServiceMock, dkifClientMock, database, bestillVarselMHandlingMqProducerMock)

    val sykmeldingId = UUID.randomUUID()
    val oppgaveVarsel = OppgaveVarsel(
        ressursId = sykmeldingId.toString(),
        mottaker = "fnr",
        utlopstidspunkt = LocalDateTime.now().plusDays(5),
        utsendelsestidspunkt = LocalDateTime.now(),
        varseltypeId = "NySykmelding",
        varselbestillingId = UUID.randomUUID()
    )

    beforeEachTest {
        clearAllMocks()
        every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
        coEvery { dkifClientMock.erReservert(any(), any()) } returns false
        every { bestillVarselMHandlingMqProducerMock.sendOppgavevarsel(any(), any()) } just Runs
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Test av sendVarsel") {
        it("Sender varsel og lagrer i database") {
            runBlocking {
                varselService.sendVarsel(oppgaveVarsel, sykmeldingId.toString())
            }

            val varselDB = database.hentVarsel(sykmeldingId)
            varselDB?.sykmeldingId shouldEqual sykmeldingId
            varselDB?.mottakerFnr shouldEqual "fnr"
            varselDB?.opprettet shouldNotEqual null
            varselDB?.varselbestillingId shouldEqual oppgaveVarsel.varselbestillingId

            verify(exactly = 1) { bestillVarselMHandlingMqProducerMock.sendOppgavevarsel(any(), any()) }
        }
        it("Skal ikke sende varsel hvis sendt før") {
            database.registrerVarsel(VarselDB(sykmeldingId, OffsetDateTime.now(), "fnr", UUID.randomUUID()))

            runBlocking {
                varselService.sendVarsel(oppgaveVarsel, sykmeldingId.toString())
            }

            verify(exactly = 0) { bestillVarselMHandlingMqProducerMock.sendOppgavevarsel(any(), any()) }
        }
        it("Skal ikke sende varsel hvis bruker har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")

            runBlocking {
                varselService.sendVarsel(oppgaveVarsel, sykmeldingId.toString())
            }

            database.hentVarsel(sykmeldingId) shouldEqual null
        }
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
