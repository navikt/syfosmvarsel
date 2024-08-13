package no.nav.syfo.syfosmvarsel.model.sykmelding.model

import java.time.LocalDate

data class ErIArbeidDTO(
    val egetArbeidPaSikt: Boolean,
    val annetArbeidPaSikt: Boolean,
    val arbeidFOM: LocalDate?,
    val vurderingsdato: LocalDate?
)
