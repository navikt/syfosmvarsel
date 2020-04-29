package no.nav.syfo.syfosmvarsel.statusendring

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarsel
import no.nav.syfo.syfosmvarsel.statusendring.kafka.StoppRevarselProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class StatusendringServiceSpek : Spek({
    val brukernotifikasjonServiceMock = mockk<BrukernotifikasjonService>()
    val stoppRevarselProducer = mockk<StoppRevarselProducer>()
    val statusendringService = StatusendringService(brukernotifikasjonServiceMock, stoppRevarselProducer)

    val sykmeldingId = UUID.randomUUID().toString()
    val timestamp = OffsetDateTime.of(2020, 2, 12, 11, 0, 0, 0, ZoneOffset.UTC)

    val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            statusEvent = StatusEventDTO.SENDT,
            arbeidsgiver = null,
            sporsmals = null
        ),
        kafkaMetadata = KafkaMetadataDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            fnr = "fnr",
            source = "syfoservice"
        )
    )

    beforeEachTest {
        clearAllMocks()
        every { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(any()) } just Runs
        every { stoppRevarselProducer.sendStoppRevarsel(any()) } just Runs
    }

    describe("Test av statusendring") {
        it("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er SENDT") {
            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO) }
            verify(exactly = 1) { stoppRevarselProducer.sendStoppRevarsel(StoppRevarsel("NY_SYKMELDING", sykmeldingId)) }
        }

        it("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er BEKREFTET") {
            val sykmeldingStatusKafkaMessageBekreftet = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = StatusEventDTO.BEKREFTET),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata)

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageBekreftet)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageBekreftet) }
            verify(exactly = 1) { stoppRevarselProducer.sendStoppRevarsel(StoppRevarsel("NY_SYKMELDING", sykmeldingId)) }
        }

        it("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er AVBRUTT") {
            val sykmeldingStatusKafkaMessageAvbrutt = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = StatusEventDTO.AVBRUTT),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata)

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageAvbrutt)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageAvbrutt) }
            verify(exactly = 1) { stoppRevarselProducer.sendStoppRevarsel(StoppRevarsel("NY_SYKMELDING", sykmeldingId)) }
        }

        it("handterStatusendring ferdigstiller ikke brukernotifikasjon hvis sykmelding er APEN") {
            val sykmeldingStatusKafkaMessageApen = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = StatusEventDTO.APEN),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata)

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageApen)

            verify(exactly = 0) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(any()) }
            verify(exactly = 0) { stoppRevarselProducer.sendStoppRevarsel(any()) }
        }
    }
})
