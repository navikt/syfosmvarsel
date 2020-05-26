package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.syfosmvarsel.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonKafkaProducer(
    private val kafkaproducerOpprett: KafkaProducer<Nokkel, Oppgave>,
    private val kafkaproducerDone: KafkaProducer<Nokkel, Done>,
    private val brukernotifikasjonOpprettTopic: String,
    private val brukernotifikasjonDoneTopic: String
) {
    fun sendOpprettmelding(nokkel: Nokkel, oppgave: Oppgave) {
        try {
            kafkaproducerOpprett.send(ProducerRecord(brukernotifikasjonOpprettTopic, nokkel, oppgave)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sending av oppgave med id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }

    fun sendDonemelding(nokkel: Nokkel, done: Done) {
        try {
            kafkaproducerDone.send(ProducerRecord(brukernotifikasjonDoneTopic, nokkel, done)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }
}
