package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.toList
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.UUID

fun DatabaseInterface.brukernotifikasjonFinnesFraFor(sykmeldingId: UUID, event: String): Boolean {
    connection.use { connection ->
        return connection.finnesFraFor(sykmeldingId, event)
    }
}

fun DatabaseInterface.hentApenBrukernotifikasjon(sykmeldingId: UUID, event: String): BrukernotifikasjonDB? =
    connection.use { connection ->
        if (connection.finnesFraFor(sykmeldingId, event)) {
            return null
        }
        val brukernotifikasjoner = connection.hentApenBrukernotifikasjon(sykmeldingId)
        return brukernotifikasjoner.firstOrNull()
    }

fun DatabaseInterface.registrerBrukernotifikasjon(brukernotifikasjonDB: BrukernotifikasjonDB) {
    connection.use { connection ->
        connection.registrerBrukernotifikasjon(brukernotifikasjonDB)
        connection.commit()
    }
}

private fun Connection.finnesFraFor(sykmeldingId: UUID, event: String): Boolean =
    this.prepareStatement(
        """
                SELECT 1 FROM brukernotifikasjon WHERE sykmelding_id=? AND event=?;
                """
    ).use {
        it.setObject(1, sykmeldingId)
        it.setString(2, event)
        it.executeQuery().next()
    }

fun Connection.registrerBrukernotifikasjon(brukernotifikasjonDB: BrukernotifikasjonDB) {
    this.prepareStatement(
        """
                INSERT INTO brukernotifikasjon(sykmelding_id, timestamp, event, grupperingsId, eventId, notifikasjonstatus) VALUES (?, ?, ?, ?, ?, ?)
                """
    ).use {
        it.setObject(1, brukernotifikasjonDB.sykmeldingId)
        it.setTimestamp(2, Timestamp.from(brukernotifikasjonDB.timestamp.toInstant()))
        it.setString(3, brukernotifikasjonDB.event)
        it.setObject(4, brukernotifikasjonDB.grupperingsId)
        it.setObject(5, brukernotifikasjonDB.eventId)
        it.setString(6, brukernotifikasjonDB.notifikasjonstatus.name)
        it.execute()
    }
}

private fun Connection.hentApenBrukernotifikasjon(sykmeldingId: UUID): List<BrukernotifikasjonDB> =
    this.prepareStatement(
        """
                 SELECT *
                   FROM brukernotifikasjon
                  WHERE sykmelding_id = ? AND event = 'APEN'
            """
    ).use {
        it.setObject(1, sykmeldingId)
        it.executeQuery().toList { tilBrukernotifikasjon() }
    }

fun ResultSet.tilBrukernotifikasjon(): BrukernotifikasjonDB =
    BrukernotifikasjonDB(
        sykmeldingId = UUID.fromString(getString("sykmelding_id")),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
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
