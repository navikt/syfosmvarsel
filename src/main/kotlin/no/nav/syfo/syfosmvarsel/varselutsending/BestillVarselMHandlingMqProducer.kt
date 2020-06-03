package no.nav.syfo.syfosmvarsel.varselutsending

import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller
import javax.xml.datatype.DatatypeConfigurationException
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.transform.stream.StreamResult
import no.nav.melding.virksomhet.varselmedhandling.v1.varselmedhandling.ObjectFactory
import no.nav.melding.virksomhet.varselmedhandling.v1.varselmedhandling.Person
import no.nav.melding.virksomhet.varselmedhandling.v1.varselmedhandling.VarselMedHandling
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.log

class BestillVarselMHandlingMqProducer(
    private val session: Session,
    private val messageProducer: MessageProducer
) {
    private val jaxbContext: JAXBContext = JAXBContext.newInstance(VarselMedHandling::class.java)

    fun sendOppgavevarsel(
        sykmeldingId: String,
        oppgaveVarsel: OppgaveVarsel
    ) {
        messageProducer.send(session.createTextMessage().apply(createMessage(oppgaveVarsel)))
        log.info("Bestilt varsel for sykmelding med id {}", sykmeldingId)
    }

    private fun createMessage(
        oppgaveVarsel: OppgaveVarsel
    ): TextMessage.() -> Unit {
        return {
            text = marshalOppgaveVarsel(mapOppgavevarsel(oppgaveVarsel))
        }
    }

    fun mapOppgavevarsel(oppgaveVarsel: OppgaveVarsel): VarselMedHandling =
        VarselMedHandling(
            Person(oppgaveVarsel.mottaker),
            tilXmlGregorianCalendar(oppgaveVarsel.utsendelsestidspunkt),
            oppgaveVarsel.varselbestillingId.toString(),
            false,
            oppgaveVarsel.varseltypeId,
            emptyList(),
            tilXmlGregorianCalendar(oppgaveVarsel.utlopstidspunkt)
        )

    private fun marshalOppgaveVarsel(varselMedHandling: VarselMedHandling): String {
        val oppgavevarselMarshaller: Marshaller = jaxbContext.createMarshaller()
            .apply {
                setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
                setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
                setProperty(Marshaller.JAXB_FRAGMENT, true)
            }
        return try {
            val writer = StringWriter()
            oppgavevarselMarshaller.marshal(ObjectFactory().createVarselMedHandling(varselMedHandling), StreamResult(writer))
            writer.toString()
        } catch (e: JAXBException) {
            log.error("Kunne ikke marshalle varsel: {}", e.message)
            throw RuntimeException(e)
        }
    }

    private fun tilXmlGregorianCalendar(tidspunkt: LocalDateTime?): XMLGregorianCalendar? {
        return if (tidspunkt == null) {
            null
        } else try {
            DatatypeFactory.newInstance().newXMLGregorianCalendar(tidspunkt.format(DateTimeFormatter.ISO_DATE_TIME))
        } catch (e: DatatypeConfigurationException) {
            log.error("Kunne ikke konvertere tidspunkt {}", tidspunkt)
            throw RuntimeException("Kunne ikke konvertere tidspunkt $tidspunkt")
        }
    }
}
