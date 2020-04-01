package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.VaultSecrets
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BrukernotifikasjonServiceSpek : Spek({
    val topics = listOf("opprett-topic", "done-topic")
    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        topicNames = topics,
        withSchemaRegistry = true
    )
    val credentials = VaultSecrets("", "")
    val config = Environment(kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        tjenesterUrl = "tjenester", cluster = "local", diskresjonskodeEndpointUrl = "diskresjonskode-url", securityTokenServiceURL = "security-token-url", syfosmvarselDBURL = "url",
        mountPathVault = "path", brukernotifikasjonOpprettTopic = "opprett-topic", brukernotifikasjonDoneTopic = "done-topic"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
        put("schema.registry.url", embeddedEnvironment.schemaRegistry!!.url)
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()
    val kafkaBrukernotifikasjonProducerConfig = baseConfig.toProducerConfig(
        "syfosmvarsel", valueSerializer = KafkaAvroSerializer::class, keySerializer = KafkaAvroSerializer::class)

    val kafkaproducerOpprett = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerConfig)
    val kafkaproducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerConfig)
    val brukernotifikasjonKafkaProducer = BrukernotifikasjonKafkaProducer(kafkaproducerOpprett = kafkaproducerOpprett,
        kafkaproducerDone = kafkaproducerDone,
        brukernotifikasjonOpprettTopic = config.brukernotifikasjonOpprettTopic,
        brukernotifikasjonDoneTopic = config.brukernotifikasjonDoneTopic)

    val consumerProperties = baseConfig
        .toConsumerConfig("spek.integration-consumer", keyDeserializer = KafkaAvroDeserializer::class, valueDeserializer = KafkaAvroDeserializer::class)
    val kafkaConsumerOppgave = KafkaConsumer<Nokkel, Oppgave>(consumerProperties)
    kafkaConsumerOppgave.subscribe(listOf("opprett-topic"))

    val database = TestDB()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "srvsyfosmvarsel", "tjenester")

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

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
        database.stop()
    }

    describe("Test av opprettBrukernotifikasjon") {
        it("opprettBrukernotifikasjon oppretter ny rad i databasen for oppretting av notifikasjon") {
            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = timestampOpprettetLocalDateTime,
                tekst = "tekst",
                fnr = "fnr",
                loggingMeta = LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 1
            brukernotifikasjoner[0].sykmeldingId shouldEqual sykmeldingId
            brukernotifikasjoner[0].timestamp shouldEqual timestampOpprettet
            brukernotifikasjoner[0].event shouldEqual "APEN"
            brukernotifikasjoner[0].grupperingsId shouldEqual sykmeldingId
            brukernotifikasjoner[0].eventId shouldNotBe null
            brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.OPPRETTET
        }

        it("opprettBrukernotifikasjon gjør ingenting hvis det allerede finnes en opprett-notifikasjon for sykmeldingen") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.opprettBrukernotifikasjon(
                sykmeldingId = sykmeldingId.toString(),
                mottattDato = timestampOpprettetLocalDateTime,
                tekst = "tekst",
                fnr = "fnr",
                loggingMeta = LoggingMeta("mottakId", "12315", "", "")
            )

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 1
        }
    }

    describe("Test av ferdigstillBrukernotifikasjon") {
        it("ferdigstillBrukernotifikasjon oppretter ny rad i databasen for ferdigstilling av notifikasjon") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)

            val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(sykmeldingId)
            brukernotifikasjoner.size shouldEqual 2
            brukernotifikasjoner[0].sykmeldingId shouldEqual sykmeldingId
            brukernotifikasjoner[0].timestamp shouldEqual timestampFerdig
            brukernotifikasjoner[0].event shouldEqual "SENDT"
            brukernotifikasjoner[0].grupperingsId shouldEqual sykmeldingId
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

    describe("Ende til ende-test oppgave") {
        it("Oppretter brukernotifikasjon-oppgave korrekt") {
            runBlocking {
                brukernotifikasjonService.opprettBrukernotifikasjon(
                    sykmeldingId = sykmeldingId.toString(),
                    mottattDato = timestampOpprettetLocalDateTime,
                    tekst = "tekst",
                    fnr = "fnr",
                    loggingMeta = LoggingMeta("mottakId", "12315", "", "")
                )
            }
            val messages = kafkaConsumerOppgave.poll(Duration.ofMillis(5000)).toList()

            messages.size shouldEqual 1
            val nokkel: Nokkel = messages[0].key()
            val oppgave: Oppgave = messages[0].value()

            nokkel.getSystembruker() shouldEqual "srvsyfosmvarsel"
            nokkel.getEventId() shouldEqual sykmeldingId.toString()
            oppgave.getFodselsnummer() shouldEqual "fnr"
            oppgave.getLink() shouldEqual "tjenester/sykefravaer"
            oppgave.getSikkerhetsnivaa() shouldEqual 4
            oppgave.getTekst() shouldEqual "tekst"
            oppgave.getTidspunkt() shouldEqual timestampOpprettet.toEpochSecond()
        }
    }
})
