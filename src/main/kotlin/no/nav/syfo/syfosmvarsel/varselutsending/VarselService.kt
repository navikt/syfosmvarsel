package no.nav.syfo.syfosmvarsel.varselutsending

import io.ktor.util.KtorExperimentalAPI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_AVBRUTT
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_RESERVERT
import no.nav.syfo.syfosmvarsel.varselutsending.database.VarselDB
import no.nav.syfo.syfosmvarsel.varselutsending.database.finnesFraFor
import no.nav.syfo.syfosmvarsel.varselutsending.database.registrerVarsel
import no.nav.syfo.syfosmvarsel.varselutsending.dkif.DkifClient
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.service.PdlPersonService

@KtorExperimentalAPI
class VarselService(
    private val pdlPersonService: PdlPersonService,
    private val dkifClient: DkifClient,
    private val database: DatabaseInterface,
    private val bestillVarselMHandlingMqProducer: BestillVarselMHandlingMqProducer
) {

    suspend fun sendVarsel(oppgaveVarsel: OppgaveVarsel, sykmeldingId: String) {
        if (skalSendeVarsel(mottaker = oppgaveVarsel.mottaker, sykmeldingId = sykmeldingId)) {
            if (database.finnesFraFor(UUID.fromString(sykmeldingId))) {
                log.info("Har allerede bestilt varsel for sykmeldingId {}", sykmeldingId)
            } else {
                bestillVarselMHandlingMqProducer.sendOppgavevarsel(sykmeldingId, oppgaveVarsel)
                database.registrerVarsel(VarselDB(
                    sykmeldingId = UUID.fromString(sykmeldingId),
                    opprettet = OffsetDateTime.now(ZoneOffset.UTC),
                    mottakerFnr = oppgaveVarsel.mottaker,
                    varselbestillingId = oppgaveVarsel.varselbestillingId
                ))
                log.info("Lagret varselbestilling for sykmeldingId {}", sykmeldingId)
            }
        }
    }

    suspend fun skalSendeVarsel(mottaker: String, sykmeldingId: String): Boolean {
        if (harDiskresjonskode(mottaker = mottaker, sykmeldingId = sykmeldingId)) {
            log.info("Bruker har diskresjonskode, sender ikke varsel for sykmeldingId {}", sykmeldingId)
            SM_VARSEL_AVBRUTT.inc()
            return false
        }
        if (erReservert(mottaker = mottaker, sykmeldingId = sykmeldingId)) {
            log.info("Bruker er reservert, sender ikke varsel for sykmeldingId {}", sykmeldingId)
            SM_VARSEL_RESERVERT.inc()
            return false
        }
        return true
    }

    private suspend fun harDiskresjonskode(mottaker: String, sykmeldingId: String): Boolean {
        try {
            return pdlPersonService.harDiskresjonskode(mottaker, sykmeldingId)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av diskresjonskode for sykmeldingId {}, ${e.message}", sykmeldingId)
            throw e
        }
    }

    private suspend fun erReservert(mottaker: String, sykmeldingId: String): Boolean {
        try {
            return dkifClient.erReservert(mottaker = mottaker, sykmeldingId = sykmeldingId)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved sjekk mot dkif for sykmeldingId {}, ${e.message}", sykmeldingId)
            throw e
        }
    }
}
