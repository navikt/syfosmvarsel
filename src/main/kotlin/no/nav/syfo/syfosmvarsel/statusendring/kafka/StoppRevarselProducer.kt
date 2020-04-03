package no.nav.syfo.syfosmvarsel.statusendring.kafka

import no.nav.syfo.syfosmvarsel.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class StoppRevarselProducer(private val kafkaproducer: KafkaProducer<String, StoppRevarsel>, private val stoppRevarselTopic: String) {
    fun sendStoppRevarsel(stoppRevarsel: StoppRevarsel) {
        try {
            kafkaproducer.send(ProducerRecord(stoppRevarselTopic, stoppRevarsel))
            log.info("Stopper revarsel for sykmeldingId {}", stoppRevarsel.ressursId)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved stopping av revarsel for sykmeldingId {}, ${e.message}", stoppRevarsel.ressursId)
            throw e
        }
    }
}
