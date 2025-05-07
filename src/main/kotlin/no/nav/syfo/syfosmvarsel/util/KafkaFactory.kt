package no.nav.syfo.syfosmvarsel.util

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
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

class KafkaFactory private constructor() {
    companion object {
        fun getNyKafkaAivenConsumer(environment: Environment): KafkaConsumer<String, String> {
            val consumerProperties =
                KafkaUtils.getAivenKafkaConfig("ny-consumer")
                    .toConsumerConfig(
                        "syfosmvarsel-consumer",
                        valueDeserializer = StringDeserializer::class,
                    )
                    .also {
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                    }
            val consumer = KafkaConsumer<String, String>(consumerProperties)
            consumer.subscribe(
                listOf(
                    environment.okSykmeldingTopicAiven,
                    environment.avvistSykmeldingTopicAiven,
                    environment.manuellSykmeldingTopicAiven,
                    environment.sykmeldingNotifikasjon
                )
            )
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
                        valueSerializer = KafkaAvroSerializer::class,
                        keySerializer = KafkaAvroSerializer::class,
                    )
                    .also {
                        it["schema.registry.url"] = environment.kafkaSchemaRegistryUrl
                        it["basic.auth.credentials.source"] = "USER_INFO"
                        it["basic.auth.user.info"] =
                            "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                    }
            val kafkaproducerOpprett =
                KafkaProducer<NokkelInput, OppgaveInput>(kafkaBrukernotifikasjonProducerConfig)
            val kafkaproducerDone =
                KafkaProducer<NokkelInput, DoneInput>(kafkaBrukernotifikasjonProducerConfig)
            return BrukernotifikasjonKafkaProducer(
                kafkaproducerOpprett = kafkaproducerOpprett,
                kafkaproducerDone = kafkaproducerDone,
                brukernotifikasjonOpprettTopic = environment.brukernotifikasjonOpprettTopic,
                brukernotifikasjonDoneTopic = environment.brukernotifikasjonDoneTopic,
            )
        }
    }
}
