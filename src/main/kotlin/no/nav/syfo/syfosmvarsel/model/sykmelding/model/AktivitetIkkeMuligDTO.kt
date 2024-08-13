package no.nav.syfo.syfosmvarsel.model.sykmelding.model

data class AktivitetIkkeMuligDTO(
    val medisinskArsak: MedisinskArsakDTO?,
    val arbeidsrelatertArsak: ArbeidsrelatertArsakDTO?
)
