package com.aegis.apa.agent

import java.util.concurrent.CancellationException

/** A handle belongs to exactly one request; registration races safely with cancellation. */
class CloudRequestCancellation(private val closeConnection: (() -> Unit) -> Unit = { it() }) {
    @Volatile private var cancelled = false
    private var close: (() -> Unit)? = null

    @Synchronized fun register(disconnect: () -> Unit) {
        if (cancelled) {
            runCatching(disconnect)
            throw CancellationException("Request stopped")
        }
        close = disconnect
    }

    fun cancel() {
        val disconnect = synchronized(this) {
            if (cancelled) return
            cancelled = true
            close.also { close = null }
        }
        disconnect?.let { action -> runCatching { closeConnection(action) } }
    }

    @Synchronized fun release() { close = null }
    fun ensureActive() { if (cancelled) throw CancellationException("Request stopped") }
}
