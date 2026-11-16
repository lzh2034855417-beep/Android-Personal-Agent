package com.aegis.apa

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ActionMode
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/** Observe the real platform menu, including defaults installed inside Compose text fields. */
@Composable
internal fun rememberChatTextMenuSession(editorFocused: () -> Boolean): ChatTextMenuSession {
    val view = LocalView.current
    val window = remember(view) { view.context.chatActivity()?.window }
    val focused = rememberUpdatedState(editorFocused)
    val session = remember { ChatTextMenuSession() }
    DisposableEffect(window, session) {
        val previous = window?.callback
        val observer = previous?.let { delegate ->
            object : Window.Callback by delegate {
                override fun onActionModeStarted(mode: ActionMode) {
                    delegate.onActionModeStarted(mode)
                    if (mode.type == ActionMode.TYPE_FLOATING && focused.value()) {
                        session.nativeStarted(mode)
                    }
                }

                override fun onActionModeFinished(mode: ActionMode) {
                    session.nativeFinished(mode)
                    delegate.onActionModeFinished(mode)
                }
            }
        }
        if (window != null && observer != null) window.callback = observer
        onDispose {
            session.dismiss()
            // Do not overwrite a callback another owner installed after ours.
            if (window != null && window.callback === observer) window.callback = previous
        }
    }
    return session
}

private fun Context.chatActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.chatActivity() else null
    else -> null
}
