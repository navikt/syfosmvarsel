package no.nav.syfo.syfosmvarsel.metrics

import io.prometheus.client.Counter

const val METRICS_NS = "syfosmvarsel"

val AVVIST_SM_VARSEL_OPPRETTET: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("avvist_sykmelding_opprettetvarsel_count")
        .help("Antall opprettede varsel for avviste sykmeldinger")
        .register()

val NY_SM_VARSEL_OPPRETTET: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("ny_sykmelding_opprettetvarsel_count")
        .help("Antall opprettede varsel for nye sykmeldinger")
        .register()

val BRUKERNOT_OPPRETTET: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("brukernot_opprettet_count")
        .help("Antall opprettede brukernotifikasjoner")
        .register()

val BRUKERNOT_FERDIG: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("brukernot_ferdig_count")
        .help("Antall ferdigstilte brukernotifikasjoner")
        .register()
