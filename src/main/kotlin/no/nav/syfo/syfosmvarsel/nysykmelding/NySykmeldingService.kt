package no.nav.syfo.syfosmvarsel.nysykmelding

import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.metrics.NY_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.util.innenforArbeidstidEllerPaafolgendeDag
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// Henger sammen med tekster i mininnboks: http://stash.devillo.no/projects/FA/repos/mininnboks-tekster/browse/src/main/tekster/mininnboks/oppgavetekster
const val OPPGAVETYPE = "0005"

class NySykmeldingService(private val varselProducer: VarselProducer, private val brukernotifikasjonService: BrukernotifikasjonService) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

    suspend fun opprettVarselForNySykmelding(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ) {
        try {
            log.info("Mottatt ny sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            val oppgaveVarsel = receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding)
            varselProducer.sendVarsel(oppgaveVarsel)
            NY_SM_VARSEL_OPPRETTET.inc()
            log.info("Opprettet oppgavevarsel for ny sykmelding med {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            brukernotifikasjonService.opprettBrukernotifikasjon(sykmeldingId = receivedSykmelding.sykmelding.id, mottattDato = receivedSykmelding.mottattDato, tekst = lagBrukernotifikasjonstekst(receivedSykmelding.sykmelding.avsenderSystem), fnr = receivedSykmelding.personNrPasient, loggingMeta = loggingMeta)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved oppretting av varsel for ny sykmelding")
            throw e
        }
    }

    fun receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding): OppgaveVarsel {
        val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
        return OppgaveVarsel(
            type = "NY_SYKMELDING",
            ressursId = receivedSykmelding.sykmelding.id,
            mottaker = receivedSykmelding.personNrPasient,
            utlopstidspunkt = utsendelsestidspunkt.plusDays(5), // utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
            utsendelsestidspunkt = utsendelsestidspunkt,
            varseltypeId = "NySykmelding",
            varselbestillingId = UUID.randomUUID()
        )
    }

    fun lagBrukernotifikasjonstekst(avsenderSystem: AvsenderSystem): String {
        return if (avsenderSystem.navn == "Egenmeldt") {
            "Egenmeldingen din er klar til bruk"
        } else {
            "Du har mottatt en ny sykmelding"
        }
    }
}
