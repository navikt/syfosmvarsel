package no.nav.syfo.syfosmvarsel

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.toList
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonDB
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.tilBrukernotifikasjon
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.util.UUID

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB : DatabaseInterface {
    companion object {
        var database: DatabaseInterface
        private val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/testdb-init.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "database"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            try {
                database = Database(mockEnv)
            } catch (ex: Exception) {
                log.error("error", ex)
                database = Database(mockEnv)
            }
        }
    }

    override val connection: Connection
        get() = database.connection
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM brukernotifikasjon").executeUpdate()
        connection.prepareStatement("DELETE FROM varselstatus").executeUpdate()
        connection.commit()
    }
}

fun Connection.hentBrukernotifikasjonListe(sykmeldingId: UUID): List<BrukernotifikasjonDB> =
    use { connection ->
        connection.prepareStatement(
            """
                 SELECT *
                   FROM brukernotifikasjon
                  WHERE sykmelding_id = ?
            """
        ).use {
            it.setObject(1, sykmeldingId)
            it.executeQuery().toList { tilBrukernotifikasjon() }
        }
    }
