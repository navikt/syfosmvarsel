package no.nav.syfo.syfosmvarsel.pdl.service

import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.GetPersonResponse
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.Gradering
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.HentPerson
import no.nav.syfo.syfosmvarsel.varselutsending.pdl.client.model.ResponseData

fun getPdlResponse(adresseGradering: List<String>?): GetPersonResponse {
    return GetPersonResponse(ResponseData(
            hentPerson = HentPerson(adresseGradering?.map { Adressebeskyttelse(gradering = Gradering.valueOf(it)) })),
    errors = null)
}
