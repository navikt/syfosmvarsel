package no.nav.syfo.syfosmvarsel

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sykmeldingStatusAivenTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val brukernotifikasjonOpprettTopic: String = getEnvVar("BRUKERNOTIFIKASJON_OPPRETT_TOPIC", "aapen-brukernotifikasjon-nyOppgave-v1"),
    val brukernotifikasjonDoneTopic: String = getEnvVar("BRUKERNOTIFIKASJON_DONE_TOPIC", "aapen-brukernotifikasjon-done-v1"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val dittSykefravaerUrl: String = getEnvVar("DITT_SYKEFRAVAER_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmvarsel"),
    val syfosmvarselDBURL: String = getEnvVar("DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val okSykmeldingTopicAiven: String = "teamsykmelding.ok-sykmelding",
    val avvistSykmeldingTopicAiven: String = "teamsykmelding.avvist-sykmelding",
    val manuellSykmeldingTopicAiven: String = "teamsykmelding.manuell-behandling-sykmelding"
) : KafkaConfig

data class VaultServiceUser(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
