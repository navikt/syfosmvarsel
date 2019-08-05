package no.nav.syfo.syfosmvarsel.nysykmelding

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import net.logstash.logback.argument.StructuredArguments
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.syfosmvarsel.*
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.util.*
import kotlin.test.assertFailsWith

object NySykmeldingServiceKtTest : Spek({

    val topic = "oppgavevarsel-topic"

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topicNames = listOf(topic)
    )

    val credentials = VaultSecrets("", "")
    val config = Environment(kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            tjenesterUrl = "tjenester", cluster = "local", diskresjonskodeEndpointUrl = "diskresjonskode-url", securityTokenServiceURL = "security-token-url"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig.toProducerConfig(
            "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)
    val kafkaproducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)
    val diskresjonskodeServiceMock = mockk<DiskresjonskodePortType>()
    every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
    val varselProducer = VarselProducer(diskresjonskodeServiceMock, kafkaproducer, topic)

    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)
    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }
    describe("Mapping av ny sykmelding til oppgavevarsel fungerer som forventet") {
        val sykmelding = opprettReceivedSykmelding(id = "123")
        it("Ny sykmelding mappes korrekt til oppgavevarsel") {
            val oppgavevarsel = receivedNySykmeldingTilOppgaveVarsel(sykmelding, "tjenester")

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
            opprettVarselForNySykmelding(objectMapper.readValue(cr.value()), varselProducer, "tjenester", LoggingMeta(arrayOf(StructuredArguments.keyValue("mottakId", "12315"))))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()

            messages.size shouldEqual 1
            val oppgavevarsel: OppgaveVarsel = objectMapper.readValue(messages[0].value())
            oppgavevarsel.type shouldEqual "NY_SYKMELDING"
            oppgavevarsel.ressursId shouldEqual "detteerensykmeldingid"
            oppgavevarsel.mottaker shouldEqual "1231231"
            oppgavevarsel.parameterListe["url"] shouldEqual "tjenester/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/detteerensykmeldingid"
            oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
            oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
            oppgavevarsel.oppgavetype shouldEqual OPPGAVETYPE
            oppgavevarsel.oppgaveUrl shouldEqual "tjenester/sykefravaer"
            oppgavevarsel.repeterendeVarsel shouldEqual false
        }

        it("Kaster feil ved mottak av ugyldig ny sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")

            assertFailsWith<JsonParseException> { opprettVarselForNySykmelding(objectMapper.readValue(ugyldigCr.value()), varselProducer, "tjenester", LoggingMeta(arrayOf(StructuredArguments.keyValue("mottakId", "12315")))) }
        }

        it("Oppretter ikke varsel for ny sykmelding hvis bruker har diskresjonskode") {
            every { diskresjonskodeServiceMock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            opprettVarselForNySykmelding(objectMapper.readValue(cr.value()), varselProducer, "tjenester", LoggingMeta(arrayOf(StructuredArguments.keyValue("mottakId", "12315"))))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()

            messages.size shouldEqual 0
        }
    }
})
