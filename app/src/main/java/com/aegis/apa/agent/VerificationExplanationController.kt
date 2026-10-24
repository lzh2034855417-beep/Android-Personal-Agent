package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.VerificationRecord
import java.util.concurrent.Executor

data class VerificationExplanationState(val loading: Boolean = false, val report: AgentReport? = null,
    val error: Exception? = null, val tooLarge: Boolean = false)
class VerificationExplanationController(private val currentRecord: () -> VerificationRecord?,
    private val selectedLevel: () -> String?, private val language: () -> AppLanguage,
    private val executor: Executor, private val publishExecutor: Executor = Executor { it.run() },
    private val onState: (VerificationExplanationState) -> Unit = {},
    private val explainPayload: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport =
        { payload, key, cancellation -> VerificationExplanationProvider().explain(payload, key, cancellation) }) {
    var state = VerificationExplanationState(); private set
    private var generation = 0L
    private var disposed = false
    private var cancellation: CloudRequestCancellation? = null

    @Synchronized fun explain(recordId: String, revision: Long, options: VerificationExplanationOptions, credentials: StoredApiKey?) {
        val record = currentRecord()?.takeIf { it.id == recordId && it.revision == revision } ?: return
        if (disposed || state.loading) return
        if (credentials == null || credentials.apiKey.isBlank()) {
            update(VerificationExplanationState(error = AgentFailureException(AgentFailure.MISSING_KEY))); return
        }
        if (credentials.expiresAt <= System.currentTimeMillis()) {
            update(VerificationExplanationState(error = AgentFailureException(AgentFailure.EXPIRED_KEY))); return
        }
        val payload = VerificationExplanationPayload.build(record, VerificationComparisonEngine.compare(record), options, selectedLevel(), language())
        if (payload !is VerificationPayloadResult.Ready) { update(VerificationExplanationState(tooLarge = true)); return }
        val token = ++generation
        val request = CloudRequestCancellation()
        cancellation = request
        update(VerificationExplanationState(loading = true))
        executor.execute {
            val result = try {
                synchronized(this) {
                    val current = currentRecord()
                    if (disposed || generation != token || current?.id != recordId || current.revision != revision ||
                        selectedLevel() != payload.selectedLevel || language() != payload.language) request.cancel()
                }
                request.ensureActive()
                VerificationExplanationState(report = explainPayload(payload, credentials, request))
            }
                catch (_: java.util.concurrent.CancellationException) { VerificationExplanationState() }
                catch (error: Exception) { VerificationExplanationState(error = error) }
            publishExecutor.execute {
                synchronized(this) {
                    if (disposed || generation != token) return@synchronized
                    val current = currentRecord()
                    if (current?.id != recordId || current.revision != revision || selectedLevel() != payload.selectedLevel || language() != payload.language) {
                        request.cancel(); generation++; cancellation = null; update(VerificationExplanationState()); return@synchronized
                    }
                    cancellation = null
                    update(result)
                }
            }
        }
    }

    @Synchronized fun cancel() { generation++; cancellation?.cancel(); cancellation = null; update(VerificationExplanationState()) }
    @Synchronized fun dispose() { cancel(); disposed = true }
    private fun update(value: VerificationExplanationState) { state = value; onState(value) }
}
