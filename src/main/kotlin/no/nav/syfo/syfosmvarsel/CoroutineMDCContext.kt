package no.nav.syfo.syfosmvarsel

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC

private typealias MDCContextMap = Map<String, String>?

class CoroutineMDCContext : ThreadContextElement<MDCContextMap> {

    private var mdcContextMap: MDCContextMap = null

    companion object Key : CoroutineContext.Key<CoroutineMDCContext>

    override val key: CoroutineContext.Key<CoroutineMDCContext>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): MDCContextMap {
        val oldState = MDC.getCopyOfContextMap()
        mdcContextMap?.apply { MDC.setContextMap(this) } ?: MDC.clear()
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: MDCContextMap) {
        mdcContextMap = MDC.getCopyOfContextMap()
        oldState?.apply { MDC.setContextMap(this) } ?: MDC.clear()
    }
}
