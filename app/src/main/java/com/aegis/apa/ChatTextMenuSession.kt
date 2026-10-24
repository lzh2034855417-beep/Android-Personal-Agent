package com.aegis.apa

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Cancels the native menu's suspended session without cancelling the editor. */
internal class ChatTextMenuSession {
    private var active: Job? = null

    suspend fun show(nativeMenu: suspend () -> Unit) = coroutineScope {
        val session = launch(start = CoroutineStart.UNDISPATCHED) { nativeMenu() }
        active = session
        try {
            session.join()
        } finally {
            if (active === session) active = null
        }
    }

    fun dismiss(): Boolean {
        val session = active?.takeIf { it.isActive } ?: return false
        active = null
        session.cancel()
        return true
    }
}
