package no.nav.syfo.application

import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.syfosmvarsel.Environment

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        routing {
            registerNaisApi(applicationState)
        }
    }
