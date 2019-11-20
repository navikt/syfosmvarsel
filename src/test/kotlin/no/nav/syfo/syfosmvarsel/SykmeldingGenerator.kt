package no.nav.syfo.syfosmvarsel

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AnnenFraversArsak
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.Sykmelding

fun opprettReceivedSykmelding(id: String, sykmelding: Sykmelding = opprettSykmelding()) = ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = "123124",
        tlfPasient = "123455",
        personNrLege = "123145",
        navLogId = "0412",
        msgId = id,
        legekontorOrgNr = "",
        legekontorHerId = "",
        legekontorReshId = "",
        legekontorOrgName = "Legevakt",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = "",
        fellesformat = "",
        tssid = ""
)

fun opprettSykmelding(
    id: String = UUID.randomUUID().toString(),
    pasientAktoerId: String = UUID.randomUUID().toString(),
    medisinskVurdering: MedisinskVurdering = opprettMedisinskVurdering(),
    skjermetForPasient: Boolean = false,
    perioder: List<Periode> = listOf(opprettPeriode()),
    prognose: Prognose = opprettPrognose(),
    utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>> = mapOf(),
    tiltakArbeidsplassen: String? = null,
    tiltakNAV: String? = null,
    andreTiltak: String? = null,
    meldingTilNAV: MeldingTilNAV? = null,
    meldingTilArbeidsgiver: String? = null,
    kontaktMedPasient: KontaktMedPasient = opprettKontaktMedPasient(),
    behandletTidspunkt: LocalDateTime = LocalDateTime.now(),
    behandler: Behandler = opprettBehandler(),
    avsenderSystem: AvsenderSystem = opprettAvsenderSystem(),
    arbeidsgiver: Arbeidsgiver = opprettArbeidsgiver(),
    msgid: String = UUID.randomUUID().toString(),
    syketilfelleStartDato: LocalDate? = null,
    signaturDato: LocalDateTime = LocalDateTime.now(),
    navnFastlege: String? = null
) = Sykmelding(
        id = id,
        msgId = msgid,
        pasientAktoerId = pasientAktoerId,
        medisinskVurdering = medisinskVurdering,
        skjermesForPasient = skjermetForPasient,
        perioder = perioder,
        prognose = prognose,
        utdypendeOpplysninger = utdypendeOpplysninger,
        tiltakArbeidsplassen = tiltakArbeidsplassen,
        tiltakNAV = tiltakNAV,
        andreTiltak = andreTiltak,
        meldingTilNAV = meldingTilNAV,
        meldingTilArbeidsgiver = meldingTilArbeidsgiver,
        kontaktMedPasient = kontaktMedPasient,
        behandletTidspunkt = behandletTidspunkt,
        behandler = behandler,
        avsenderSystem = avsenderSystem,
        arbeidsgiver = arbeidsgiver,
        syketilfelleStartDato = syketilfelleStartDato,
        signaturDato = signaturDato,
        navnFastlege = navnFastlege
)

fun opprettMedisinskVurdering(
    hovedDiagnose: Diagnose? = Diagnose("oid", "kode", "tekst"),
    bidiagnoser: List<Diagnose> = listOf(),
    svangerskap: Boolean = false,
    yrkesskade: Boolean = false,
    yrkesskadeDato: LocalDate? = null,
    annenFraversArsak: AnnenFraversArsak? = null
) = MedisinskVurdering(
        hovedDiagnose = hovedDiagnose,
        biDiagnoser = bidiagnoser,
        svangerskap = svangerskap,
        yrkesskade = yrkesskade,
        yrkesskadeDato = yrkesskadeDato,
        annenFraversArsak = annenFraversArsak
)

