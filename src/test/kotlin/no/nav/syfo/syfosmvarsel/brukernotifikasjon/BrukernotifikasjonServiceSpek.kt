package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.pdl.service.PdlPersonService
import no.nav.syfo.syfosmvarsel.util.KafkaTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class BrukernotifikasjonServiceSpek : FunSpec({
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val config = Environment(
        dittSykefravaerUrl = "https://dittsykefravaer", brukernotifikasjonOpprettTopic = "opprett-topic",
        brukernotifikasjonDoneTopic = "done-topic", pdlGraphqlPath = "pdl-sti", pdlScope = "scope",
        aadAccessTokenV2Url = "aadAccessTokenV2Url", clientIdV2 = "clientid", clientSecretV2 = "secret",
        kafkaSchemaRegistryPassword = "", kafkaSchemaRegistryUrl = "", kafkaSchemaRegistryUsername = "",
        cluster = "dev-fss", databaseUsername = "user", databasePassword = "pwd", dbHost = "host",
        dbName = "smvarsel", dbPort = "5089"
    )

    val kafkaBrukernotifikasjonProducerConfig = kafkaConfig.toProducerConfig(
        "syfosmvarsel",
        valueSerializer = KafkaAvroSerializer::class,
        keySerializer = KafkaAvroSerializer::class
    )

    val kafkaproducerOpprett = KafkaProducer<NokkelInput, OppgaveInput>(kafkaBrukernotifikasjonProducerConfig)
    val kafkaproducerDone = KafkaProducer<NokkelInput, DoneInput>(kafkaBrukernotifikasjonProducerConfig)
    val brukernotifikasjonKafkaProducer = BrukernotifikasjonKafkaProducer(
        kafkaproducerOpprett = kafkaproducerOpprett,
        kafkaproducerDone = kafkaproducerDone,
        brukernotifikasjonOpprettTopic = config.brukernotifikasjonOpprettTopic,
        brukernotifikasjonDoneTopic = config.brukernotifikasjonDoneTopic
    )

    val consumerProperties = kafkaConfig
        .toConsumerConfig("spek.integration-consumer", keyDeserializer = KafkaAvroDeserializer::class, valueDeserializer = KafkaAvroDeserializer::class)
    val kafkaConsumerOppgave = KafkaConsumer<NokkelInput, OppgaveInput>(consumerProperties)
    kafkaConsumerOppgave.subscribe(listOf("opprett-topic"))

    val database = TestDB()
    val pdlPersonService = mockk<PdlPersonService>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "https://dittsykefravar", pdlPersonService)

    val sykmeldingId = UUID.randomUUID()
    val timestampOpprettet = OffsetDateTime.of(2020, 2, 10, 11, 0, 0, 0, ZoneOffset.UTC)
    val timestampOpprettetLocalDateTime = LocalDateTime.of(2020, 2, 10, 11, 0, 0, 0)
    val eventIdOpprettet = UUID.randomUUID()
    val timestampFerdig = OffsetDateTime.of(2020, 2, 12, 11, 0, 0, 0, ZoneOffset.UTC)

    val brukernotifikasjonDB = BrukernotifikasjonDB(
        sykmeldingId = sykmeldingId,
        timestamp = timestampOpprettet,
        event = "APEN",
        grupperingsId = sykmeldingId,
        eventId = eventIdOpprettet,
        notifikasjonstatus = Notifikasjonstatus.OPPRETTET
    )

    val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingId.toString(),
            timestamp = timestampFerdig,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = null,
            sporsmals = null
        ),
        kafkaMetadata = KafkaMetadataDTO(
            sykmeldingId = sykmeldingId.toString(),
            timestamp = timestampFerdig,
            fnr = "12345678912",
            source = "syfoservice"
        )
    )

    beforeTest {
        clearMocks(pdlPersonService)
        coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false
    }

    afterTest {
        database.connection.dropData()
    }

    context("Test av opprettBrukernotifikasjon") {
        test("opprettBrukernotifikasjon oppretter ny rad i databasen for oppretting av notifikasjon") {
            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = timestampOpprettetLocalDateTime,
                tekst = "tekst",
                fnr = "12345678912",
                loggingMeta = LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldBeEqualTo 1
            brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampOpprettet
            brukernotifikasjoner[0].event shouldBeEqualTo "APEN"
            brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.OPPRETTET
        }

        test("opprettBrukernotifikasjon gjør ingenting hvis det allerede finnes en opprett-notifikasjon for sykmeldingen") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = timestampOpprettetLocalDateTime,
                tekst = "tekst",
                fnr = "fnr",
                loggingMeta = LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldBeEqualTo 1
        }
    }

    context("Test av ferdigstillBrukernotifikasjon") {
        test("ferdigstillBrukernotifikasjon oppretter ny rad i databasen for ferdigstilling av notifikasjon") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
                .sortedByDescending { it.timestamp }
            brukernotifikasjoner.size shouldBeEqualTo 2
            brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampFerdig
            brukernotifikasjoner[0].event shouldBeEqualTo "SENDT"
            brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.FERDIG
            brukernotifikasjoner[1] shouldBeEqualTo brukernotifikasjonDB
        }

        test("ferdigstillBrukernotifikasjon gjør ingenting hvis den ikke finner noen notifikasjon for sykmeldingen") {
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldBeEqualTo 0
        }

        test("ferdigstillBrukernotifikasjon oppretter kun done en gang") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)
            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
                .sortedByDescending { it.timestamp }
            brukernotifikasjoner.size shouldBeEqualTo 2
            brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampFerdig
            brukernotifikasjoner[0].event shouldBeEqualTo "SENDT"
            brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.FERDIG
            brukernotifikasjoner[1] shouldBeEqualTo brukernotifikasjonDB
        }
    }

    context("Ende til ende-test oppgave") {
        test("Oppretter brukernotifikasjon-oppgave korrekt") {
            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = timestampOpprettetLocalDateTime,
                tekst = "tekst",
                fnr = "12345678912",
                loggingMeta = LoggingMeta("mottakId", "12315", "", "")
            )

            val messages = kafkaConsumerOppgave.poll(Duration.ofMillis(5000)).toList()

            messages.size shouldBeEqualTo 1
            val nokkel: NokkelInput = messages[0].key()

            val oppgave: OppgaveInput = messages[0].value()

            nokkel.getAppnavn() shouldBeEqualTo "syfosmvarsel"
            nokkel.getEventId() shouldBeEqualTo sykmeldingId.toString()
            nokkel.getFodselsnummer() shouldBeEqualTo "12345678912"
            oppgave.getLink() shouldBeEqualTo "https://dittsykefravar/syk/sykefravaer"
            oppgave.getSikkerhetsnivaa() shouldBeEqualTo 4
            oppgave.getTekst() shouldBeEqualTo "tekst"
            oppgave.getTidspunkt() shouldBeEqualTo timestampOpprettet.toInstant().toEpochMilli()
        }
    }

    context("Test av skalSendeEksterntVarsel") {
        test("Skal sende eksternt varsel hvis bruker ikke har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false

            val skalSendeEksterntVarsel = brukernotifikasjonService.skalSendeEksterntVarsel("mottaker", "id")

            skalSendeEksterntVarsel shouldBeEqualTo true
        }

        test("Skal ikke sende eksternt varsel hvis bruker har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns true

            val skalSendeEksterntVarsel = brukernotifikasjonService.skalSendeEksterntVarsel("mottaker", "id")

            skalSendeEksterntVarsel shouldBeEqualTo false
        }
    }
})
