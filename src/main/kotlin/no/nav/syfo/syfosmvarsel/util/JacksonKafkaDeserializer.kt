package no.nav.syfo.syfosmvarsel.util

import kotlin.reflect.KClass
import org.apache.kafka.common.serialization.Deserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class JacksonKafkaDeserializer<T : Any>(private val type: KClass<T>) : Deserializer<T> {
    private val jsonMapper: JsonMapper =
        jacksonMapperBuilder()
            .enable(
                tools.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )
            .build()

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {}

    override fun deserialize(topic: String?, data: ByteArray): T? {
        return jsonMapper.readValue(data, type.java)
    }

    override fun close() {}
}
