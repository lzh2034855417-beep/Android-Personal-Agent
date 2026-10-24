package com.aegis.apa

import com.aegis.apa.model.*
import com.aegis.apa.agent.GuidedDiagnosticController
import com.aegis.apa.agent.GuidedEvidenceAdapter

/** Shared Activity event boundary: only a newly authorized intent may dispatch host work. */
internal fun dispatchGuidedAction(controller: GuidedDiagnosticController, event: GuidedUiEvent,
    dispatch: (GuidedAction) -> Unit): Boolean {
    val action = when (event) {
        is GuidedUiEvent.RequestAction -> controller.state.assessment?.primaryAction?.takeIf {
            controller.markActionStarted(event.key, event.actionId)
        }
        is GuidedUiEvent.RetryAction -> controller.state.assessment?.primaryAction?.takeIf {
            controller.retryAction(event.key, event.actionId)
        }
        is GuidedUiEvent.RefreshHeatSnapshot -> if (controller.refreshHeatSnapshot(event.key))
            GuidedAction("heat.refresh_snapshot", GuidedActionKind.COLLECT, emptyList()) else null
        else -> null
    } ?: return false
    dispatch(action)
    return true
}

internal fun canRefreshGuidedHeatSnapshot(state: GuidedUiState): Boolean = GuidedDiagnosticController.canRefreshHeatSnapshot(state)

/** The Activity's primary, retry and manual refresh collections all deliver through this boundary. */
internal fun offerGuidedBasicSnapshot(controller: GuidedDiagnosticController, token: GuidedWorkToken,
    snapshot: DeviceSnapshot, sampleId: String): Boolean {
    val session = controller.state.session?.takeIf { it.key == token.key } ?: return false
    return when (val result = GuidedEvidenceAdapter.adapt(GuidedInput(null, snapshot, null), sampleId, session.answers)) {
        is GuidedSampleResult.Ready -> controller.offerSample(token, result.sample)
        is GuidedSampleResult.Rejected -> controller.failWork(token, result.reasonId)
    }
}

internal fun guidedVerificationTarget(state: GuidedUiState, key: GuidedSessionKey, actionId: String): VerificationTarget.AppGroup? {
    val session = state.session ?: return null
    if (session.key != key || session.scenario != GuidedScenario.IDLE_DRAIN || session.stage !in listOf(GuidedStage.READY, GuidedStage.AWAITING_USER_ACTION)) return null
    val assessment = state.assessment ?: return null
    val action = assessment.primaryAction
    if (action.id != actionId || actionId != "idle.verify_background" || action.kind != GuidedActionKind.OPEN_VERIFICATION) return null
    val reason = action.reasonIds.singleOrNull() ?: return null
    val candidate = assessment.candidates.filter { it.ruleId == reason }.singleOrNull() ?: return null
    if (!candidate.ruleId.startsWith("idle.background_activity_correlation:") || candidate.evidenceIds.isEmpty() ||
        candidate.evidenceIds.distinct().size != candidate.evidenceIds.size) return null
    val facts = candidate.evidenceIds.map { id -> assessment.facts.filter { it.id == id }.singleOrNull() ?: return null }
    val packages = facts.first().packages
    if (packages.size != 1 || facts.any { it.packages != packages }) return null
    // The allowance must belong to the one confirmed sample that actually owns every selected fact.
    val owner = session.samples.filter { sample -> facts.all { fact -> sample.evidence.filter { it.id == fact.id }.singleOrNull() == fact } }
        .singleOrNull() ?: return null
    if (packages !in owner.verificationEligibleAppGroups) return null
    return VerificationTarget.AppGroup(packages)
}

internal fun guidedVerificationSource(state: GuidedUiState, key: GuidedSessionKey, actionId: String,
    current: PowerDiagnosticSnapshot?): PowerDiagnosticSnapshot? {
    current ?: return null
    val target = guidedVerificationTarget(state, key, actionId) ?: return null
    val selected = state.assessment!!.candidates.single { it.ruleId == state.assessment.primaryAction.reasonIds.single() }
    val owner = state.session!!.samples.singleOrNull { sample -> selected.evidenceIds.all { id -> sample.evidence.any { it.id == id } } } ?: return null
    val rebuilt = com.aegis.apa.agent.GuidedEvidenceAdapter.adapt(GuidedInput(current, null, null), owner.id, owner.answersAtConfirmation)
        as? GuidedSampleResult.Ready ?: return null
    if (rebuilt.sample.evidence != owner.evidence || rebuilt.sample.sources != owner.sources ||
        target.packageNames !in rebuilt.sample.verificationEligibleAppGroups) return null
    return current
}
