package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_FERDIG
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_OPPRETTET
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.Produsent
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder

data class Brukernotifikasjon(
    val sykmeldingId: String,
    val mottattDato: LocalDateTime,
    val tekst: String,
    val fnr: String,
)

class BrukernotifikasjonService(
    private val database: DatabaseInterface,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val dittSykefravaerUrl: String,
    private val cluster: String,
) {
    companion object {
        private const val NAMESPACE = "teamsykmelding"
        private const val APP = "syfosmvarsel"
    }

    fun opprettBrukernotifikasjon(
        brukernotifikasjon: Brukernotifikasjon,
    ) {
        val brukernotifikasjonFinnesFraFor =
            database.brukernotifikasjonFinnesFraFor(
                sykmeldingId = UUID.fromString(brukernotifikasjon.sykmeldingId),
                event = "APEN"
            )
        if (brukernotifikasjonFinnesFraFor) {
            log.info(
                "Notifikasjon for ny sykmelding med id ${brukernotifikasjon.sykmeldingId} finnes fra før, ignorerer",
            )
        } else {
            val opprettBrukernotifikasjon =
                mapTilOpprettetBrukernotifikasjon(
                    brukernotifikasjon.sykmeldingId,
                    brukernotifikasjon.mottattDato
                )
            val newVarselId = brukernotifikasjon.sykmeldingId
            val varsel =
                VarselActionBuilder.opprett {
                    type = Varseltype.Oppgave
                    varselId = newVarselId
                    sensitivitet = Sensitivitet.High
                    ident = brukernotifikasjon.fnr
                    tekst =
                        Tekst(
                            spraakkode = "nb",
                            tekst = brukernotifikasjon.tekst,
                            default = true,
                        )
                    link = lagOppgavelenke(dittSykefravaerUrl, brukernotifikasjon.sykmeldingId)
                    eksternVarsling { preferertKanal = EksternKanal.SMS }
                    produsent =
                        Produsent(
                            namespace = NAMESPACE,
                            appnavn = APP,
                            cluster = cluster,
                        )
                }

            brukernotifikasjonKafkaProducer.sendOpprettmelding(
                varselId = newVarselId,
                varsel = varsel,
            )
            database.registrerBrukernotifikasjon(opprettBrukernotifikasjon)
            log.info(
                "Opprettet brukernotifikasjon for sykmelding med id ${brukernotifikasjon.sykmeldingId}"
            )
            BRUKERNOT_OPPRETTET.inc()
        }
    }

    fun ferdigstillBrukernotifikasjon(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO
    ) {
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId
        val apenBrukernotifikasjon =
            database.hentApenBrukernotifikasjon(
                sykmeldingId = UUID.fromString(sykmeldingId),
                event = sykmeldingStatusKafkaMessageDTO.event.statusEvent,
            )
        if (apenBrukernotifikasjon == null) {
            log.info(
                "Fant ingen notifikasjon for sykmelding med id $sykmeldingId som ikke er ferdigstilt"
            )
        } else {
            val ferdigstiltBrukernotifikasjon =
                mapTilFerdigstiltBrukernotifikasjon(
                    sykmeldingStatusKafkaMessageDTO,
                    apenBrukernotifikasjon
                )
            val inaktiverVarsel =
                VarselActionBuilder.inaktiver {
                    varselId = sykmeldingId
                    produsent =
                        Produsent(
                            namespace = NAMESPACE,
                            appnavn = APP,
                            cluster = cluster,
                        )
                }
            brukernotifikasjonKafkaProducer.sendDonemelding(
                varselId = sykmeldingId,
                varsel = inaktiverVarsel
            )
            log.info("Ferdigstilt brukernotifikasjon for sykmelding med id $sykmeldingId")
            database.registrerBrukernotifikasjon(ferdigstiltBrukernotifikasjon)
            BRUKERNOT_FERDIG.inc()
        }
    }

    private fun mapTilOpprettetBrukernotifikasjon(
        sykmeldingId: String,
        mottattDato: LocalDateTime,
    ): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(sykmeldingId),
            timestamp = mottattDato.atOffset(ZoneOffset.UTC),
            event = "APEN",
            grupperingsId = UUID.fromString(sykmeldingId),
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.OPPRETTET,
        )

    private fun mapTilFerdigstiltBrukernotifikasjon(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO,
        opprettetBrukernotifikasjonDB: BrukernotifikasjonDB,
    ): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId =
                UUID.fromString(sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId),
            timestamp = sykmeldingStatusKafkaMessageDTO.event.timestamp,
            event = sykmeldingStatusKafkaMessageDTO.event.statusEvent,
            grupperingsId = opprettetBrukernotifikasjonDB.grupperingsId,
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.FERDIG,
        )

    private fun lagOppgavelenke(dittSykefravaerUrl: String, sykmeldingId: String): String {
        return "$dittSykefravaerUrl/syk/sykefravaer/sykmeldinger/$sykmeldingId"
    }
}
