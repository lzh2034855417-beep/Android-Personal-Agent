package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import java.util.concurrent.Executor

/** The selected in-memory session is authoritative; read credentials only on confirmed send. */
object GuidedExplanationSession {
    fun configuration(): String = synchronized(ApiSession) {
        ApiSession.provider + ":" + ApiSession.configurationRevision
    }

    fun credentials(): StoredApiKey = synchronized(ApiSession) {
        val before = configuration()
        try { ApiSession.requireValid() }
        catch (failure: AgentFailureException) {
            // requireValid may clear expiry itself. Record only that atomic, nonsecret transition.
            throw GuidedExplanationCredentialFailure(failure.reason, before, configuration())
        }
    }
}

private class GuidedExplanationCredentialFailure(
    val reason: AgentFailure, val before: String, val after: String
) : IllegalStateException()

data class GuidedExplanationState(
    val options: GuidedExplanationOptions = GuidedExplanationOptions(),
    val payload: GuidedPayloadResult? = null,
    val previewVisible: Boolean = false,
    val loading: Boolean = false,
    val text: GuidedExplanationText? = null,
    val errorId: String? = null
)

class GuidedExplanationController(
    private val session: GuidedDiagnosticController,
    private val credentials: () -> StoredApiKey?,
    private val language: () -> AppLanguage,
    private val configuration: () -> String,
    private val executor: Executor,
    private val publishExecutor: Executor = Executor { it.run() },
    private val onState: (GuidedExplanationState) -> Unit = {},
    private val provider: (GuidedPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> GuidedExplanationResult =
        { payload, key, cancellation -> GuidedExplanationProvider().explain(payload, key, cancellation) }
) {
    var state = GuidedExplanationState(); private set
    private data class Context(val key: GuidedSessionKey?, val assessment: GuidedAssessment?,
        val language: AppLanguage, val configuration: String)
    private var observed = context()
    private var previewContext: Context? = null
    private var generation = 0L
    private var disposed = false
    private var request: CloudRequestCancellation? = null
    private var work: GuidedWorkToken? = null

    private fun context(): Context {
        val local = session.state
        return Context(local.session?.takeIf { it.stage != GuidedStage.ENDED }?.key,
            local.assessment, language(), configuration())
    }

    /** Commands and publications belong to the page UI dispatcher; workers never publish directly. */
    @Synchronized fun synchronizeInputs() {
        if (disposed) return
        val next = context()
        if (next != observed) {
            val newSession = next.key?.id != observed.key?.id
            invalidate(); observed = next
            if (newSession) update(state.copy(options = GuidedExplanationOptions()))
        }
    }

    @Synchronized fun preview(options: GuidedExplanationOptions) {
        if (disposed) return
        synchronizeInputs()
        if (state.loading) return
        if (state.options != options) setOptions(options)
        val local = session.state
        val active = local.session?.takeIf { it.stage != GuidedStage.ENDED } ?: return
        val assessment = local.assessment ?: return
        val payload = GuidedExplanationPayload.build(active, assessment, options, language())
        previewContext = context()
        update(state.copy(payload = payload, previewVisible = true, text = null, errorId = null))
    }

    @Synchronized fun setOptions(options: GuidedExplanationOptions) {
        if (disposed || state.options == options) return
        invalidate()
        update(state.copy(options = options))
    }

    @Synchronized fun dismissPreview() {
        previewContext = null
        update(state.copy(previewVisible = false, payload = null))
    }

    @Synchronized fun send(expectedKey: GuidedSessionKey) {
        if (disposed) return
        synchronizeInputs()
        val payload = state.payload as? GuidedPayloadResult.Ready ?: return
        val expected = previewContext ?: return
        if (state.loading || !state.previewVisible || expected.key != expectedKey || context() != expected ||
            payload.sessionKey != expectedKey || payload.language != expected.language) return
        // Consume preview before any credential supplier or executor can re-enter this owner.
        val token = ++generation
        previewContext = null
        update(state.copy(previewVisible = false, loading = true, text = null, errorId = null))
        val key = try { credentials() }
        catch (failure: GuidedExplanationCredentialFailure) {
            rejectCredentialFailure(expected, failure.reason, failure.before, failure.after); return
        } catch (failure: AgentFailureException) {
            rejectCredentialFailure(expected, failure.reason, expected.configuration, expected.configuration); return
        } catch (_: Exception) { null }
        if (context() != expected) { invalidate(); observed = context(); return }
        val reason = when {
            key == null || key.apiKey.isBlank() -> "guided.ai.missing_key"
            key.expiresAt <= System.currentTimeMillis() -> "guided.ai.expired_key"
            else -> null
        }
        if (reason != null) { update(state.copy(loading = false, payload = null, errorId = reason)); return }
        val cancellation = CloudRequestCancellation()
        request = cancellation
        val owned = session.beginWork(expectedKey) {
            synchronized(this) {
                generation++; request?.cancel(); request = null; work = null; previewContext = null
                update(state.copy(loading = false, payload = null, previewVisible = false,
                    text = null, errorId = "guided.ai.cancelled"))
            }
        } ?: run { invalidate(); return }
        work = owned
        try {
            executor.execute {
                val current = synchronized(this) { !disposed && generation == token && context() == expected }
                if (!current) cancellation.cancel()
                val result = try {
                    cancellation.ensureActive()
                    provider(payload, key!!, cancellation)
                } catch (_: java.util.concurrent.CancellationException) {
                    GuidedExplanationResult.Rejected("guided.ai.cancelled")
                } catch (_: Exception) { GuidedExplanationResult.Rejected("guided.ai.network_error") }
                publishExecutor.execute {
                    synchronized(this) {
                        if (disposed || generation != token) return@synchronized
                        if (context() != expected) { invalidate(); observed = context(); return@synchronized }
                        session.completeWork(owned) {
                            request = null; work = null
                            update(state.copy(loading = false, payload = null,
                                text = (result as? GuidedExplanationResult.Accepted)?.text,
                                errorId = (result as? GuidedExplanationResult.Rejected)?.reasonId))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            session.completeWork(owned) { request = null; work = null; cancellation.cancel()
                update(state.copy(loading = false, payload = null, errorId = "guided.ai.network_error")) }
        }
    }

    private fun rejectCredentialFailure(expected: Context, reason: AgentFailure, before: String, after: String) {
        if (disposed) return
        val current = context()
        // Only the credential read's own expiry transition may differ from the confirmed preview.
        // A changed session, language, assessment or unrelated configuration gets no stale error.
        val isCurrent = expected.configuration == before && current == expected.copy(configuration = after)
        invalidate(); observed = current
        if (isCurrent) update(state.copy(errorId = when (reason) {
            AgentFailure.MISSING_KEY -> "guided.ai.missing_key"
            AgentFailure.EXPIRED_KEY -> "guided.ai.expired_key"
            else -> "guided.ai.network_error"
        }))
    }

    @Synchronized fun cancel() {
        if (disposed) return
        invalidate()
        update(state.copy(errorId = "guided.ai.cancelled"))
    }

    @Synchronized fun invalidate() {
        generation++
        // Claim only our own token. Never cancel a newer collection task occupying the shared slot.
        work?.let { session.completeWork(it) {} }
        work = null; request?.cancel(); request = null; previewContext = null
        update(GuidedExplanationState(options = state.options))
    }

    @Synchronized fun dispose() { if (!disposed) { invalidate(); disposed = true } }
    private fun update(value: GuidedExplanationState) { state = value; onState(value) }
}
