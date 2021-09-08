package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
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
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.VaultServiceUser
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.pdl.service.PdlPersonService
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
    val vaultServiceUser = VaultServiceUser("", "")
    val config = Environment(kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        dittSykefravaerUrl = "dittsykefravaer", cluster = "local", securityTokenServiceURL = "security-token-url", syfosmvarselDBURL = "url",
        mountPathVault = "path", brukernotifikasjonOpprettTopic = "opprett-topic",
        brukernotifikasjonDoneTopic = "done-topic", pdlGraphqlPath = "pdl-sti", pdlScope = "scope",
        aadAccessTokenV2Url = "aadAccessTokenV2Url", clientIdV2 = "clientid", clientSecretV2 = "secret"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
        put("schema.registry.url", embeddedEnvironment.schemaRegistry!!.url)
    }

    val baseConfig = loadBaseConfig(config, vaultServiceUser).overrideForTest()
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
    val pdlPersonService = mockk<PdlPersonService>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "srvsyfosmvarsel", "dittsykefravaer", pdlPersonService)

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
            fnr = "fnr",
            source = "syfoservice"
        )
    )

    beforeGroup {
        embeddedEnvironment.start()
    }

    beforeEachTest {
        clearMocks(pdlPersonService)
        coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false
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
            runBlocking {
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
        }

        it("opprettBrukernotifikasjon gjør ingenting hvis det allerede finnes en opprett-notifikasjon for sykmeldingen") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            runBlocking {
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

        it("ferdigstillBrukernotifikasjon oppretter kun done en gang") {
            database.registrerBrukernotifikasjon(brukernotifikasjonDB)

            brukernotifikasjonService.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO)
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
            oppgave.getLink() shouldEqual "dittsykefravaer/syk/sykefravaer"
            oppgave.getSikkerhetsnivaa() shouldEqual 4
            oppgave.getTekst() shouldEqual "tekst"
            oppgave.getTidspunkt() shouldEqual timestampOpprettet.toInstant().toEpochMilli()
        }
    }

    describe("Test av skalSendeEksterntVarsel") {
        it("Skal sende eksternt varsel hvis bruker ikke har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns false

            var skalSendeEksterntVarsel: Boolean? = null
            runBlocking {
                skalSendeEksterntVarsel = brukernotifikasjonService.skalSendeEksterntVarsel("mottaker", "id")
            }

            skalSendeEksterntVarsel shouldEqual true
        }

        it("Skal ikke sende eksternt varsel hvis bruker har diskresjonskode") {
            coEvery { pdlPersonService.harDiskresjonskode(any(), any()) } returns true

            var skalSendeEksterntVarsel: Boolean? = null
            runBlocking {
                skalSendeEksterntVarsel = brukernotifikasjonService.skalSendeEksterntVarsel("mottaker", "id")
            }

            skalSendeEksterntVarsel shouldEqual false
        }
    }
})
