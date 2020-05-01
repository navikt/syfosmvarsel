package no.nav.syfo.syfosmvarsel.avvistsykmelding

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.Notifikasjonstatus
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.objectMapper
import no.nav.syfo.syfosmvarsel.opprettReceivedSykmelding
import no.nav.syfo.syfosmvarsel.varselutsending.BestillVarselMHandlingMqProducer
import no.nav.syfo.syfosmvarsel.varselutsending.VarselService
import no.nav.syfo.syfosmvarsel.varselutsending.database.hentVarsel
import no.nav.syfo.syfosmvarsel.varselutsending.dkif.DkifClient
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeResponse
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object AvvistSykmeldingServiceKtTest : Spek({
    val database = TestDB()
    val diskresjonskodeServiceMock = mockk<DiskresjonskodePortType>()
    val bestillVarselMHandlingMqProducerMock = mockk<BestillVarselMHandlingMqProducer>()
    val dkifClientMock = mockk<DkifClient>()
    val varselService = VarselService(diskresjonskodeServiceMock, dkifClientMock, database, bestillVarselMHandlingMqProducerMock)
    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "", "tjenester")

    val avvistSykmeldingService = AvvistSykmeldingService(varselService, brukernotifikasjonService)

    beforeEachTest {
        clearAllMocks()
        every { brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), any()) } just Runs
        every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs
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
    describe("Mapping av avvist sykmelding til oppgavevarsel fungerer som forventet") {
        val sykmelding = opprettReceivedSykmelding(id = "123")
        it("Avvist sykmelding mappes korrekt til oppgavevarsel") {
            val oppgavevarsel = avvistSykmeldingService.receivedAvvistSykmeldingTilOppgaveVarsel(sykmelding)

            oppgavevarsel.ressursId shouldEqual sykmelding.sykmelding.id
            oppgavevarsel.mottaker shouldEqual "123124"
            oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
            oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
            oppgavevarsel.utsendelsestidspunkt shouldBeAfter LocalDate.now().atTime(8, 59)
            oppgavevarsel.utsendelsestidspunkt shouldBeBefore LocalDate.now().plusDays(1).atTime(17, 0)
            oppgavevarsel.varselbestillingId shouldNotBe null
        }
    }

    describe("Ende til ende-test avvist sykmelding") {
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/dummysykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", sykmelding)
        it("Oppretter varsel for avvist sykmelding") {
            runBlocking {
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(cr.value()), LoggingMeta("mottakId", "12315", "", ""))

                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
                brukernotifikasjoner[0].event shouldEqual "APEN"
                brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.OPPRETTET

                val varselDB = database.hentVarsel(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                varselDB?.mottaker shouldEqual "1231231"
                varselDB?.sykmeldingId shouldEqual UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936")
                verify(exactly = 1) { bestillVarselMHandlingMqProducerMock.sendOppgavevarsel("d6112773-9587-41d8-9a3f-c8cb42364936", any()) }
            }
        }

        it("Kaster feil ved mottak av ugyldig avvist sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")
            runBlocking {
                assertFailsWith<JsonParseException> { avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(ugyldigCr.value()), LoggingMeta("mottakId", "12315", "", "")) }
            }
        }

        it("Oppretter brukernotifikasjon, men ikke varsel for avvist sykmelding hvis bruker har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            runBlocking {
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(cr.value()), LoggingMeta("mottakId", "12315", "", ""))

                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
                val varselDB = database.hentVarsel(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                varselDB shouldEqual null
                verify(exactly = 0) { bestillVarselMHandlingMqProducerMock.sendOppgavevarsel(any(), any()) }
            }
        }
    }
})