fun opprettPeriode(
    fom: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now().plusDays(10),
    aktivitetIkkeMulig: AktivitetIkkeMulig? = opprettAktivitetIkkeMulig(),
    avventendeInnspillTilArbeidsgiver: String? = null,
    behandlingsdager: Int? = null,
    gradert: Gradert? = null,
    reisetilskudd: Boolean = false
) = Periode(
        fom = fom,
        tom = tom,
        aktivitetIkkeMulig = aktivitetIkkeMulig,
        avventendeInnspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        behandlingsdager = behandlingsdager,
        gradert = gradert,
        reisetilskudd = reisetilskudd
)

fun opprettAktivitetIkkeMulig(
    medisinskArsak: MedisinskArsak? = opprettMedisinskArsak(),
    arbeidsrelatertArsak: ArbeidsrelatertArsak? = null
) = AktivitetIkkeMulig(
        medisinskArsak = medisinskArsak,
        arbeidsrelatertArsak = arbeidsrelatertArsak
)

fun opprettMedisinskArsak(
    beskrivelse: String = "test data",
    arsak: List<MedisinskArsakType> = listOf(MedisinskArsakType.values()[Random.nextInt(MedisinskArsakType.values().size)])
) = MedisinskArsak(
        beskrivelse = beskrivelse,
        arsak = arsak
)

fun opprettPrognose(
    arbeidsforEtterPeriode: Boolean = true,
    hennsynArbeidsplassen: String? = null,
    erIArbeid: ErIArbeid? = opprettErIArbeid(),
    erIkkeIArbeid: ErIkkeIArbeid? = null
) = Prognose(
        arbeidsforEtterPeriode = arbeidsforEtterPeriode,
        hensynArbeidsplassen = hennsynArbeidsplassen,
        erIArbeid = erIArbeid,
        erIkkeIArbeid = erIkkeIArbeid
)

fun opprettErIArbeid(
    egetArbeidPaSikt: Boolean = true,
    annetArbeidPaSikt: Boolean = true,
    arbeidFOM: LocalDate = LocalDate.now().plusDays(30),
    vurderingsdato: LocalDate = LocalDate.now()
) = ErIArbeid(
        egetArbeidPaSikt = egetArbeidPaSikt,
        annetArbeidPaSikt = annetArbeidPaSikt,
        arbeidFOM = arbeidFOM,
        vurderingsdato = vurderingsdato
)

fun opprettKontaktMedPasient(
    kontaktDato: LocalDate? = LocalDate.now(),
    begrunnelseIkkeKontakt: String? = null
) = KontaktMedPasient(kontaktDato = kontaktDato, begrunnelseIkkeKontakt = begrunnelseIkkeKontakt)

fun opprettBehandler(
    fornavn: String = "Fornavn",
    mellomnavn: String? = "Mellomnavn",
    etternavn: String = "Etternavnsen",
    aktoerId: String = "128731827",
    fnr: String = "1234567891",
    hpr: String? = null,
    her: String? = null,
    adresse: Adresse = opprettAdresse(),
    tlf: String? = null
) = Behandler(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        aktoerId = aktoerId,
        fnr = fnr,
        hpr = hpr,
        her = her,
        adresse = adresse,
        tlf = tlf
)

fun opprettAdresse(
    gate: String? = "Gate",
    postnummer: Int? = 557,
    kommune: String? = "Oslo",
    postboks: String? = null,
    land: String? = "NO"
) = Adresse(
        gate = gate,
        postnummer = postnummer,
        kommune = kommune,
        postboks = postboks,
        land = land
)

fun opprettAvsenderSystem(
    navn: String = "test",
    versjon: String = "1.2.3"
) = AvsenderSystem(
        navn = navn,
        versjon = versjon
)

fun opprettArbeidsgiver(
    harArbeidsgiver: HarArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
    legekontor: String = "HelseHus",
    yrkesbetegnelse: String = "Maler",
    stillingsprosent: Int = 100
) = Arbeidsgiver(
        harArbeidsgiver = harArbeidsgiver,
        navn = legekontor,
        yrkesbetegnelse = yrkesbetegnelse,
        stillingsprosent = stillingsprosent)
