package com.aegis.apa

import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedDiagnosticRoutingTest {
    private val fact = GuidedEvidence("s:1", GuidedEvidenceKind.APP_BACKGROUND_POWER, GuidedValue.Number(10.0, "mah"),
        "batterystats", DiagnosticSourceStatus.AVAILABLE, EvidenceFieldStatus.PARSED, VerificationEstimate.ACTUAL,
        listOf("com.example.app"), GuidedDiagnosticFixtures.now, null, null)
    private val candidate = GuidedCandidate("idle.background_activity_correlation:s:1", listOf("s:1"), emptyList())
    private val action = GuidedAction("idle.verify_background", GuidedActionKind.OPEN_VERIFICATION, listOf(candidate.ruleId))
    private val sample = GuidedSample("s", listOf(fact), GuidedAnswers(reviewed = true),
        verificationEligibleAppGroups = listOf(listOf("com.example.app")))
    private fun state(facts: List<GuidedEvidence> = listOf(fact), candidates: List<GuidedCandidate> = listOf(candidate),
        action: GuidedAction = this.action, samples: List<GuidedSample> = listOf(sample)) = GuidedUiState(
        GuidedDiagnosticSession("session", GuidedScenario.IDLE_DRAIN, 3, 2, 1, GuidedAnswers(reviewed = true), samples, GuidedStage.READY),
        GuidedAssessment(facts, candidates, emptyList(), action))

    @Test fun validGraphUsesTypedPackagesAndCurrentFullKey() {
        val state = state()
        assertEquals(VerificationTarget.AppGroup(listOf("com.example.app")), guidedVerificationTarget(state, state.session!!.key, action.id))
        assertNull(guidedVerificationTarget(state, state.session.key.copy(answerRevision = 2), action.id))
        assertNull(guidedVerificationTarget(state, state.session.key, "model.open"))
    }
    @Test fun missingAmbiguousDisagreeingOrDisallowedGraphFailsClosed() {
        val broken = listOf(
            state(facts = emptyList()), state(facts = listOf(fact, fact)), state(candidates = listOf(candidate, candidate)),
            state(candidates = listOf(GuidedCandidate(candidate.ruleId, emptyList(), emptyList()))),
            state(action = GuidedAction(action.id, action.kind, listOf(candidate.ruleId, candidate.ruleId))),
            state(samples = listOf(sample.copy(verificationEligibleAppGroups = emptyList()))),
            state(facts = listOf(fact.copy(packages = emptyList()))),
            state(facts = listOf(fact, fact.copy(id = "other", packages = listOf("com.other.app"))),
                candidates = listOf(GuidedCandidate(candidate.ruleId, listOf("s:1", "other"), emptyList()))),
            state(candidates = listOf(GuidedCandidate(candidate.ruleId, listOf("s:1", "s:1"), emptyList())))
        )
        broken.forEach { assertNull(guidedVerificationTarget(it, it.session!!.key, action.id)) }
    }
    @Test fun heatOnlyAndEndedSessionsCannotOpenPowerVerification() {
        val valid = state()
        val heat = GuidedUiState(GuidedDiagnosticSession("session", GuidedScenario.USAGE_HEAT, 3, 2, 1,
            GuidedAnswers(), listOf(sample), GuidedStage.READY), valid.assessment)
        assertNull(guidedVerificationTarget(heat, heat.session!!.key, action.id))
        val ended = valid.copy(session = valid.session!!.copy(stage = GuidedStage.ENDED))
        assertNull(guidedVerificationTarget(ended, ended.session!!.key, action.id))
    }
    @Test fun verificationPreviewRejectsChangedActualReportEvenWithSamePackagesAndAllowsRetainedSnapshot() {
        val original = VerificationFixtures.snapshot()
        val snapshot = original.copy(verificationMetadata = VerificationFixtures.metadata(),
            system = SystemPowerEvidence(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, 500.0, true)),
            apps = listOf(original.apps.single().copy(
            estimatedPowerMah = 300.0, backgroundPowerMah = 240.0, wakeLockDurationMillis = 900_000)))
        val answers = GuidedAnswers(reviewed = true)
        val actual = (com.aegis.apa.agent.GuidedEvidenceAdapter.adapt(GuidedInput(snapshot, null, null), "report", answers) as GuidedSampleResult.Ready).sample
        val support = actual.evidence.filter { it.kind == GuidedEvidenceKind.APP_BACKGROUND_POWER }
        val graph = GuidedCandidate("idle.background_activity_correlation:report", support.map { it.id }, emptyList())
        val state = state(support, listOf(graph), GuidedAction(action.id, action.kind, listOf(graph.ruleId)),
            listOf(actual.copy(verificationEligibleAppGroups = listOf(listOf("com.example.app")))))
        assertSame(snapshot, guidedVerificationSource(state, state.session!!.key, action.id, snapshot))
        assertNull(guidedVerificationSource(state, state.session.key, action.id,
            snapshot.copy(sampledAtInstant = snapshot.sampledAtInstant.plusSeconds(1))))
        assertNull(guidedVerificationSource(state, state.session.key.copy(sampleRevision = 9), action.id, snapshot))
    }
}
