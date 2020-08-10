package no.nav.syfo.syfosmvarsel

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import java.util.UUID
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.toList
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonDB
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.tilBrukernotifikasjon
import org.flywaydb.core.Flyway

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM brukernotifikasjon").executeUpdate()
        connection.prepareStatement("DELETE FROM varselstatus").executeUpdate()
        connection.commit()
    }
}

fun Connection.hentBrukernotifikasjonListe(sykmeldingId: UUID): List<BrukernotifikasjonDB> =
    this.prepareStatement(
        """
                 SELECT *
                   FROM brukernotifikasjon
                  WHERE sykmelding_id = ?
            """
    ).use {
        it.setObject(1, sykmeldingId)
        it.executeQuery().toList { tilBrukernotifikasjon() }
    }
