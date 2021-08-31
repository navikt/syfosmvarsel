package no.nav.syfo.syfosmvarsel.pdl.service

import no.nav.syfo.syfosmvarsel.client.AccessTokenClientV2
import no.nav.syfo.syfosmvarsel.pdl.client.PdlClient
import no.nav.syfo.syfosmvarsel.pdl.client.model.Gradering
import no.nav.syfo.syfosmvarsel.pdl.error.PersonNotFoundInPdl
import org.slf4j.LoggerFactory

class PdlPersonService(
        private val pdlClient: PdlClient,
        private val accessTokenClientV2: AccessTokenClientV2,
        private val pdlScope: String
) { companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }
    suspend fun harDiskresjonskode(fnr: String, sykmeldingsId: String): Boolean {
        log.info("Trying to get accessToken for $sykmeldingsId")
        val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)
        log.info("Trying to call pdl for $sykmeldingsId")
        val pdlResponse = pdlClient.getPerson(fnr, accessToken)

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
