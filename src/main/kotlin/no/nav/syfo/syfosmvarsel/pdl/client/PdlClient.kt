package no.nav.syfo.syfosmvarsel.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.syfosmvarsel.pdl.client.model.GetPersonRequest
import no.nav.syfo.syfosmvarsel.pdl.client.model.GetPersonResponse
import no.nav.syfo.syfosmvarsel.pdl.client.model.GetPersonVariables

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String
) {
    private val temaHeader = "TEMA"
    private val tema = "SYM"

    suspend fun getPerson(fnr: String, accessToken: String): GetPersonResponse {
        val getPersonRequest = GetPersonRequest(query = graphQlQuery, variables = GetPersonVariables(ident = fnr))
        return httpClient.post(basePath) {
            setBody(getPersonRequest)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(temaHeader, tema)
            header(HttpHeaders.ContentType, "application/json")
        }.body()
    }
}
