package no.nav.syfo.syfosmvarsel.util

import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.kafka.aiven.KafkaUtils
import no.nav.syfo.syfosmvarsel.kafka.toConsumerConfig
import no.nav.syfo.syfosmvarsel.kafka.toProducerConfig
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingnotifikasjonKafkaConsumer(
            environment: Environment
        ): KafkaConsumer<String, String> {
            val consumerProperties =
                KafkaUtils.getAivenKafkaConfig("ny-consumer")
                    .toConsumerConfig(
                        "syfosmvarsel-consumer",
                        valueDeserializer = StringDeserializer::class,
                    )
                    .also {
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    }
            val consumer = KafkaConsumer<String, String>(consumerProperties)
            consumer.subscribe(listOf(environment.sykmeldingnotifikasjon))
            return consumer
        }

        fun getKafkaStatusConsumerAiven(
            environment: Environment
        ): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
            val kafkaBaseConfigAiven = KafkaUtils.getAivenKafkaConfig("status-consumer")
            val properties =
                kafkaBaseConfigAiven.toConsumerConfig(
                    "syfosmvarsel-consumer",
                    JacksonKafkaDeserializer::class
                )
            properties.let {
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            }
            val kafkaStatusConsumer =
                KafkaConsumer(
                    properties,
                    StringDeserializer(),
                    JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class)
                )
            kafkaStatusConsumer.subscribe(listOf(environment.sykmeldingStatusAivenTopic))
            return kafkaStatusConsumer
        }

        fun getBrukernotifikasjonKafkaProducer(
            environment: Environment
        ): BrukernotifikasjonKafkaProducer {
            val kafkaBrukernotifikasjonProducerConfig =
                KafkaUtils.getAivenKafkaConfig("bruker-notifikatsjon-produser")
                    .toProducerConfig(
                        "syfosmvarsel",
                        valueSerializer = StringSerializer::class,
                        keySerializer = StringSerializer::class,
                    )
            val producer = KafkaProducer<String, String>(kafkaBrukernotifikasjonProducerConfig)
            return BrukernotifikasjonKafkaProducer(
                producer,
                environment.brukernotifikasjonTopic,
            )
        }
    }
}
