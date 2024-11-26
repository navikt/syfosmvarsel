package no.nav.syfo.syfosmvarsel.application

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import no.nav.syfo.syfosmvarsel.Environment
import no.nav.syfo.syfosmvarsel.application.api.registerNaisApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, env.applicationPort) { routing { registerNaisApi(applicationState) } }
