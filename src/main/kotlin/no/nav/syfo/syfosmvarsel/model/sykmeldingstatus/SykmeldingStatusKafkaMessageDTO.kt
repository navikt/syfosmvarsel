package no.nav.syfo.syfosmvarsel.model.sykmeldingstatus

data class SykmeldingStatusKafkaMessageDTO(
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)
