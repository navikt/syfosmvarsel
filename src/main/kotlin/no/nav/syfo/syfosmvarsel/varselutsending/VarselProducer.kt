package no.nav.syfo.syfosmvarsel.varselutsending

import com.ctc.wstx.exc.WstxException
import no.nav.syfo.helpers.retry
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_AVBRUTT
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeRequest
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.xml.ws.soap.SOAPFaultException

class VarselProducer(private val diskresjonskodeService: DiskresjonskodePortType, private val kafkaproducer: KafkaProducer<String, OppgaveVarsel>, private val oppgavevarselTopic: String) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

    suspend fun sendVarsel(oppgaveVarsel: OppgaveVarsel) {
        try {
            val diskresjonskode: String? = retry(
                    callName = "hent_diskresjonskode",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 60000L),
                    legalExceptions = *arrayOf(SOAPFaultException::class, WstxException::class)
            ) {
                diskresjonskodeService.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(oppgaveVarsel.mottaker)).diskresjonskode
            }
            if (diskresjonskode == "6") {
                log.info("Bruker har diskresjonskode, sender ikke varsel for sykmeldingId {}", oppgaveVarsel.ressursId)
                SM_VARSEL_AVBRUTT.inc()
                return
            }
            kafkaproducer.send(ProducerRecord(oppgavevarselTopic, oppgaveVarsel))
            log.info("Bestilt oppgavevarsel for sykmeldingId {}", oppgaveVarsel.ressursId)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved bestilling av varsel for sykmeldingId {}, ${e.message}", oppgaveVarsel.ressursId)
            throw e
        }
    }
}