package no.nav.syfo.syfosmvarsel.util

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.util.Properties

fun configureAndStartKafka(): KafkaContainer {
    val kafka = KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME).withTag(KAFKA_IMAGE_VERSION)).withNetwork(Network.newNetwork())
    kafka.start()
    return kafka
}

class KafkaTest {
    companion object {
        val kafka = configureAndStartKafka()
        fun setupKafkaConfig(): Properties {
            val kafkaConfig = Properties()
            kafkaConfig.let {
                it["bootstrap.servers"] = kafka.bootstrapServers
                it["schema.registry.url"] = "mock://url"
                it["specific.avro.reader"] = true
                it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
                it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
                it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            }
            kafkaConfig.remove("security.protocol")
            kafkaConfig.remove("sasl.mechanism")
            return kafkaConfig
        }
    }
}
