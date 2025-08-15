package no.nav.syfo.syfosmvarsel.util

import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.testcontainers.containers.Network
import org.testcontainers.kafka.KafkaContainer

fun configureAndStartKafka(): KafkaContainer {
    val kafka = KafkaContainer("apache/kafka").withNetwork(Network.newNetwork())
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
                it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            }
            kafkaConfig.remove("security.protocol")
            kafkaConfig.remove("sasl.mechanism")
            return kafkaConfig
        }
    }
}
