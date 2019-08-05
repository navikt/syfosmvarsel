package no.nav.syfo.syfosmvarsel.domain

import java.time.LocalDateTime

data class OppgaveVarsel(
    val type: String,
    val ressursId: String,
    val mottaker: String,
    val parameterListe: Map<String, String>,
    val utlopstidspunkt: LocalDateTime,
    val utsendelsestidspunkt: LocalDateTime,
    val varseltypeId: String,
    val oppgavetype: String,
    val oppgaveUrl: String,
    val repeterendeVarsel: Boolean
)