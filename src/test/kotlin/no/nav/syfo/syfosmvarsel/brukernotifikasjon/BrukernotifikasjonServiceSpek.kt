package no.nav.syfo.syfosmvarsel.brukernotifikasjon

import io.kotest.core.spec.style.FunSpec
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.TestDB
import no.nav.syfo.syfosmvarsel.dropData
import no.nav.syfo.syfosmvarsel.hentBrukernotifikasjonListe
import no.nav.syfo.syfosmvarsel.kafka.toConsumerConfig
import no.nav.syfo.syfosmvarsel.kafka.toProducerConfig
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.syfosmvarsel.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.syfosmvarsel.util.KafkaTest
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.Produsent
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

class BrukernotifikasjonServiceSpek :
    FunSpec({
        val kafkaConfig = KafkaTest.setupKafkaConfig()
        val config =
            Environment(
                dittSykefravaerUrl = "https://dittsykefravaer",
                brukernotifikasjonTopic = "min-side.varsel-topic",
                databaseUsername = "user",
                databasePassword = "pwd",
                dbHost = "host",
                dbName = "smvarsel",
                dbPort = "5089",
                cluster = "cluster",
            )

        val kafkaBrukernotifikasjonProducerConfig =
            kafkaConfig.toProducerConfig(
                "syfosmvarsel",
                valueSerializer = StringSerializer::class,
                keySerializer = StringSerializer::class,
            )

        val producer = KafkaProducer<String, String>(kafkaBrukernotifikasjonProducerConfig)
        val brukernotifikasjonKafkaProducer =
            BrukernotifikasjonKafkaProducer(
                kafkaProducer = producer,
                brukernotifikasjonTopic = config.brukernotifikasjonTopic,
            )

        val consumerProperties =
            kafkaConfig.toConsumerConfig(
                "spek.integration-consumer",
                keyDeserializer = StringDeserializer::class,
                valueDeserializer = StringDeserializer::class
            )
        val kafkaConsumerOppgave = KafkaConsumer<String, String>(consumerProperties)
        kafkaConsumerOppgave.subscribe(listOf("min-side.varsel-topic"))

        val database = TestDB()
        val brukernotifikasjonService =
            BrukernotifikasjonService(
                database,
                brukernotifikasjonKafkaProducer,
                config.dittSykefravaerUrl,
                config.cluster
            )

        val sykmeldingId = UUID.randomUUID()
        val timestampOpprettet = OffsetDateTime.of(2020, 2, 10, 11, 0, 0, 0, ZoneOffset.UTC)
        val timestampOpprettetLocalDateTime = LocalDateTime.of(2020, 2, 10, 11, 0, 0, 0)
        val eventIdOpprettet = UUID.randomUUID()
        val timestampFerdig = OffsetDateTime.of(2020, 2, 12, 11, 0, 0, 0, ZoneOffset.UTC)

        val brukernotifikasjonDB =
            BrukernotifikasjonDB(
                sykmeldingId = sykmeldingId,
                timestamp = timestampOpprettet,
                event = "APEN",
                grupperingsId = sykmeldingId,
                eventId = eventIdOpprettet,
                notifikasjonstatus = Notifikasjonstatus.OPPRETTET,
            )

        val sykmeldingStatusKafkaMessageDTO =
            SykmeldingStatusKafkaMessageDTO(
                event =
                    SykmeldingStatusKafkaEventDTO(
                        sykmeldingId = sykmeldingId.toString(),
                        timestamp = timestampFerdig,
                        statusEvent = STATUS_SENDT,
                    ),
                kafkaMetadata =
                    KafkaMetadataDTO(
                        sykmeldingId = sykmeldingId.toString(),
                        timestamp = timestampFerdig,
                        fnr = "12345678912",
                        source = "syfoservice",
                    ),
            )

        afterTest { database.connection.dropData() }

        context("Test av opprettBrukernotifikasjon") {
            test(
                "opprettBrukernotifikasjon oppretter ny rad i databasen for oppretting av notifikasjon"
            ) {
                brukernotifikasjonService.opprettBrukernotifikasjon(
                    Brukernotifikasjon(
                        sykmeldingId = sykmeldingId.toString(),
                        mottattDato = timestampOpprettetLocalDateTime,
                        tekst = "tekst",
                        fnr = "12345678912"
                    )
                )

                val brukernotifikasjoner =
                    database.connection.hentBrukernotifikasjonListe(sykmeldingId)
                brukernotifikasjoner.size shouldBeEqualTo 1
                brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampOpprettet
                brukernotifikasjoner[0].event shouldBeEqualTo "APEN"
                brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].eventId shouldNotBe null
                brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo
                    Notifikasjonstatus.OPPRETTET
            }

            test(
                "opprettBrukernotifikasjon gjør ingenting hvis det allerede finnes en opprett-notifikasjon for sykmeldingen"
            ) {
                database.registrerBrukernotifikasjon(brukernotifikasjonDB)

                brukernotifikasjonService.opprettBrukernotifikasjon(
                    Brukernotifikasjon(
                        sykmeldingId = sykmeldingId.toString(),
                        mottattDato = timestampOpprettetLocalDateTime,
                        tekst = "tekst",
                        fnr = "fnr"
                    )
                )

                val brukernotifikasjoner =
                    database.connection.hentBrukernotifikasjonListe(sykmeldingId)
                brukernotifikasjoner.size shouldBeEqualTo 1
            }
        }

        context("Test av ferdigstillBrukernotifikasjon") {
            test(
                "ferdigstillBrukernotifikasjon oppretter ny rad i databasen for ferdigstilling av notifikasjon"
            ) {
                database.registrerBrukernotifikasjon(brukernotifikasjonDB)

                brukernotifikasjonService.ferdigstillBrukernotifikasjon(
                    sykmeldingStatusKafkaMessageDTO
                )

                val brukernotifikasjoner =
                    database.connection
                        .hentBrukernotifikasjonListe(sykmeldingId)
                        .sortedByDescending { it.timestamp }
                brukernotifikasjoner.size shouldBeEqualTo 2
                brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampFerdig
                brukernotifikasjoner[0].event shouldBeEqualTo "SENDT"
                brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].eventId shouldNotBe null
                brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.FERDIG
                brukernotifikasjoner[1] shouldBeEqualTo brukernotifikasjonDB
            }

            test(
                "ferdigstillBrukernotifikasjon gjør ingenting hvis den ikke finner noen notifikasjon for sykmeldingen"
            ) {
                brukernotifikasjonService.ferdigstillBrukernotifikasjon(
                    sykmeldingStatusKafkaMessageDTO
                )

                val brukernotifikasjoner =
                    database.connection.hentBrukernotifikasjonListe(sykmeldingId)
                brukernotifikasjoner.size shouldBeEqualTo 0
            }

            test("ferdigstillBrukernotifikasjon oppretter kun done en gang") {
                database.registrerBrukernotifikasjon(brukernotifikasjonDB)

                brukernotifikasjonService.ferdigstillBrukernotifikasjon(
                    sykmeldingStatusKafkaMessageDTO
                )
                brukernotifikasjonService.ferdigstillBrukernotifikasjon(
                    sykmeldingStatusKafkaMessageDTO
                )

                val brukernotifikasjoner =
                    database.connection
                        .hentBrukernotifikasjonListe(sykmeldingId)
                        .sortedByDescending { it.timestamp }
                brukernotifikasjoner.size shouldBeEqualTo 2
                brukernotifikasjoner[0].sykmeldingId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].timestamp shouldBeEqualTo timestampFerdig
                brukernotifikasjoner[0].event shouldBeEqualTo "SENDT"
                brukernotifikasjoner[0].grupperingsId shouldBeEqualTo sykmeldingId
                brukernotifikasjoner[0].eventId shouldNotBe null
                brukernotifikasjoner[0].notifikasjonstatus shouldBeEqualTo Notifikasjonstatus.FERDIG
                brukernotifikasjoner[1] shouldBeEqualTo brukernotifikasjonDB
            }
        }

        context("Ende til ende-test oppgave") {
            test("Oppretter brukernotifikasjon-oppgave korrekt") {
                brukernotifikasjonService.opprettBrukernotifikasjon(
                    Brukernotifikasjon(
                        sykmeldingId = sykmeldingId.toString(),
                        mottattDato = timestampOpprettetLocalDateTime,
                        tekst = "tekst",
                        fnr = "12345678912"
                    ),
                )

                val messages = kafkaConsumerOppgave.poll(Duration.ofMillis(5000)).toList()

                val newTimestamp = OffsetDateTime.now(ZoneOffset.UTC)

                messages.size shouldBeEqualTo 1
                val key: String = messages[0].key()
                val varsel: String = messages[0].value()
                key shouldBeEqualTo sykmeldingId.toString()

                val expectedVarsel =
                    VarselActionBuilder.opprett {
                        type = Varseltype.Oppgave
                        varselId = sykmeldingId.toString()
                        sensitivitet = Sensitivitet.High
                        ident = "12345678912"

                        tekst =
                            Tekst(
                                spraakkode = "nb",
                                tekst = "tekst",
                                default = true,
                            )
                        link =
                            "${config.dittSykefravaerUrl}/syk/sykefravaer/sykmeldinger/$sykmeldingId"
                        eksternVarsling { preferertKanal = EksternKanal.SMS }
                        produsent =
                            Produsent(
                                namespace = "teamsykmelding",
                                appnavn = "syfosmvarsel",
                                cluster = config.cluster,
                            )
                        metadata
                    }

                varsel.replace(
                    Regex(""""built_at"\s*:\s*"[^"]*""""),
                    """"built_at":"$newTimestamp""""
                ) shouldBeEqualTo
                    expectedVarsel.replace(
                        Regex(""""built_at"\s*:\s*"[^"]*""""),
                        """"built_at":"$newTimestamp""""
                    )
            }
        }
    })
