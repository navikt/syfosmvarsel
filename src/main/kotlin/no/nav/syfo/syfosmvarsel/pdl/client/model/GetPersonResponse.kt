package no.nav.syfo.syfosmvarsel.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseData(
    val hentPerson: HentPerson?
)

data class HentPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?
)

data class Adressebeskyttelse(
    val gradering: Gradering?
)

enum class Gradering {
    UGRADERT,
    FORTROLIG,
    STRENGT_FORTROLIG,
    STRENGT_FORTROLIG_UTLAND
}

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)
