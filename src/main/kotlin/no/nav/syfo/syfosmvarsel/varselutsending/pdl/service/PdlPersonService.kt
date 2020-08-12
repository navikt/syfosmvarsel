package no.nav.syfo.syfosmvarsel.varselutsending.pdl.service

import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.Gradering
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.error.PersonNotFoundInPdl
import org.slf4j.LoggerFactory

class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }
    suspend fun harDiskresjonskode(fnr: String, sykmeldingsId: String): Boolean {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(fnr, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL kastet error: {} for sykmeldingsId: {} ", it, sykmeldingsId)
            }
        }
        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", sykmeldingsId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }

        val gradert = pdlResponse.data.hentPerson.adressebeskyttelse?.any { adressebeskyttelse ->
            adressebeskyttelse.gradering == Gradering.STRENGT_FORTROLIG ||
                    adressebeskyttelse.gradering == Gradering.STRENGT_FORTROLIG_UTLAND
        }

        return gradert ?: false
    }
}
