package no.nav.syfo.syfosmvarsel.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import org.apache.kafka.common.serialization.Deserializer

class JacksonKafkaDeserializer<T : Any>(private val type: KClass<T>) : Deserializer<T> {
    private val objectMapper: ObjectMapper =
        jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        }

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {}

    override fun deserialize(topic: String?, data: ByteArray): T? {
        try {
            return objectMapper.readValue(data, type.java)
        } catch (e: Exception) {
            val string = String(data)
            if (string == "null") {
                return null
            }
            throw RuntimeException("Could not deserialize json", e)
        }
    }

    override fun close() {}
}
