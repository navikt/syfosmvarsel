package no.nav.syfo.syfosmvarsel.avvistsykmelding

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.syfosmvarsel.*
import org.amshove.kluent.shouldBeAfter
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
import java.util.*
import kotlin.test.assertFailsWith

object AvvistSykmeldingServiceKtTest : Spek({

    val topic = "oppgavevarsel-topic"

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf(topic)
    )

    val credentials = VaultSecrets("", "")
    val config = Environment(kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            tjenesterUrl = "tjenester", cluster = "local"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig.toProducerConfig(
            "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)
    val kafkaproducer = KafkaProducer<String, OppgaveVarsel>(producerProperties)

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
    describe("Mapping av sykmelding til oppgavevarsel fungerer som forventet") {
        val sykmelding = opprettReceivedSykmelding(id = "123")
        it("Sykmelding mappes korrekt til oppgavevarsel") {
            val oppgavevarsel = receivedSykmeldingTilOppgaveVarsel(sykmelding, "tjenester")

            oppgavevarsel.type shouldEqual "SYKMELDING_AVVIST"
            oppgavevarsel.ressursId shouldEqual sykmelding.sykmelding.id
            oppgavevarsel.mottaker shouldEqual "123124"
            oppgavevarsel.parameterListe["url"] shouldEqual "tjenester/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/${sykmelding.sykmelding.id}"
            oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
            oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
            oppgavevarsel.oppgavetype shouldEqual OPPGAVETYPE
            oppgavevarsel.oppgaveUrl shouldEqual "tjenester/sykefravaer"
            oppgavevarsel.repeterendeVarsel shouldEqual false
        }
    }

    describe("Ende til ende-test") {
        val sykmelding = String(Files.readAllBytes(Paths.get("src/test/resources/gyldigAvvistSykmelding.json")), StandardCharsets.UTF_8)
        val cr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", sykmelding)
        it("Oppretter varsel for avvist sykmelding") {
            opprettVarselForAvvisteSykmeldinger(cr, kafkaproducer, topic, "tjenester")
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()

            messages.size shouldEqual 1
            val oppgavevarsel: OppgaveVarsel = objectMapper.readValue(messages[0].value())
            oppgavevarsel.type shouldEqual "SYKMELDING_AVVIST"
            oppgavevarsel.ressursId shouldEqual "detteerensykmeldingid"
            oppgavevarsel.mottaker shouldEqual "1231231"
            oppgavevarsel.parameterListe["url"] shouldEqual "tjenester/innloggingsinfo/type/oppgave/undertype/$OPPGAVETYPE/varselid/detteerensykmeldingid"
            oppgavevarsel.utlopstidspunkt shouldBeAfter oppgavevarsel.utsendelsestidspunkt
            oppgavevarsel.varseltypeId shouldEqual "NySykmelding"
            oppgavevarsel.oppgavetype shouldEqual OPPGAVETYPE
            oppgavevarsel.oppgaveUrl shouldEqual "tjenester/sykefravaer"
            oppgavevarsel.repeterendeVarsel shouldEqual false
        }

        it("Kaster feil ved mottak av ugyldig avvist sykmelding") {
            val ugyldigCr = ConsumerRecord<String, String>("test-topic", 0, 42L, "key", "{ikke gyldig...}")

            assertFailsWith<JsonParseException> { opprettVarselForAvvisteSykmeldinger(ugyldigCr, kafkaproducer, topic, "tjenester") }
        }

    }
})
