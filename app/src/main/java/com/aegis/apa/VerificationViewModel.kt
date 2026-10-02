package com.aegis.apa

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aegis.apa.agent.VerificationController
import com.aegis.apa.agent.VerificationStore
import com.aegis.apa.agent.VerificationUiState
import com.aegis.apa.model.DiagnosticVerificationSummary
import com.aegis.apa.model.VerificationChange
import com.aegis.apa.model.VerificationCloseReason
import com.aegis.apa.model.VerificationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.time.Instant
import java.util.concurrent.Executor

/** Verification has its own lifecycle and archive, independent from the ordinary chat session. */
class VerificationViewModel(store: VerificationStore) : ViewModel() {
    val state = mutableStateOf(VerificationUiState())
    private val main = Handler(Looper.getMainLooper())
    private val controller = VerificationController(store, Instant::now, Dispatchers.IO.asExecutor(),
        onState = { state.value = it }, publishExecutor = Executor { main.post(it) })

    fun createBaseline(title: String, target: VerificationTarget, summary: DiagnosticVerificationSummary) =
        controller.createBaseline(title, target, summary)
    fun recordChange(id: String, expectedRevision: Long, change: VerificationChange) =
        controller.recordChange(id, expectedRevision, change)
    fun recordRetest(id: String, expectedRevision: Long, summary: DiagnosticVerificationSummary) =
        controller.recordRetest(id, expectedRevision, summary)
    fun close(id: String, expectedRevision: Long, reason: VerificationCloseReason) =
        controller.close(id, expectedRevision, reason)
    fun delete(id: String) = controller.delete(id)
    fun clear() = controller.clear()
    fun select(id: String?) = controller.select(id)

    override fun onCleared() {
        controller.dispose()
        super.onCleared()
    }
}
