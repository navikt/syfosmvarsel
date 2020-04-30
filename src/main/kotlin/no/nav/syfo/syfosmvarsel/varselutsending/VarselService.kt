package no.nav.syfo.syfosmvarsel.varselutsending

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import javax.xml.ws.soap.SOAPFaultException
import no.nav.syfo.helpers.retry
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_AVBRUTT
import no.nav.syfo.syfosmvarsel.metrics.SM_VARSEL_RESERVERT
import no.nav.syfo.syfosmvarsel.varselutsending.dkif.DkifClient
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeRequest

@KtorExperimentalAPI
class VarselService(private val diskresjonskodeService: DiskresjonskodePortType, private val dkifClient: DkifClient) {

    fun sendVarsel() {
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
            val diskresjonskode: String? = retry(
                callName = "hent_diskresjonskode",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 60000L),
                legalExceptions = *arrayOf(SOAPFaultException::class, WstxException::class)
            ) {
                diskresjonskodeService.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(mottaker)).diskresjonskode
            }
            if (diskresjonskode == "6") {
                return true
            }
            return false
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
