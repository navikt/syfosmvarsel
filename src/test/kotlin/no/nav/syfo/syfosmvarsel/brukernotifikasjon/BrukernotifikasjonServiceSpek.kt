package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BrukernotifikasjonServiceSpek : Spek({
    val database = TestDB()
    val brukernotifikasjonService = BrukernotifikasjonService(database)

    val sykmeldingId = UUID.randomUUID()
    val timestampOpprettet = OffsetDateTime.of(2020, 2, 10, 11, 0, 0, 0, ZoneOffset.UTC)
    val eventIdOpprettet = UUID.randomUUID()
    val timestampFerdig = OffsetDateTime.of(2020, 2, 12, 11, 0, 0, 0, ZoneOffset.UTC)
    val grupperingsId = UUID.randomUUID()

    val brukernotifikasjonDB = BrukernotifikasjonDB(
        sykmeldingId = sykmeldingId,
        timestamp = timestampOpprettet,
        event = "APEN",
        grupperingsId = grupperingsId,
        eventId = eventIdOpprettet,
        notifikasjonstatus = Notifikasjonstatus.OPPRETTET
    )

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Test av opprettBrukernotifikasjon") {
        it("opprettBrukernotifikasjon oppretter ny rad i databasen for oppretting av notifikasjon") {
            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = getAdjustedToLocalDateTime(timestampOpprettet),
                tekst = "tekst"
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 1
            brukernotifikasjoner[0].sykmeldingId shouldEqual sykmeldingId
            brukernotifikasjoner[0].timestamp shouldEqual timestampOpprettet
            brukernotifikasjoner[0].event shouldEqual "APEN"
            brukernotifikasjoner[0].grupperingsId shouldNotBe null
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.OPPRETTET
        }

        it("opprettBrukernotifikasjon gjør ingenting hvis det allerede finnes en opprett-notifikasjon for sykmeldingen") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = getAdjustedToLocalDateTime(timestampOpprettet),
                tekst = "tekst"
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 1
        }
    }

    describe("Test av ferdigstillBrukernotifikasjon") {
        val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
            event = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId.toString(),
                timestamp = timestampFerdig,
                statusEvent = StatusEventDTO.SENDT,
                arbeidsgiver = null,
                sporsmals = null
            ),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = sykmeldingId.toString(),
                timestamp = timestampFerdig,
                fnr = "fnr",
                source = "syfoservice"
            )
        )
        it("ferdigstillBrukernotifikasjon oppretter ny rad i databasen for ferdigstilling av notifikasjon") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 2
            brukernotifikasjoner[0].sykmeldingId shouldEqual sykmeldingId
            brukernotifikasjoner[0].timestamp shouldEqual timestampFerdig
            brukernotifikasjoner[0].event shouldEqual "SENDT"
            brukernotifikasjoner[0].grupperingsId shouldEqual grupperingsId
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.FERDIG
            brukernotifikasjoner[1] shouldEqual brukernotifikasjonDB
        }

        it("ferdigstillBrukernotifikasjon gjør ingenting hvis den ikke finner noen notifikasjon for sykmeldingen") {
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 0
        }
    }
})
