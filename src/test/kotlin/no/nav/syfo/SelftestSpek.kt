package no.nav.syfo

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.api.registerNaisApi
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SelftestSpek : Spek({
    val applicationState = ApplicationState()

    describe("Calling selftest with successful liveness and readyness tests") {
        with(TestApplicationEngine()) {
            start()
            application.initRouting(applicationState)

            it("Returns ok on isalive") {
                applicationState.running = true

                with(handleRequest(HttpMethod.Get, "/isalive")) {
                    response.status()?.isSuccess() shouldEqual true
                    response.content shouldNotEqual null
                }
            }
            it("Returns ok on isready") {
                applicationState.initialized = true

                with(handleRequest(HttpMethod.Get, "/isready")) {
                    println(response.status())
                    response.status()?.isSuccess() shouldEqual true
                    response.content shouldNotEqual null
                }
            }
            it("Returns error on failed isalive") {
                applicationState.running = false

                with(handleRequest(HttpMethod.Get, "/isalive")) {
                    response.status()?.isSuccess() shouldNotEqual true
                    response.content shouldNotEqual null

                }
            }
            it("Returns error on failed isready") {
                applicationState.initialized = false

                with(handleRequest(HttpMethod.Get, "/isready")) {
                    response.status()?.isSuccess() shouldNotEqual true
                    response.content shouldNotEqual null

                }
            }
        }
    }

    describe("Calling selftests with unsucessful liveness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNaisApi(readynessCheck = { true }, livenessCheck = { false })
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/isalive")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldNotEqual null
                }
            }
        }
    }

    describe("Calling selftests with unsucessful readyness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNaisApi(readynessCheck = { false }, livenessCheck = { true })
            }

            it("Returns internal server error when readyness check fails") {
                with(handleRequest(HttpMethod.Get, "/isready")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldNotEqual null
                }
            }
        }
    }
})
