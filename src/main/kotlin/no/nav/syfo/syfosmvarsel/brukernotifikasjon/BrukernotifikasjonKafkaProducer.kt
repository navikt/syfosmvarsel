package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import no.nav.syfo.syfosmvarsel.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, String>,
    private val brukernotifikasjonTopic: String,
) {
    fun sendOpprettmelding(varselId: String, varsel: String) {
        try {
            kafkaProducer.send(ProducerRecord(brukernotifikasjonTopic, varselId, varsel)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sending av oppgave med id $varselId:", e)
            throw e
        }
    }

    fun sendDonemelding(varselId: String, varsel: String) {
        try {
            kafkaProducer.send(ProducerRecord(brukernotifikasjonTopic, varselId, varsel)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id $varselId", e)
            throw e
        }
    }
}
