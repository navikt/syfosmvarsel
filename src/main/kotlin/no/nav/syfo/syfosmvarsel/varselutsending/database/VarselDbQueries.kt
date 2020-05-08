package no.nav.syfo.syfosmvarsel.varselutsending.database

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.toList

fun DatabaseInterface.hentVarsel(sykmeldingId: UUID): VarselDB? =
    connection.use { connection ->
        val varsler = connection.hentVarsel(sykmeldingId)
        return varsler.firstOrNull()
    }

fun DatabaseInterface.registrerVarsel(varselDB: VarselDB) {
    connection.use { connection ->
        if (!connection.finnesFraFor(varselDB.sykmeldingId)) {
            connection.registrerVarsel(varselDB)
            connection.commit()
        }
    }
}

fun DatabaseInterface.finnesFraFor(sykmeldingId: UUID): Boolean =
    connection.use { connection ->
        connection.finnesFraFor(sykmeldingId)
    }

private fun Connection.finnesFraFor(sykmeldingId: UUID): Boolean =
    this.prepareStatement(
        """
                SELECT 1 FROM varselstatus WHERE sykmelding_id=?;
                """
    ).use {
        it.setObject(1, sykmeldingId)
        it.executeQuery().next()
    }

fun Connection.registrerVarsel(varselDB: VarselDB) {
    this.prepareStatement(
        """
                INSERT INTO varselstatus(sykmelding_id, opprettet, mottaker_fnr, varselbestilling_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setObject(1, varselDB.sykmeldingId)
        it.setTimestamp(2, Timestamp.from(varselDB.opprettet.toInstant()))
        it.setString(3, varselDB.mottakerFnr)
        it.setObject(4, varselDB.varselbestillingId)
        it.execute()
    }
}

private fun Connection.hentVarsel(sykmeldingId: UUID): List<VarselDB> =
    this.prepareStatement(
        """
                 SELECT *
                   FROM varselstatus
                  WHERE sykmelding_id = ?
            """
    ).use {
        it.setObject(1, sykmeldingId)
        it.executeQuery().toList { tilVarsel() }
    }

fun ResultSet.tilVarsel(): VarselDB =
    VarselDB(
        sykmeldingId = UUID.fromString(getString("sykmelding_id")),
        opprettet = getTimestamp("opprettet").toInstant().atOffset(ZoneOffset.UTC),
        mottakerFnr = getString("mottaker_fnr"),
        varselbestillingId = UUID.fromString(getString("varselbestilling_id"))
    )
