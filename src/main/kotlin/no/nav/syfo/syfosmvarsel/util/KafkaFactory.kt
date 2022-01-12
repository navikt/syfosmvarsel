package no.nav.syfo.syfosmvarsel.util

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

class KafkaFactory private constructor() {
    companion object {
        fun getNyKafkaConsumer(kafkaBaseConfig: Properties, environment: Environment): KafkaConsumer<String, String> {
            val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
            )
            consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val nyKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
            nyKafkaConsumer.subscribe(listOf(environment.sykmeldingAutomatiskBehandlingTopic, environment.sykmeldingManuellBehandlingTopic, environment.avvistSykmeldingTopic))
            return nyKafkaConsumer
        }

        fun getNyKafkaAivenConsumer(environment: Environment): KafkaConsumer<String, String> {
            val consumerProperties = KafkaUtils.getAivenKafkaConfig().toConsumerConfig(
                "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
            )
            val consumer = KafkaConsumer<String, String>(consumerProperties)
            consumer.subscribe(listOf(environment.okSykmeldingTopicAiven, environment.avvistSykmeldingTopicAiven, environment.manuellSykmeldingTopicAiven))
            return consumer
        }

        fun getKafkaStatusConsumerAiven(environment: Environment): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
            val kafkaBaseConfigAiven = KafkaUtils.getAivenKafkaConfig()
            val properties = kafkaBaseConfigAiven.toConsumerConfig("syfosmvarsel-consumer", JacksonKafkaDeserializer::class)
            properties.let {
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            }
            val kafkaStatusConsumer = KafkaConsumer(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class))
            kafkaStatusConsumer.subscribe(listOf(environment.sykmeldingStatusAivenTopic))
            return kafkaStatusConsumer
        }

        fun getBrukernotifikasjonKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): BrukernotifikasjonKafkaProducer {
            val kafkaBrukernotifikasjonProducerConfig = kafkaBaseConfig.toProducerConfig(
                "syfosmvarsel", valueSerializer = KafkaAvroSerializer::class, keySerializer = KafkaAvroSerializer::class
            )

            val kafkaproducerOpprett = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerConfig)
            val kafkaproducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerConfig)
            return BrukernotifikasjonKafkaProducer(
                kafkaproducerOpprett = kafkaproducerOpprett,
                kafkaproducerDone = kafkaproducerDone,
                brukernotifikasjonOpprettTopic = environment.brukernotifikasjonOpprettTopic,
                brukernotifikasjonDoneTopic = environment.brukernotifikasjonDoneTopic
            )
        }
    }
}
