package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.toList

fun DatabaseInterface.hentBrukernotifikasjon(sykmeldingId: UUID, event: String): BrukernotifikasjonDB? =
    connection.use { connection ->
        val brukernotifikasjoner = connection.hentBrukernotifikasjon(sykmeldingId, event)
        return brukernotifikasjoner.firstOrNull()
    }

fun DatabaseInterface.registrerBrukernotifikasjon(brukernotifikasjonDB: BrukernotifikasjonDB) {
    connection.use { connection ->
        if (!connection.finnesFraFor(brukernotifikasjonDB)) {
            connection.registrerBrukernotifikasjon(brukernotifikasjonDB)
        connection.commit()
        }
    }
}

private fun Connection.finnesFraFor(brukernotifikasjonDB: BrukernotifikasjonDB): Boolean =
    this.prepareStatement(
        """
                SELECT 1 FROM brukernotifikasjon WHERE sykmelding_id=? AND event=?;
                """
    ).use {
        it.setObject(1, brukernotifikasjonDB.sykmeldingId)
        it.setString(2, brukernotifikasjonDB.event)
        it.executeQuery().next()
    }

fun Connection.registrerBrukernotifikasjon(brukernotifikasjonDB: BrukernotifikasjonDB) {
    this.prepareStatement(
        """
                INSERT INTO brukernotifikasjon(sykmelding_id, timestamp, event, grupperingsId, eventId, notifikasjonstatus) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setObject(1, brukernotifikasjonDB.sykmeldingId)
        it.setTimestamp(2, Timestamp.valueOf(getAdjustedToLocalDateTime(brukernotifikasjonDB.timestamp)))
        it.setString(3, brukernotifikasjonDB.event)
        it.setObject(4, brukernotifikasjonDB.grupperingsId)
        it.setObject(5, brukernotifikasjonDB.eventId)
        it.setString(6, brukernotifikasjonDB.notifikasjonstatus.name)
        it.execute()
    }
}

private fun Connection.hentBrukernotifikasjon(sykmeldingId: UUID, event: String): List<BrukernotifikasjonDB> =
    this.prepareStatement(
        """
                 SELECT *
                   FROM brukernotifikasjon
                  WHERE sykmelding_id = ? AND event != ?
            """
    ).use {
        it.setObject(1, sykmeldingId)
        it.setString(2, event)
        it.executeQuery().toList { tilBrukernotifikasjon() }
    }

fun ResultSet.tilBrukernotifikasjon(): BrukernotifikasjonDB =
        BrukernotifikasjonDB(
            sykmeldingId = UUID.fromString(getString("sykmelding_id")),
            timestamp = getAdjustedOffsetDateTime(getTimestamp("timestamp").toLocalDateTime()),
            event = getString("event"),
            grupperingsId = UUID.fromString(getString("grupperingsId")),
            eventId = UUID.fromString(getString("eventId")),
            notifikasjonstatus = tilNotifikasjonstatus(getString("notifikasjonstatus"))
        )

private fun tilNotifikasjonstatus(status: String): Notifikasjonstatus {
    return when (status) {
        "OPPRETTET" -> Notifikasjonstatus.OPPRETTET
        "FERDIG" -> Notifikasjonstatus.FERDIG
        else -> throw IllegalStateException("Sykmeldingen har ukjent notifikasjonstatus, skal ikke kunne skje")
    }
}

fun getAdjustedOffsetDateTime(localDateTime: LocalDateTime): OffsetDateTime {
    return localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
}

fun getAdjustedToLocalDateTime(timestamp: OffsetDateTime) =
    timestamp.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
