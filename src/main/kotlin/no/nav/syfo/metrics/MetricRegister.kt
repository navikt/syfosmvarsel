package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val METRICS_NS = "syfosmvarsel"

val AVVIST_SYKMELDING_MOTTATT: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("avvist_sykmelding_mottatt_count")
        .help("Antall avviste sykmeldinger som er mottatt")
        .register()
