package no.nav.syfo.syfosmvarsel

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sykmeldingStatusAivenTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val brukernotifikasjonOpprettTopic: String = "min-side.aapen-brukernotifikasjon-oppgave-v1",
    val brukernotifikasjonDoneTopic: String = "min-side.aapen-brukernotifikasjon-done-v1",
    val dittSykefravaerUrl: String = getEnvVar("DITT_SYKEFRAVAER_URL"),
    val okSykmeldingTopicAiven: String = "teamsykmelding.ok-sykmelding",
    val avvistSykmeldingTopicAiven: String = "teamsykmelding.avvist-sykmelding",
    val manuellSykmeldingTopicAiven: String = "teamsykmelding.manuell-behandling-sykmelding",
    val kafkaSchemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaSchemaRegistryUsername: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val sykmeldingNotifikasjon: String = "teamsykmelding.sykmeldingnotifikasjon"
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
