package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.util.UUID

/** Memory-only owner. Callbacks and UI mutations share this lock; no work is dispatched here. */
class GuidedDiagnosticController(
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onState: (GuidedUiState) -> Unit = {}
) {
    @Volatile var state = GuidedUiState()
        private set
    private var disposed = false
    private var candidate: GuidedSample? = null
    private var candidateRevision = 0L
    private var preview: GuidedSamplePreview? = null
    private var receipt: ConfirmationReceipt? = null
    private var requestId = 0L
    private var work: OwnedWork? = null
    private data class OwnedWork(val token: GuidedWorkToken, val cancel: () -> Unit)
    private data class ConfirmationReceipt(val preview: GuidedSamplePreview, val replaceId: String?, val key: GuidedSessionKey)

    @Synchronized fun start(scenario: GuidedScenario) {
        if (disposed) return
        val session = state.session
        if (session != null && session.stage != GuidedStage.ENDED) {
            if (session.scenario != scenario) error("guided.restart_confirmation_required")
            return
        }
        fresh(scenario)
    }

    /** The caller confirms restart/scenario switching in the UI before invoking this method. */
    @Synchronized fun restart(scenario: GuidedScenario) { if (!disposed) fresh(scenario) }
    @Synchronized fun restart(key: GuidedSessionKey, scenario: GuidedScenario): Boolean {
        if (disposed || state.session?.key != key) return false
        fresh(scenario); return true
    }

    private fun fresh(scenario: GuidedScenario) {
        cancelOwnedWork()
        candidate = null; preview = null; receipt = null; candidateRevision++
        publish(GuidedUiState(GuidedDiagnosticSession(newId(), scenario, 0, 0, 0,
            GuidedAnswers(), emptyList(), GuidedStage.ANSWERING)))
    }

    @Synchronized fun updateAnswers(answers: GuidedAnswers) {
        val s = active() ?: return
        if (s.answers == answers) return
        invalidateInputs()
        publish(state.copy(session = s.copy(revision = s.revision + 1, answerRevision = s.answerRevision + 1,
            answers = answers.copy(), stage = if (answers.reviewed) GuidedStage.NEEDS_EVIDENCE else GuidedStage.ANSWERING),
            assessment = null, errorId = null))
    }
    @Synchronized fun updateAnswers(key: GuidedSessionKey, answers: GuidedAnswers): Boolean {
        if (active()?.key != key) return false
        updateAnswers(answers); return true
    }

    @Synchronized fun offerSample(sample: GuidedSample) {
        val s = active() ?: return
        if (sample.id.isBlank()) { error("guided.invalid_sample_id"); return }
        val confirmed = s.samples.firstOrNull { it.id == sample.id }
        if (confirmed != null && confirmed != sample.copy(answersAtConfirmation = confirmed.answersAtConfirmation)) {
            error("guided.sample_id_conflict"); return
        }
        if (!validate(sample.copy(answersAtConfirmation = s.answers))) return
        cancelOwnedWork()
        candidateRevision++; preview = null; receipt = null
        candidate = sample.copy()
        publish(state.copy(availableSampleId = sample.id, errorId = null))
    }

    /** The returned sample is exactly the previewed evidence plus the current declared answers. */
    @Synchronized fun previewSample(sampleId: String): GuidedSamplePreview? {
        val s = active() ?: return null
        val source = candidate?.takeIf { it.id == sampleId } ?: s.samples.firstOrNull { it.id == sampleId } ?: return null
        val bound = source.copy(answersAtConfirmation = s.answers.copy())
        if (!validate(bound)) return null
        return GuidedSamplePreview(s.key, candidateRevision, bound).also { preview = it }
    }

    /** Immediate convenience only. Delayed UI confirmations must carry the actual preview token. */
    @Synchronized fun confirmSample(sampleId: String, replaceSampleId: String? = null): Boolean {
        val s = active() ?: return false
        if (candidate == null && s.samples.any { it.id == sampleId && it.answersAtConfirmation == s.answers } && replaceSampleId == null) return true
        val p = preview?.takeIf { it.sample.id == sampleId } ?: return reject("guided.preview_required")
        return confirmSample(p, replaceSampleId)
    }

    @Synchronized fun confirmSample(preview: GuidedSamplePreview, replaceSampleId: String? = null): Boolean {
        val s = active() ?: return false
        val previous = receipt
        if (previous?.preview == preview && previous.replaceId == replaceSampleId && previous.key == s.key) return true
        if (this.preview != preview || preview.key != s.key || preview.candidateRevision != candidateRevision) return reject("guided.preview_stale")
        val id = preview.sample.id
        val existing = s.samples.indexOfFirst { it.id == id }
        if (existing >= 0 && replaceSampleId != null) return reject("guided.invalid_replacement")
        val replacement = replaceSampleId?.let { target -> s.samples.indexOfFirst { it.id == target } }
        if (replacement != null && replacement < 0) return reject("guided.invalid_replacement")
        if (existing < 0 && s.samples.size >= 2 && replacement == null) return reject("guided.replacement_required")
        if (!validate(preview.sample)) return false
        val samples = s.samples.toMutableList()
        when {
            existing >= 0 -> samples[existing] = preview.sample.copy()
            replacement != null -> samples[replacement] = preview.sample.copy()
            else -> samples.add(preview.sample.copy())
        }
        val changed = samples != s.samples
        if (!changed) {
            this.preview = null
            receipt = ConfirmationReceipt(preview, replaceSampleId, s.key)
            if (candidate?.id == id) {
                cancelOwnedWork(); candidate = null; candidateRevision++
                publish(state.copy(availableSampleId = null, errorId = null))
            }
            return true
        }
        invalidateInputs()
        if (candidate?.id == id) { candidate = null; candidateRevision++ }
        this.preview = null
        val updated = s.copy(revision = s.revision + 1, sampleRevision = s.sampleRevision + 1, samples = samples)
        val assessment = evaluate(updated)
        val evaluated = updated.copy(stage = stage(assessment))
        receipt = ConfirmationReceipt(preview, replaceSampleId, evaluated.key)
        publish(state.copy(session = evaluated, assessment = assessment, availableSampleId = candidate?.id, errorId = null))
        return true
    }

    @Synchronized fun evaluateLocal(key: GuidedSessionKey): Boolean {
        val s = active()?.takeIf { it.key == key } ?: return false
        // Inputs invalidate assessment. Re-evaluating unchanged inputs must not rearm an action.
        if (state.assessment != null) return true
        val assessment = evaluate(s)
        publish(state.copy(session = s.copy(stage = stage(assessment)), assessment = assessment, errorId = null))
        return true
    }

    private fun evaluate(s: GuidedDiagnosticSession) = when (s.scenario) {
        GuidedScenario.IDLE_DRAIN -> IdleDrainRuleSet.evaluate(s.answers, s.samples)
        GuidedScenario.USAGE_HEAT -> UsageHeatRuleSet.evaluate(s.answers, s.samples)
    }

    private fun stage(assessment: GuidedAssessment) = when (assessment.primaryAction.kind) {
        GuidedActionKind.ANSWER -> GuidedStage.ANSWERING
        GuidedActionKind.IMPORT_REPORT, GuidedActionKind.COLLECT -> GuidedStage.NEEDS_EVIDENCE
        else -> GuidedStage.READY
    }

    @Synchronized fun markActionStarted(actionId: String) {
        active()?.let { markActionStarted(it.key, actionId) }
    }

    @Synchronized fun markActionStarted(key: GuidedSessionKey, actionId: String): Boolean {
        val s = active()?.takeIf { it.key == key } ?: return false
        val action = state.assessment?.primaryAction?.takeIf { it.id == actionId } ?: return false
        if (s.stage == GuidedStage.AWAITING_USER_ACTION) return false
        if (action.kind == GuidedActionKind.END) { end(); return true }
        invalidateInputs()
        publish(state.copy(session = s.copy(revision = s.revision + 1, stage = GuidedStage.AWAITING_USER_ACTION), errorId = null))
        return true
    }

    /** A separate user retry consumes a new full-key receipt; it never repeats running work or an offered sample. */
    @Synchronized fun retryAction(key: GuidedSessionKey, actionId: String): Boolean {
        val s = active()?.takeIf { it.key == key && it.stage == GuidedStage.AWAITING_USER_ACTION } ?: return false
        val action = state.assessment?.primaryAction?.takeIf { it.id == actionId } ?: return false
        if (work != null || candidate != null || action.kind == GuidedActionKind.END) return false
        startManualIntent(s)
        return true
    }

    @Synchronized fun refreshHeatSnapshot(key: GuidedSessionKey): Boolean {
        val s = active()?.takeIf { it.key == key } ?: return false
        if (!canRefreshHeatSnapshot(state) || work != null || candidate != null) return false
        startManualIntent(s)
        return true
    }

    private fun startManualIntent(s: GuidedDiagnosticSession) {
        invalidateInputs()
        publish(state.copy(session = s.copy(revision = s.revision + 1, stage = GuidedStage.AWAITING_USER_ACTION), errorId = null))
    }

    companion object {
        /** An ended task is still an active session. Recognized warnings and input conflicts keep priority. */
        fun canRefreshHeatSnapshot(state: GuidedUiState): Boolean {
            val s = state.session ?: return false
            val assessment = state.assessment ?: return false
            return s.scenario == GuidedScenario.USAGE_HEAT && s.stage != GuidedStage.ENDED && s.answers.reviewed &&
                state.availableSampleId == null && assessment.primaryAction.kind !in setOf(GuidedActionKind.ANSWER, GuidedActionKind.VIEW_SYSTEM_WARNING) &&
                assessment.facts.any { it.kind in setOf(GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.THERMAL_STATUS) && it.value != null } &&
                s.samples.none { it.sources["battery"] in setOf(DiagnosticSourceStatus.UNSUPPORTED, DiagnosticSourceStatus.PERMISSION_DENIED) }
        }
    }

    /** Explicit caller-owned work only; install its cancellation before starting any worker. */
    @Synchronized fun beginWork(key: GuidedSessionKey, cancel: () -> Unit): GuidedWorkToken? {
        if (active()?.key != key) return null
        cancelOwnedWork()
        if (active()?.key != key) return null
        val token = GuidedWorkToken(key, ++requestId)
        work = OwnedWork(token, cancel)
        return token
    }

    /** One-shot delivery under the ownership lock. Call only on the owner's UI dispatcher. */
    @Synchronized fun completeWork(token: GuidedWorkToken, onCurrent: () -> Unit): Boolean {
        if (!owns(token)) return false
        work = null
        onCurrent()
        return true
    }

    @Synchronized fun offerSample(token: GuidedWorkToken, sample: GuidedSample): Boolean =
        completeWork(token) { offerSample(sample) }

    @Synchronized fun failWork(token: GuidedWorkToken, errorId: String): Boolean =
        completeWork(token) { error(errorId) }

    @Synchronized fun cancelWork() { cancelOwnedWork() }

    private fun owns(token: GuidedWorkToken) = active()?.key == token.key && work?.token == token

    private fun cancelOwnedWork() {
        val old = work
        work = null // Gate first: cancellation may race with, or itself trigger, delivery.
        if (old != null) runCatching { old.cancel() }
    }

    private fun invalidateInputs() { cancelOwnedWork(); preview = null; receipt = null }

    @Synchronized fun end() {
        val s = active() ?: return
        invalidateInputs(); candidate = null; candidateRevision++
        publish(state.copy(session = s.copy(revision = s.revision + 1, stage = GuidedStage.ENDED), availableSampleId = null))
    }
    @Synchronized fun end(key: GuidedSessionKey): Boolean {
        if (disposed || state.session?.key != key) return false
        end(); return true
    }

    @Synchronized fun dispose() { if (!disposed) { disposed = true; invalidateInputs(); candidate = null } }

    private fun active() = state.session?.takeIf { !disposed && it.stage != GuidedStage.ENDED }
    private fun publish(value: GuidedUiState) { state = value; onState(value) }
    private fun error(id: String) { publish(state.copy(errorId = id)) }
    private fun reject(id: String): Boolean { error(id); return false }
    private fun validate(sample: GuidedSample): Boolean = try {
        GuidedSummaryCodec.encode(sample); true
    } catch (e: GuidedSummaryException) { reject(e.reasonId) }
}
