package no.nav.syfo.syfosmvarsel

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sykmeldingStatusAivenTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val brukernotifikasjonTopic: String = "min-side.aapen-brukervarsel-v1",
    val dittSykefravaerUrl: String = getEnvVar("DITT_SYKEFRAVAER_URL"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val sykmeldingNotifikasjon: String = "teamsykmelding.sykmeldingnotifikasjon",
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME")
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
