package no.nav.syfo.syfosmvarsel.model.sykmelding.kafka

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.syfosmvarsel.model.Merknad
import no.nav.syfo.syfosmvarsel.model.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.syfosmvarsel.model.sykmelding.model.BehandlerDTO
import no.nav.syfo.syfosmvarsel.model.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.syfosmvarsel.model.sykmelding.model.PrognoseDTO
import no.nav.syfo.syfosmvarsel.model.sykmelding.model.SykmeldingsperiodeDTO

data class EnkelSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val behandletTidspunkt: OffsetDateTime,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val tiltakArbeidsplassen: String?,
    val syketilfelleStartDato: LocalDate?,
    val behandler: BehandlerDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val arbeidsgiver: ArbeidsgiverDTO,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val prognose: PrognoseDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<Merknad>?
)
