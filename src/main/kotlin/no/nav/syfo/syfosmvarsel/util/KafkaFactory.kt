package no.nav.syfo.syfosmvarsel.util

import io.confluent.kafka.serializers.KafkaAvroSerializer
import java.util.Properties
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.VaultSecrets
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.syfosmvarsel.domain.OppgaveVarsel
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarsel
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarselProducer
import no.nav.syfo.syfosmvarsel.varselutsending.VarselProducer
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaFactory private constructor() {
    companion object {
        fun getVarselProducer(kafkaBaseConfig: Properties, environment: Environment, diskresjonskodeService: DiskresjonskodePortType): VarselProducer {
            val kafkaVarselProducerConfig = kafkaBaseConfig.toProducerConfig(
                "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)

            val kafkaProducer = KafkaProducer<String, OppgaveVarsel>(kafkaVarselProducerConfig)
            return VarselProducer(diskresjonskodeService, kafkaProducer, environment.oppgavevarselTopic)
        }

        fun getStoppRevarselProducer(kafkaBaseConfig: Properties, environment: Environment): StoppRevarselProducer {
            val kafkaVarselProducerConfig = kafkaBaseConfig.toProducerConfig(
                "syfosmvarsel", valueSerializer = JacksonKafkaSerializer::class)

            val kafkaProducer = KafkaProducer<String, StoppRevarsel>(kafkaVarselProducerConfig)
            return StoppRevarselProducer(kafkaProducer, environment.stoppRevarselTopic)
        }

        fun getAvvistKafkaConsumer(kafkaBaseConfig: Properties, environment: Environment): KafkaConsumer<String, String> {
            val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
            )
            consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val avvistKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
            avvistKafkaConsumer.subscribe(listOf(environment.avvistSykmeldingTopic))
            return avvistKafkaConsumer
        }

        fun getNyKafkaConsumer(kafkaBaseConfig: Properties, environment: Environment): KafkaConsumer<String, String> {
            val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                "syfosmvarsel-consumer", valueDeserializer = StringDeserializer::class
            )
            consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val nyKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
            nyKafkaConsumer.subscribe(listOf(environment.sykmeldingAutomatiskBehandlingTopic, environment.sykmeldingManuellBehandlingTopic))
            return nyKafkaConsumer
        }

        fun getKafkaStatusConsumer(vaultSecrets: VaultSecrets, environment: Environment): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
            val kafkaBaseConfigForStatus = loadBaseConfig(environment, vaultSecrets).envOverrides()
            kafkaBaseConfigForStatus["auto.offset.reset"] = "latest"
            val properties = kafkaBaseConfigForStatus.toConsumerConfig("syfosmvarsel-consumer-2", JacksonKafkaDeserializer::class)
            properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val kafkaStatusConsumer = KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class))
            kafkaStatusConsumer.subscribe(listOf(environment.sykmeldingStatusTopic))
            return kafkaStatusConsumer
        }

        fun getBrukernotifikasjonKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): BrukernotifikasjonKafkaProducer {
            val kafkaBrukernotifikasjonProducerConfig = kafkaBaseConfig.toProducerConfig(
                "syfosmvarsel", valueSerializer = KafkaAvroSerializer::class, keySerializer = KafkaAvroSerializer::class)

            val kafkaproducerOpprett = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerConfig)
            val kafkaproducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerConfig)
            return BrukernotifikasjonKafkaProducer(kafkaproducerOpprett = kafkaproducerOpprett,
                kafkaproducerDone = kafkaproducerDone,
                brukernotifikasjonOpprettTopic = environment.brukernotifikasjonOpprettTopic,
                brukernotifikasjonDoneTopic = environment.brukernotifikasjonDoneTopic)
        }
    }
}
