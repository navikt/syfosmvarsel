package no.nav.syfo.syfosmvarsel.traceinterceptor

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.request.header
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelineInterceptor
import java.util.UUID
import kotlinx.coroutines.withContext
import no.nav.syfo.syfosmvarsel.CoroutineMDCContext
import org.slf4j.MDC

suspend fun <T : Any> PipelineContext<T, ApplicationCall>.withTraceInterceptor(body: PipelineInterceptor<T, ApplicationCall>) {
    withContext(CoroutineMDCContext()) {
        try {
            MDC.put("Nav-Callid", call.request.header("Nav-Callid") ?: UUID.randomUUID().toString())
            MDC.put("Nav-Consumer-Id", call.request.header("Nav-Consumer-Id") ?: "syfosmvarsel")

            body(subject)
        } finally {
            MDC.remove("Nav-Callid")
            MDC.remove("Nav-Consumer-Id")
        }
    }
}
