package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.syfosmvarsel.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonKafkaProducer(
    private val kafkaproducerOpprett: KafkaProducer<NokkelInput, OppgaveInput>,
    private val kafkaproducerDone: KafkaProducer<NokkelInput, DoneInput>,
    private val brukernotifikasjonOpprettTopic: String,
    private val brukernotifikasjonDoneTopic: String,
) {
    fun sendOpprettmelding(nokkel: NokkelInput, oppgave: OppgaveInput) {
        try {
            kafkaproducerOpprett
                .send(ProducerRecord(brukernotifikasjonOpprettTopic, nokkel, oppgave))
                .get()
        } catch (e: Exception) {
            log.error(
                "Noe gikk galt ved sending av oppgave med id {}: ${e.message}",
                nokkel.getEventId()
            )
            throw e
        }
    }

    fun sendDonemelding(nokkel: NokkelInput, done: DoneInput) {
        try {
            kafkaproducerDone.send(ProducerRecord(brukernotifikasjonDoneTopic, nokkel, done)).get()
        } catch (e: Exception) {
            log.error(
                "Noe gikk galt ved ferdigstilling av oppgave med id {}: ${e.message}",
                nokkel.getEventId()
            )
            throw e
        }
    }
}
