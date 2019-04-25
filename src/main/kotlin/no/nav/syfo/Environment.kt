package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
        val avvistSykmeldingTopic: String = getEnvVar("KAFKA_AVVIST_SYKMELDING_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
        val oppgavevarselTopic: String = getEnvVar("KAFKA_OPPGAVEVARSEL_TOPIC", "aapen-syfo-oppgavevarsel-v1"),
        override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val tjenesterUrl: String = getEnvVar("TJENESTER_URL"),
        val cluster: String = getEnvVar("CLUSTER")
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
