package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.VerificationRecord
import java.util.concurrent.Executor

data class VerificationExplanationUiState(
    val request: VerificationExplanationState = VerificationExplanationState(),
    val includeNotes: Boolean = false,
    val preview: Boolean = false,
    val payload: VerificationPayloadResult? = null
)

/** Memory-only page owner. UI commands and publications run on the host's UI executor.
 * Context is visible to the controller worker, while credentials exist only during confirmed send.
 */
class VerificationExplanationOwner(
    executor: Executor,
    publishExecutor: Executor = Executor { it.run() },
    private val onState: (VerificationExplanationUiState) -> Unit = {},
    explainPayload: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport =
        { payload, key, cancellation -> VerificationExplanationProvider().explain(payload, key, cancellation) }
) {
    var state = VerificationExplanationUiState(); private set
    private data class RequestKey(val recordId: String, val revision: Long, val level: String?, val language: AppLanguage)
    private data class Context(val record: VerificationRecord, val key: RequestKey)
    @Volatile private var context: Context? = null
    private var disposed = false
    private val controller = VerificationExplanationController(
        { context?.record }, { context?.key?.level }, { context?.key?.language ?: AppLanguage.ZH_CN },
        executor, publishExecutor, { update(state.copy(request = it)) }, explainPayload)

    fun updateContext(record: VerificationRecord?, level: String?, language: AppLanguage) {
        if (disposed) return
        val next = record?.let { Context(it, RequestKey(it.id, it.revision, level, language)) }
        val old = context
        context = next
        if (old?.key == next?.key) return
        val keepNotes = old != null && next != null && old.key.recordId == next.key.recordId &&
            old.key.revision == next.key.revision
        update(VerificationExplanationUiState(includeNotes = keepNotes && state.includeNotes,
            payload = next?.let { payload(it.record, it.key, keepNotes && state.includeNotes) }))
        controller.cancel()
    }

    fun preview() {
        if (!disposed && context != null && !state.request.loading && state.payload is VerificationPayloadResult.Ready)
            update(state.copy(preview = true, request = state.request.copy(error = null)))
    }

    fun dismissPreview() { update(state.copy(preview = false)) }

    fun setIncludeNotes(value: Boolean) {
        val current = context ?: return
        if (disposed || state.request.loading || state.preview) return
        update(state.copy(includeNotes = value, payload = payload(current.record, current.key, value)))
    }

    fun send(credentials: () -> StoredApiKey?) {
        val current = context ?: return
        if (disposed || !state.preview || state.request.loading || state.payload !is VerificationPayloadResult.Ready) return
        update(state.copy(preview = false))
        try {
            controller.explain(current.key.recordId, current.key.revision,
                VerificationExplanationOptions(state.includeNotes), credentials())
        } catch (error: Exception) {
            update(state.copy(request = VerificationExplanationState(error = error)))
        }
    }

    fun stop() { controller.cancel() }
    fun exit() {
        context = null
        update(VerificationExplanationUiState())
        controller.cancel()
    }
    fun dispose() { exit(); disposed = true; controller.dispose() }

    private fun payload(record: VerificationRecord, key: RequestKey, notes: Boolean) =
        VerificationExplanationPayload.build(record, VerificationComparisonEngine.compare(record),
            VerificationExplanationOptions(notes), key.level, key.language)
    private fun update(value: VerificationExplanationUiState) { state = value; onState(value) }
}
