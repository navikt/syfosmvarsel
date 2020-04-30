package no.nav.syfo.syfosmvarsel.varselutsending.dkif

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.syfosmvarsel.log

@KtorExperimentalAPI
class DkifClient(
    private val endpointUrl: String = "http://dkif.default",
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun erReservert(mottaker: String, sykmeldingId: String): Boolean =
        retry("er_reservert") {
            val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/api/v1/personer/kontaktinformasjon") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                    append("Nav-Consumer-Id", "syfosmvarsel")
                    append("Nav-Personidenter", mottaker)
                }
            }.execute()
            if (httpResponse.status != HttpStatusCode.OK) {
                log.error("Kall mot dkif feiler med statuskode {} for sykmeldingId {}", httpResponse.status, sykmeldingId)
                throw IOException("Kall mot dkif feiler med statuskode ${httpResponse.status}")
            } else {
                val digitalKontaktinfoBolkDTO = httpResponse.call.receive<DigitalKontaktinfoBolkDTO>()
                if (digitalKontaktinfoBolkDTO.feil != null && digitalKontaktinfoBolkDTO.feil.isNotEmpty()) {
                    val feilmelding = digitalKontaktinfoBolkDTO.feil.values.filter { !it.melding.isNullOrEmpty() }.getOrNull(0)?.melding
                    feilmelding?.let {
                        log.warn("Det skjedde en feil ved kall mot dkif: {}", feilmelding)
                    }
                    return@retry true
                }
                if (digitalKontaktinfoBolkDTO.kontaktinfo == null || digitalKontaktinfoBolkDTO.kontaktinfo.isEmpty()) {
                    log.error("Kall mot dkif gir OK-svar, men svaret mangler kontaktinfo, antar bruker er reservert")
                    return@retry true
                }
                val digitalKontaktinfoDTO: DigitalKontaktinfoDTO = digitalKontaktinfoBolkDTO.kontaktinfo.values.toList().getOrElse(index = 0, defaultValue = { DigitalKontaktinfoDTO(null, false, null, null) })

                return@retry !digitalKontaktinfoDTO.kanVarsles
            }
    }
}

data class DigitalKontaktinfoBolkDTO(
    val feil: Map<String, FeilDTO>?,
    val kontaktinfo: Map<String, DigitalKontaktinfoDTO>?
)

data class DigitalKontaktinfoDTO(
    val epostadresse: String?,
    val kanVarsles: Boolean,
    val mobiltelefonnummer: String?,
    val personident: String?
)

data class FeilDTO(
    val melding: String?
)
