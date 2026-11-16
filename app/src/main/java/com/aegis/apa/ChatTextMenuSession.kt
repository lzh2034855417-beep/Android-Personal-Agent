package com.aegis.apa

import android.view.ActionMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Closes the native editing menu without clearing editor focus. */
internal class ChatTextMenuSession {
    private var active: Job? = null
    private var nativeMode: ActionMode? = null

    fun nativeStarted(mode: ActionMode) { nativeMode = mode }
    fun nativeFinished(mode: ActionMode) { if (nativeMode === mode) nativeMode = null }

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
        nativeMode?.let { mode ->
            nativeMode = null
            mode.finish()
            return true
        }
        val session = active?.takeIf { it.isActive } ?: return false
        active = null
        session.cancel()
        return true
    }
}
