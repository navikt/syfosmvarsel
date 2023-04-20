package no.nav.syfo.syfosmvarsel.statusendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.brukernotifikasjon.BrukernotifikasjonService
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class StatusendringServiceSpek : FunSpec({
    val brukernotifikasjonServiceMock = mockk<BrukernotifikasjonService>()
    val statusendringService = StatusendringService(brukernotifikasjonServiceMock)

    val sykmeldingId = UUID.randomUUID().toString()
    val timestamp = OffsetDateTime.of(2020, 2, 12, 11, 0, 0, 0, ZoneOffset.UTC)

    val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = null,
            sporsmals = null,
        ),
        kafkaMetadata = KafkaMetadataDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            fnr = "fnr",
            source = "syfoservice",
        ),
    )

    beforeTest {
        clearAllMocks()
        every { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(any()) } just Runs
    }

    context("Test av statusendring") {
        test("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er SENDT") {
            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageDTO)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageDTO) }
        }

        test("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er BEKREFTET") {
            val sykmeldingStatusKafkaMessageBekreftet = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = STATUS_BEKREFTET),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageBekreftet)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageBekreftet) }
        }

        test("handterStatusendring ferdigstiller brukernotifikasjon hvis sykmelding er AVBRUTT") {
            val sykmeldingStatusKafkaMessageAvbrutt = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = STATUS_AVBRUTT),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageAvbrutt)

            verify(exactly = 1) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(sykmeldingStatusKafkaMessageAvbrutt) }
        }

        test("handterStatusendring ferdigstiller ikke brukernotifikasjon hvis sykmelding er APEN") {
            val sykmeldingStatusKafkaMessageApen = SykmeldingStatusKafkaMessageDTO(
                event = sykmeldingStatusKafkaMessageDTO.event.copy(statusEvent = STATUS_APEN),
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

            statusendringService.handterStatusendring(sykmeldingStatusKafkaMessageApen)

            verify(exactly = 0) { brukernotifikasjonServiceMock.ferdigstillBrukernotifikasjon(any()) }
        }
    }
})
