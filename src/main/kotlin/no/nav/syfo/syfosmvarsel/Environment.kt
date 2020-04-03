package no.nav.syfo.syfosmvarsel

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val avvistSykmeldingTopic: String = getEnvVar("KAFKA_AVVIST_SYKMELDING_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sykmeldingManuellBehandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sykmeldingAutomatiskBehandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val oppgavevarselTopic: String = getEnvVar("KAFKA_OPPGAVEVARSEL_TOPIC", "aapen-syfo-oppgavevarsel-v1"),
    val stoppRevarselTopic: String = getEnvVar("KAFKA_OPPGAVEVARSEL_TOPIC", "aapen-syfo-stopprevarsel-v1"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmeldingstatus-leesah-v1"),
    val brukernotifikasjonOpprettTopic: String = getEnvVar("BRUKERNOTIFIKASJON_OPPRETT_TOPIC", "aapen-brukernotifikasjon-nyOppgave-v1"),
    val brukernotifikasjonDoneTopic: String = getEnvVar("BRUKERNOTIFIKASJON_DONE_TOPIC", "aapen-brukernotifikasjon-done-v1"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val tjenesterUrl: String = getEnvVar("TJENESTER_URL"),
    val diskresjonskodeEndpointUrl: String = getEnvVar("DISKRESJONSKODE_ENDPOINT_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmvarsel"),
    val syfosmvarselDBURL: String = getEnvVar("DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME")
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
