package no.nav.syfo.syfosmvarsel.varselutsending

import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeRequest
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselProducer(private val diskresjonskodeService: DiskresjonskodePortType, private val kafkaproducer: KafkaProducer<String, OppgaveVarsel>, private val oppgavevarselTopic: String) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

    fun sendVarsel(oppgaveVarsel: OppgaveVarsel) {
        try {
            val diskresjonskode: String? = diskresjonskodeService.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(oppgaveVarsel.mottaker)).diskresjonskode
            if (diskresjonskode == "6") {
                log.info("Bruker har diskresjonskode, sender ikke varsel for sykmeldingId {}", oppgaveVarsel.ressursId)
                return
            }
            kafkaproducer.send(ProducerRecord(oppgavevarselTopic, oppgaveVarsel))
            log.info("Bestilt oppgavevarsel for sykmeldingId {}", oppgaveVarsel.ressursId)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved bestilling av varsel for sykmeldingId {}", oppgaveVarsel.ressursId)
            throw e
        }
    }
}