package no.nav.syfo.syfosmvarsel.avvistsykmelding

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.Notifikasjonstatus
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.objectMapper
import no.nav.syfo.syfosmvarsel.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.test.assertFailsWith

class AvvistSykmeldingServiceKtTest : FunSpec({
    val database = TestDB()
    val pdlPersonService = mockk<PdlPersonService>()
    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "https://dittsykefravar", pdlPersonService)

    val avvistSykmeldingService = AvvistSykmeldingService(brukernotifikasjonService)

    beforeTest {
        clearAllMocks()
        every { brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), any()) } just Runs
        every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs
        coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false
    }

    afterTest {
        database.connection.dropData()
    }

    context("Ende til ende-test avvist sykmelding") {
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/dummysykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord("test-topic", 0, 42L, "key", sykmelding)
        test("Oppretter brukernotifikasjon med eksternt varsel for avvist sykmelding") {

            avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(
                objectMapper.readValue(cr.value()),
                LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner =
                database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
            brukernotifikasjoner.size shouldBeEqualTo 1
            brukernotifikasjoner[0].event shouldBeEqualTo "APEN"
            brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.OPPRETTET
            verify(exactly = 1) {
                brukernotifikasjonKafkaProducer.sendOpprettmelding(
                    any(),
                    withArg {
                        it.getEksternVarsling() shouldBeEqualTo true
                    }
                )
            }
        }

        test("Kaster feil ved mottak av ugyldig avvist sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")

            assertFailsWith<JsonParseException> { avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(objectMapper.readValue(ugyldigCr.value()), LoggingMeta("mottakId", "12315", "", "")) }
        }

        test("Oppretter brukernotifikasjon, men ikke varsel for avvist sykmelding hvis bruker har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns true
            avvistSykmeldingService.opprettVarselForAvvisteSykmeldinger(
                objectMapper.readValue(cr.value()),
                LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner =
                database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
            brukernotifikasjoner.size shouldBeEqualTo 1
            verify(exactly = 1) {
                brukernotifikasjonKafkaProducer.sendOpprettmelding(
                    any(),
                    withArg {
                        it.getEksternVarsling() shouldBeEqualTo false
                    }
                )
            }
        }
    }
})
