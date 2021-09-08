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
import no.nav.syfo.syfosmvarsel.pdl.service.PdlPersonService
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object AvvistSykmeldingServiceKtTest : Spek({
    val database = TestDB()
    val pdlPersonService = mockk<PdlPersonService>()
    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "", "dittsykefravaer", pdlPersonService)

    val avvistSykmeldingService = AvvistSykmeldingService(brukernotifikasjonService)

    beforeEachTest {
        clearAllMocks()
        every { brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), any()) } just Runs
        every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs
        coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Ende til ende-test avvist sykmelding") {
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/dummysykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", sykmelding)
        it("Oppretter brukernotifikasjon med eksternt varsel for avvist sykmelding") {
            runBlocking {
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(cr.value()), LoggingMeta("mottakId", "12315", "", ""))

                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
                brukernotifikasjoner[0].event shouldEqual "APEN"
                brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.OPPRETTET
                verify(exactly = 1) {
                    brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), withArg {
                        it.eksternVarsling shouldEqual true
                    })
                }
            }
        }

        it("Kaster feil ved mottak av ugyldig avvist sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")
            runBlocking {
                assertFailsWith<JsonParseException> { avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(ugyldigCr.value()), LoggingMeta("mottakId", "12315", "", "")) }
            }
        }

        it("Oppretter brukernotifikasjon, men ikke varsel for avvist sykmelding hvis bruker har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns true
            runBlocking {
                avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(cr.value()), LoggingMeta("mottakId", "12315", "", ""))

                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
                verify(exactly = 1) {
                    brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), withArg {
                        it.eksternVarsling shouldEqual false
                    })
                }
            }
        }
    }
})
