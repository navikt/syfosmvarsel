package no.nav.syfo.syfosmvarsel.varselutsending.dkif

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class DkifClientSpek : Spek({
    val stsOidcClientMock = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/api/v1/personer/kontaktinformasjon") {
                when {
                    call.request.headers["Nav-Personidenter"] == "fnr" -> call.respond(DigitalKontaktinfoBolkDTO(
                        feil = emptyMap(),
                        kontaktinfo = mapOf("fnr" to DigitalKontaktinfoDTO("epost@adresse", true, "12345678", "fnr"))
                    ))
                    call.request.headers["Nav-Personidenter"] == "fnr-res" -> call.respond(DigitalKontaktinfoBolkDTO(
                        feil = emptyMap(),
                        kontaktinfo = mapOf("fnr-res" to DigitalKontaktinfoDTO(null, false, null, "fnr-res"))
                    ))
                    call.request.headers["Nav-Personidenter"] == "fnr-ikkeKI" -> call.respond(DigitalKontaktinfoBolkDTO(
                        feil = emptyMap(),
                        kontaktinfo = emptyMap()
                    ))
                    call.request.headers["Nav-Personidenter"] == "fnr-feil" -> call.respond(DigitalKontaktinfoBolkDTO(
                        feil = mapOf("fnr-feil" to FeilDTO("Her er det noe feil")),
                        kontaktinfo = emptyMap()
                    ))
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
        }
    }.start()

    val dkifClient = DkifClient(mockHttpServerUrl, stsOidcClientMock, httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    describe("Dkif happy-case") {
        it("erReservert er false for bruker som ikke er reservert") {
            var erReservert: Boolean? = null
            runBlocking {
                erReservert = dkifClient.erReservert("fnr", "sykmeldingsId")
            }

            erReservert shouldEqual false
        }

        it("erReservert er true for bruker som er reservert") {
            var erReservert: Boolean? = null
            runBlocking {
                erReservert = dkifClient.erReservert("fnr-res", "sykmeldingsId")
            }

            erReservert shouldEqual true
        }
    }

    describe("DkifClient feilsituasjoner") {
        it("Manglende kontaktinfo gir erReservert lik true") {
            var erReservert: Boolean? = null
            runBlocking {
                erReservert = dkifClient.erReservert("fnr-ikkeKI", "sykmeldingsId")
            }

            erReservert shouldEqual true
        }

        it("Funksjonell feilmelding gir erReservert lik true") {
            var erReservert: Boolean? = null
            runBlocking {
                erReservert = dkifClient.erReservert("fnr-feil", "sykmeldingsId")
            }

            erReservert shouldEqual true
        }
    }
})
