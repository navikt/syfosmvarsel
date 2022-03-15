package no.nav.syfo.syfosmvarsel

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sykmeldingStatusAivenTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val brukernotifikasjonOpprettTopic: String = "min-side.aapen-brukernotifikasjon-oppgave-v1",
    val brukernotifikasjonDoneTopic: String = "min-side.aapen-brukernotifikasjon-done-v1",
    val dittSykefravaerUrl: String = getEnvVar("DITT_SYKEFRAVAER_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmvarsel"),
    val syfosmvarselDBURL: String = getEnvVar("DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val okSykmeldingTopicAiven: String = "teamsykmelding.ok-sykmelding",
    val avvistSykmeldingTopicAiven: String = "teamsykmelding.avvist-sykmelding",
    val manuellSykmeldingTopicAiven: String = "teamsykmelding.manuell-behandling-sykmelding",
    val kafkaSchemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaSchemaRegistryUsername: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
