package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.LoggingMeta
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.log
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_FERDIG
import no.nav.syfo.syfosmvarsel.metrics.BRUKERNOT_OPPRETTET

class BrukernotifikasjonService(
    private val database: DatabaseInterface,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val servicebruker: String,
    private val tjenesterUrl: String
) {

    fun opprettBrukernotifikasjon(sykmeldingId: String, mottattDato: LocalDateTime, fnr: String, tekst: String, loggingMeta: LoggingMeta) {
        val brukernotifikasjonFinnesFraFor = database.brukernotifikasjonFinnesFraFor(sykmeldingId = UUID.fromString(sykmeldingId), event = "APEN")
        if (brukernotifikasjonFinnesFraFor) {
            log.info("Notifikasjon for ny sykmelding med id $sykmeldingId finnes fra før, ignorerer, {}", StructuredArguments.fields(loggingMeta))
        } else {
            val opprettBrukernotifikasjon = mapTilOpprettetBrukernotifikasjon(sykmeldingId, mottattDato)
            database.registrerBrukernotifikasjon(opprettBrukernotifikasjon)
            brukernotifikasjonKafkaProducer.sendOpprettmelding(
                Nokkel(servicebruker, opprettBrukernotifikasjon.grupperingsId.toString()),
                Oppgave(
                    opprettBrukernotifikasjon.timestamp.toInstant().toEpochMilli(),
                    fnr,
                    opprettBrukernotifikasjon.grupperingsId.toString(),
                    tekst,
                    lagOppgavelenke(tjenesterUrl),
                    4
                )
            )
            log.info("Opprettet brukernotifikasjon for sykmelding med id $sykmeldingId {}", StructuredArguments.fields(loggingMeta))
            BRUKERNOT_OPPRETTET.inc()
        }
    }

    fun ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId
        val apenBrukernotifikasjon = database.hentApenBrukernotifikasjon(sykmeldingId = UUID.fromString(sykmeldingId), event = sykmeldingStatusKafkaMessageDTO.event.statusEvent.name)
        if (apenBrukernotifikasjon == null) {
            log.info("Fant ingen notifikasjon for sykmelding med id $sykmeldingId som ikke er ferdigstilt")
        } else {
            val ferdigstiltBrukernotifikasjon = mapTilFerdigstiltBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO, apenBrukernotifikasjon)
            database.registrerBrukernotifikasjon(ferdigstiltBrukernotifikasjon)
            brukernotifikasjonKafkaProducer.sendDonemelding(
                Nokkel(servicebruker, apenBrukernotifikasjon.grupperingsId.toString()),
                Done(
                    ferdigstiltBrukernotifikasjon.timestamp.toInstant().toEpochMilli(),
                    sykmeldingStatusKafkaMessageDTO.kafkaMetadata.fnr,
                    ferdigstiltBrukernotifikasjon.grupperingsId.toString()
                )
            )
            log.info("Ferdigstilt brukernotifikasjon for sykmelding med id $sykmeldingId")
            BRUKERNOT_FERDIG.inc()
        }
    }

    private fun mapTilOpprettetBrukernotifikasjon(sykmeldingId: String, mottattDato: LocalDateTime): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(sykmeldingId),
            timestamp = mottattDato.atOffset(ZoneOffset.UTC),
            event = "APEN",
            grupperingsId = UUID.fromString(sykmeldingId),
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.OPPRETTET
        )

    private fun mapTilFerdigstiltBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO, opprettetBrukernotifikasjonDB: BrukernotifikasjonDB): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(sykmeldingStatusKafkaMessageDTO.kafkaMetadata.sykmeldingId),
            timestamp = sykmeldingStatusKafkaMessageDTO.event.timestamp,
            event = sykmeldingStatusKafkaMessageDTO.event.statusEvent.name,
            grupperingsId = opprettetBrukernotifikasjonDB.grupperingsId,
            eventId = UUID.randomUUID(),
            notifikasjonstatus = Notifikasjonstatus.FERDIG
        )

    private fun lagOppgavelenke(tjenesterUrl: String): String {
        return "$tjenesterUrl/sykefravaer"
    }
}
