package no.nav.syfo.syfosmvarsel

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val avvistSykmeldingTopic: String = getEnvVar("KAFKA_AVVIST_SYKMELDING_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sykmeldingManuellBehandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sykmeldingAutomatiskBehandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmeldingstatus-leesah-v1"),
    val brukernotifikasjonOpprettTopic: String = getEnvVar("BRUKERNOTIFIKASJON_OPPRETT_TOPIC", "aapen-brukernotifikasjon-nyOppgave-v1"),
    val brukernotifikasjonDoneTopic: String = getEnvVar("BRUKERNOTIFIKASJON_DONE_TOPIC", "aapen-brukernotifikasjon-done-v1"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val tjenesterUrl: String = getEnvVar("TJENESTER_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmvarsel"),
    val syfosmvarselDBURL: String = getEnvVar("DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val bestvarselmhandlingQueueName: String = getEnvVar("BESTVARSELMHANDLING_QUEUENAME"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH")
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
