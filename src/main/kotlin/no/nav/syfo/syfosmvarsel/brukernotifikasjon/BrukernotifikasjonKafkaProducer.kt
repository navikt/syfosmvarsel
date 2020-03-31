package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonKafkaProducer(
    private val kafkaproducerOpprett: KafkaProducer<Nokkel, Oppgave>,
    private val kafkaproducerDone: KafkaProducer<Nokkel, Done>,
    private val brukernotifikasjonOpprettTopic: String,
    private val brukernotifikasjonDoneTopic: String
) {
    fun sendOpprettmelding(nokkel: Nokkel, oppgave: Oppgave) {
        kafkaproducerOpprett.send(ProducerRecord(brukernotifikasjonOpprettTopic, nokkel, oppgave))
    }

    fun sendDonemelding(nokkel: Nokkel, done: Done) {
        kafkaproducerDone.send(ProducerRecord(brukernotifikasjonDoneTopic, nokkel, done))
    }
}
