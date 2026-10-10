package com.aegis.apa

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
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
    val explanationState = mutableStateOf(VerificationExplanationUiState())
    private val main = Handler(Looper.getMainLooper())
    private var explanationVisible = false
    private var explanationLevel: String? = "Level 0"
    private var explanationLanguage = AppLanguage.ZH_CN
    private val explanationOwner = VerificationExplanationOwner(Dispatchers.IO.asExecutor(),
        Executor { main.post(it) }, { explanationState.value = it })
    private val controller = VerificationController(store, Instant::now, Dispatchers.IO.asExecutor(),
        onState = { state.value = it; syncExplanation() }, publishExecutor = Executor { main.post(it) })

    fun createBaseline(title: String, target: VerificationTarget, summary: DiagnosticVerificationSummary) =
        controller.createBaseline(title, target, summary)
    fun recordChange(id: String, expectedRevision: Long, change: VerificationChange) {
        invalidateExplanation(id)
        controller.recordChange(id, expectedRevision, change)
    }
    fun recordRetest(id: String, expectedRevision: Long, summary: DiagnosticVerificationSummary) {
        invalidateExplanation(id)
        controller.recordRetest(id, expectedRevision, summary)
    }
    fun close(id: String, expectedRevision: Long, reason: VerificationCloseReason) {
        invalidateExplanation(id)
        controller.close(id, expectedRevision, reason)
    }
    fun retrySave(id: String, expectedRevision: Long) { invalidateExplanation(id); controller.retrySave(id, expectedRevision) }
    fun discardUnsaved(id: String, expectedRevision: Long) { invalidateExplanation(id); controller.discardUnsaved(id, expectedRevision) }
    fun delete(id: String) { invalidateExplanation(id); controller.delete(id) }
    fun clear() { explanationOwner.exit(); controller.clear() }
    fun select(id: String?) {
        if (id != state.value.selectedId) explanationOwner.exit()
        controller.select(id)
    }

    fun updateExplanationContext(visible: Boolean, level: String?, language: AppLanguage) {
        if (!visible && explanationVisible) explanationOwner.exit()
        explanationVisible = visible
        explanationLevel = level
        explanationLanguage = language
        syncExplanation()
    }
    fun previewExplanation() = explanationOwner.preview()
    fun dismissExplanationPreview() = explanationOwner.dismissPreview()
    fun includeExplanationNotes(value: Boolean) = explanationOwner.setIncludeNotes(value)
    fun sendExplanation(credentials: () -> StoredApiKey?) = explanationOwner.send(credentials)
    fun stopExplanation() = explanationOwner.stop()
    fun exitExplanation() { explanationVisible = false; explanationOwner.exit() }

    private fun invalidateExplanation(id: String) {
        if (state.value.selectedId == id) explanationOwner.exit()
    }
    private fun syncExplanation() {
        if (!explanationVisible) return
        val selected = state.value.unsavedRecord?.takeIf { it.id == state.value.selectedId }
            ?: state.value.records.firstOrNull { it.id == state.value.selectedId }
        explanationOwner.updateContext(selected, explanationLevel, explanationLanguage)
    }

    override fun onCleared() {
        controller.dispose()
        explanationOwner.dispose()
        super.onCleared()
    }
}
