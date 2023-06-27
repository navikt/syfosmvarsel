package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_FERDIG
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_OPPRETTET

class BrukernotifikasjonService(
    private val database: DatabaseInterface,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val dittSykefravaerUrl: String,
) {
    companion object {
        private const val NAMESPACE = "teamsykmelding"
        private const val APP = "syfosmvarsel"
    }

    fun opprettBrukernotifikasjon(
        sykmeldingId: String,
        mottattDato: LocalDateTime,
        fnr: String,
        tekst: String,
        loggingMeta: LoggingMeta,
    ) {
        val brukernotifikasjonFinnesFraFor =
            database.brukernotifikasjonFinnesFraFor(
                sykmeldingId = UUID.fromString(sykmeldingId),
                event = "APEN"
            )
        if (brukernotifikasjonFinnesFraFor) {
            log.info(
                "Notifikasjon for ny sykmelding med id $sykmeldingId finnes fra før, ignorerer, {}",
                StructuredArguments.fields(loggingMeta),
            )
        } else {
            val opprettBrukernotifikasjon =
                mapTilOpprettetBrukernotifikasjon(sykmeldingId, mottattDato)
            val nokkelInput =
                NokkelInputBuilder()
                    .withAppnavn(APP)
                    .withNamespace(NAMESPACE)
                    .withEventId(opprettBrukernotifikasjon.grupperingsId.toString())
                    .withFodselsnummer(fnr)
                    .withGrupperingsId(opprettBrukernotifikasjon.grupperingsId.toString())
                    .build()
            val oppgaveInput =
                OppgaveInputBuilder()
                    .withTidspunkt(opprettBrukernotifikasjon.timestamp.toLocalDateTime())
                    .withTekst(tekst)
                    .withLink(URL(lagOppgavelenke(dittSykefravaerUrl, sykmeldingId)))
                    .withSikkerhetsnivaa(4)
                    .withEksternVarsling(true)
                    .apply { withPrefererteKanaler(PreferertKanal.SMS) }
                    .build()

            brukernotifikasjonKafkaProducer.sendOpprettmelding(
                nokkelInput,
                oppgaveInput,
            )
            database.registrerBrukernotifikasjon(opprettBrukernotifikasjon)
            log.info(
                "Opprettet brukernotifikasjon for sykmelding med id $sykmeldingId {}",
                StructuredArguments.fields(loggingMeta),
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
            val nokkelInput =
                NokkelInputBuilder()
                    .withAppnavn(APP)
                    .withNamespace(NAMESPACE)
                    .withEventId(ferdigstiltBrukernotifikasjon.grupperingsId.toString())
                    .withGrupperingsId(ferdigstiltBrukernotifikasjon.grupperingsId.toString())
                    .withFodselsnummer(sykmeldingStatusKafkaMessageDTO.kafkaMetadata.fnr)
                    .build()
            val doneInput =
                DoneInputBuilder()
                    .withTidspunkt(ferdigstiltBrukernotifikasjon.timestamp.toLocalDateTime())
                    .build()
            brukernotifikasjonKafkaProducer.sendDonemelding(
                nokkel = nokkelInput,
                done = doneInput,
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
        return "$dittSykefravaerUrl/syk/sykmeldinger/$sykmeldingId"
    }
}
