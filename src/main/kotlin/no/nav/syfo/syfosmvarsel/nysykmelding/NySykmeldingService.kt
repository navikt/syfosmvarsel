package no.nav.syfo.syfosmvarsel.nysykmelding

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.metrics.NY_SM_VARSEL_OPPRETTET
import no.nav.syfo.syfosmvarsel.varselutsending.VarselService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class NySykmeldingService(private val varselService: VarselService, private val brukernotifikasjonService: BrukernotifikasjonService) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmvarsel")

    suspend fun opprettVarselForNySykmelding(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ) {
        try {
            log.info("Mottatt ny sykmelding med id {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            val oppgaveVarsel = receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding)
            varselService.sendVarsel(oppgaveVarsel, receivedSykmelding.sykmelding.id)
            NY_SM_VARSEL_OPPRETTET.inc()
            log.info("Opprettet oppgavevarsel for ny sykmelding med {}, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
            brukernotifikasjonService.opprettBrukernotifikasjon(sykmeldingId = receivedSykmelding.sykmelding.id, mottattDato = receivedSykmelding.mottattDato, tekst = lagBrukernotifikasjonstekst(receivedSykmelding.sykmelding.avsenderSystem), fnr = receivedSykmelding.personNrPasient, loggingMeta = loggingMeta)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved oppretting av varsel for ny sykmelding")
            throw e
        }
    }

    fun receivedNySykmeldingTilOppgaveVarsel(receivedSykmelding: ReceivedSykmelding): OppgaveVarsel {
        // val utsendelsestidspunkt = LocalDateTime.now().innenforArbeidstidEllerPaafolgendeDag()
        val utsendelsestidspunkt = LocalDateTime.now() // for test
        return OppgaveVarsel(
            ressursId = receivedSykmelding.sykmelding.id,
            mottaker = receivedSykmelding.personNrPasient,
            utlopstidspunkt = utsendelsestidspunkt.plusDays(5), // utløpstidspunkt må være om mindre enn 7 dager for å unngå revarsling
            utsendelsestidspunkt = utsendelsestidspunkt,
            varseltypeId = "NySykmeldingUtenLenke",
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
