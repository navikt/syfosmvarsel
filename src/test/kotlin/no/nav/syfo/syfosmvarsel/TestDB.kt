package no.nav.syfo.syfosmvarsel

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.syfosmvarsel.application.db.Database
import no.nav.syfo.syfosmvarsel.application.db.DatabaseInterface
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentialService
import no.nav.syfo.syfosmvarsel.application.db.VaultCredentials
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
        val vaultCredentialService = mockk<VaultCredentialService>()
        private val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/testdb-init.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.mountPathVault } returns ""
            every { mockEnv.databaseName } returns "database"
            every { mockEnv.syfosmvarselDBURL } returns psqlContainer.jdbcUrl
            every { vaultCredentialService.renewCredentialsTaskData = any() } returns Unit
            every { vaultCredentialService.getNewCredentials(any(), any(), any()) } returns VaultCredentials(
                "1",
                "username",
                "password"
            )
            try {
                database = Database(mockEnv, vaultCredentialService)
            } catch (ex: Exception) {
                log.error("error", ex)
                database = Database(mockEnv, vaultCredentialService)
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
