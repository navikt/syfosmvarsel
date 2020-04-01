package no.nav.syfo.syfosmvarsel.nysykmelding

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.VaultSecrets
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.Notifikasjonstatus
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.objectMapper
import no.nav.syfo.syfosmvarsel.opprettReceivedSykmelding
import no.nav.syfo.syfosmvarsel.util.JacksonKafkaSerializer
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeResponse
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NySykmeldingServiceKtTest : Spek({
    val database = TestDB()
    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val brukernotifikasjonService = BrukernotifikasjonService(database, brukernotifikasjonKafkaProducer, "", "tjenester")
    every { brukernotifikasjonKafkaProducer.sendOpprettmelding(any(), any()) } just Runs
    every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs

    val topic = "oppgavevarsel-topic"

    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        topicNames = listOf(topic)
    )

    val credentials = VaultSecrets("", "")
    val config = Environment(kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        tjenesterUrl = "tjenester", cluster = "local", diskresjonskodeEndpointUrl = "diskresjonskode-url", securityTokenServiceURL = "security-token-url", syfosmvarselDBURL = "url",
        mountPathVault = "path", brukernotifikasjonOpprettTopic = "opprett-topic", brukernotifikasjonDoneTopic = "done-topic"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig.toProducerConfig(
        "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)
    val kafkaProducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)
    val diskresjonskodeServiceMock = mockk<DiskresjonskodePortType>()
    every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
    val varselProducer = VarselProducer(diskresjonskodeServiceMock, kafkaProducer, topic)

    val consumerProperties = baseConfig
        .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val kafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    kafkaConsumer.subscribe(listOf(topic))

    val nySykmeldingService = NySykmeldingService(varselProducer, brukernotifikasjonService, "dev-fss")

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

    describe("Mapping av ny sykmelding til oppgavevarsel fungerer som forventet") {
        val sykmelding = opprettReceivedSykmelding(id = UUID.randomUUID().toString())
        println(sykmelding.sykmelding.id)
        it("Ny sykmelding mappes korrekt til oppgavevarsel") {
            val oppgavevarsel = nySykmeldingService.receivedNySykmeldingTilOppgaveVarsel(sykmelding, "tjenester")

            oppgavevarsel.type shouldEqual "NY_SYKMELDING"
            oppgavevarsel.ressursId shouldEqual sykmelding.sykmelding.id
            oppgavevarsel.mottaker shouldEqual "123124"
            oppgavevarsel.parameterListe["url"] shouldEqual "tjenester/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/${sykmelding.sykmelding.id}"
            oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
            oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
            oppgavevarsel.oppgavetype shouldEqual OPPGAVETYPE
            oppgavevarsel.oppgaveUrl shouldEqual "tjenester/sykefravaer"
            oppgavevarsel.repeterendeVarsel shouldEqual false
            oppgavevarsel.utsendelsestidspunkt shouldBeAfter LocalDate.now().atTime(8, 59)
            oppgavevarsel.utsendelsestidspunkt shouldBeBefore LocalDate.now().plusDays(1).atTime(17, 0)
        }
    }

    describe("Ende til ende-test ny sykmelding") {
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/dummysykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", sykmelding)
        it("Oppretter varsel for ny sykmelding") {
            runBlocking {
                nySykmeldingService.opprettVarselForNySykmelding(objectMapper.readValue(cr.value()), "tjenester", LoggingMeta("mottakId", "12315", "", ""))
                val messages = kafkaConsumer.poll(Duration.ofMillis(5000)).toList()

                messages.size shouldEqual 1
                val oppgavevarsel: OppgaveVarsel = objectMapper.readValue(messages[0].value())
                oppgavevarsel.type shouldEqual "NY_SYKMELDING"
                oppgavevarsel.ressursId shouldEqual "d6112773-9587-41d8-9a3f-c8cb42364936"
                oppgavevarsel.mottaker shouldEqual "1231231"
                oppgavevarsel.parameterListe["url"] shouldEqual "tjenester/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/d6112773-9587-41d8-9a3f-c8cb42364936"
                oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
                oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
                oppgavevarsel.oppgavetype shouldEqual OPPGAVETYPE
                oppgavevarsel.oppgaveUrl shouldEqual "tjenester/sykefravaer"
                oppgavevarsel.repeterendeVarsel shouldEqual false
                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
                brukernotifikasjoner[0].event shouldEqual "APEN"
                brukernotifikasjoner[0].notifikasjonstatus shouldEqual Notifikasjonstatus.OPPRETTET
            }
        }

        it("Kaster feil ved mottak av ugyldig ny sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")
            runBlocking {
                assertFailsWith<JsonParseException> { nySykmeldingService.opprettVarselForNySykmelding(objectMapper.readValue(ugyldigCr.value()), "tjenester", LoggingMeta("mottakId", "12315", "", "")) }
            }
        }

        it("Oppretter ikke varsel for ny sykmelding hvis bruker har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            runBlocking {
                nySykmeldingService.opprettVarselForNySykmelding(objectMapper.readValue(cr.value()), "tjenester", LoggingMeta("mottakId", "12315", "", ""))
                val messages = kafkaConsumer.poll(Duration.ofMillis(5000)).toList()

                messages.size shouldEqual 0
            }
        }
    }

    describe("Oppretter ikke varsel i prod") {
        val nySykmeldingServiceProd = NySykmeldingService(varselProducer, brukernotifikasjonService, "prod-fss")
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/dummysykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", sykmelding)
        it("Oppretter brukernotifikasjon, men ikke varsel hvis cluster er prod-fss") {
            runBlocking {
                nySykmeldingServiceProd.opprettVarselForNySykmelding(objectMapper.readValue(cr.value()), "tjenester", LoggingMeta("mottakId", "12315", "", ""))
                val messages = kafkaConsumer.poll(Duration.ofMillis(5000)).toList()

                messages.size shouldEqual 0
                val brukernotifikasjoner = database.connection.hentBrukernotifikasjonListe(UUID.fromString("d6112773-9587-41d8-9a3f-c8cb42364936"))
                brukernotifikasjoner.size shouldEqual 1
            }
        }
    }

    describe("Får riktig tekst for brukernotifikasjon") {
        it("Egenmeldt sykmelding skal gi egenmeldt-tekst") {
            val avsenderSystem = AvsenderSystem("Egenmeldt", "1")

            nySykmeldingService.lagBrukernotifikasjonstekst(avsenderSystem) shouldEqual "Egenmeldingen din er klar til bruk"
        }

        it("Vanlig sykmelding skal gi melding om ny sykmelding") {
            val avsenderSystem = AvsenderSystem("Min EPJ", "1")

            nySykmeldingService.lagBrukernotifikasjonstekst(avsenderSystem) shouldEqual "Du har mottatt en ny sykmelding"
        }
    }
})
