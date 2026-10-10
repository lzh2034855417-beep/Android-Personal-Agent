package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedDiagnosticControllerTest {
    private fun controller(): GuidedDiagnosticController {
        var id = 0
        return GuidedDiagnosticController(newId = { "session-${++id}" }).also { it.start(GuidedScenario.IDLE_DRAIN) }
    }
    private fun sample(id: String, temperature: Double = 0.0) =
        (GuidedEvidenceAdapter.adapt(GuidedInput(null, GuidedDiagnosticFixtures.device(temperature), null), id,
            GuidedAnswers()) as GuidedSampleResult.Ready).sample
    private fun confirm(c: GuidedDiagnosticController, id: String) {
        c.offerSample(sample(id)); assertTrue(c.confirmSample(c.previewSample(id)!!))
    }
    private fun key(c: GuidedDiagnosticController) = c.state.session!!.key

    @Test fun offerPreservesConfirmedAssessmentAndOnlyOneVisibleCandidate() {
        val c = controller(); confirm(c, "one")
        val prior = c.state.session!!; val result = c.state.assessment
        c.offerSample(sample("two")); c.offerSample(sample("three"))
        assertSame(prior, c.state.session); assertSame(result, c.state.assessment)
        assertEquals(listOf("one"), c.state.session!!.samples.map { it.id })
        assertEquals("three", c.state.availableSampleId); assertNull(c.previewSample("two"))
    }

    @Test fun thirdConfirmationRequiresExplicitExistingReplacementAndDuplicateIsIdempotent() {
        val c = controller(); confirm(c, "one"); confirm(c, "two")
        c.offerSample(sample("three")); val p = c.previewSample("three")!!
        assertFalse(c.confirmSample(p)); assertEquals("guided.replacement_required", c.state.errorId)
        assertFalse(c.confirmSample(p, "missing")); assertEquals(listOf("one", "two"), c.state.session!!.samples.map { it.id })
        assertTrue(c.confirmSample(p, "one")); val state = c.state
        assertEquals(listOf("three", "two"), state.session!!.samples.map { it.id })
        assertTrue(c.confirmSample(p, "one")); assertSame(state, c.state)
        assertTrue(c.confirmSample("three")); assertSame(state, c.state)
    }

    @Test fun confirmationBindsCurrentAnswersButDoesNotMarkFormReviewed() {
        val c = controller(); c.updateAnswers(GuidedAnswers(charging = VerificationConfirmation.NO))
        confirm(c, "one")
        assertFalse(c.state.session!!.answers.reviewed)
        assertEquals(c.state.session!!.answers, c.state.session!!.samples.single().answersAtConfirmation)
        assertEquals("idle.answer", c.state.assessment!!.primaryAction.id)
        assertEquals(GuidedStage.ANSWERING, c.state.session!!.stage)
    }

    @Test fun answerChangeInvalidatesResultAndPreviewThenExplicitReconfirmationRefreshesBinding() {
        val c = controller(); confirm(c, "one"); c.offerSample(sample("two"))
        val p = c.previewSample("two")!!; val old = key(c)
        c.updateAnswers(GuidedAnswers(reviewed = true))
        assertNull(c.state.assessment); assertFalse(c.confirmSample(p)); assertFalse(c.confirmSample("two"))
        assertEquals(old.answerRevision + 1, key(c).answerRevision)
        assertEquals(old.sampleRevision, key(c).sampleRevision)
        assertTrue(c.confirmSample(c.previewSample("one")!!))
        assertTrue(c.state.session!!.samples.single().answersAtConfirmation.reviewed)
        assertEquals("idle.open_observation", c.state.assessment!!.primaryAction.id)
        assertEquals(VerificationConfirmation.UNKNOWN, c.state.session!!.answers.charging)
    }

    @Test fun sameIdChangedCandidateInvalidatesOldPreviewAndConfirmedIdentityCannotBeOverwritten() {
        val c = controller(); c.offerSample(sample("one")); val p = c.previewSample("one")!!
        c.offerSample(sample("one", 10.0)); assertFalse(c.confirmSample(p))
        assertTrue(c.confirmSample(c.previewSample("one")!!)); val saved = c.state.session!!.samples.single()
        c.offerSample(sample("one", 20.0))
        assertEquals("guided.sample_id_conflict", c.state.errorId); assertEquals(saved, c.state.session!!.samples.single())
    }

    private class FakeWork(private val c: GuidedDiagnosticController) {
        var cancelled = false
        var delivered = false
        val token = c.beginWork(c.state.session!!.key) { cancelled = true }!!
        fun deliver(): Boolean = c.completeWork(token) { delivered = true }
    }

    @Test fun answerChangesCancelOwnedWorkAndLateSuccessOrErrorCannotMutate() {
        val c = controller(); val work = FakeWork(c)
        c.updateAnswers(GuidedAnswers(reviewed = true)); val state = c.state
        assertTrue(work.cancelled); assertFalse(work.deliver()); assertFalse(work.delivered)
        assertFalse(c.offerSample(work.token, sample("late"))); assertFalse(c.failWork(work.token, "late_error"))
        assertSame(state, c.state)
    }

    @Test fun sameInputNewRequestRejectsPreviousCallbackAndCompletionIsOneShot() {
        val c = controller(); val old = FakeWork(c); val current = FakeWork(c)
        assertTrue(old.cancelled); assertFalse(old.deliver()); assertTrue(current.deliver())
        assertFalse(current.deliver()); assertFalse(c.failWork(current.token, "too_late"))
    }

    @Test fun candidateRefreshCancelsWorkAndInvalidatesCallbackEvenWhenSessionKeyUnchanged() {
        val c = controller(); c.offerSample(sample("one")); val before = key(c); val work = FakeWork(c)
        c.offerSample(sample("two")); val state = c.state
        assertEquals(before, key(c)); assertTrue(work.cancelled); assertFalse(work.deliver()); assertSame(state, c.state)
    }

    @Test fun actionStartsOnlyCurrentPrimaryActionAndPauseForCollectionKeepsSession() {
        val c = controller(); c.updateAnswers(GuidedAnswers(reviewed = true)); c.evaluateLocal(key(c))
        val old = key(c)
        assertFalse(c.markActionStarted(old, "heat.collect_current"))
        assertTrue(c.markActionStarted(old, "idle.open_observation"))
        assertEquals(GuidedStage.AWAITING_USER_ACTION, c.state.session!!.stage)
        val work = FakeWork(c) // Merely navigating to collect is not an exit event.
        assertTrue(work.deliver()); assertNotNull(c.state.session)
        c.updateAnswers(GuidedAnswers(reviewed = true, mostlyIdle = VerificationConfirmation.YES))
        assertFalse(c.markActionStarted(old, "idle.open_observation"))
    }

    @Test fun endCancelsWorkRetainsReadableResultAndStartCreatesNewSession() {
        val c = controller(); confirm(c, "one"); val id = key(c).id; val result = c.state.assessment
        val work = FakeWork(c); c.end(); val ended = c.state
        assertTrue(work.cancelled); assertSame(result, ended.assessment)
        assertEquals(GuidedStage.ENDED, ended.session!!.stage); assertFalse(work.deliver())
        c.offerSample(sample("new")); c.updateAnswers(GuidedAnswers(reviewed = true))
        assertFalse(c.confirmSample("one")); assertSame(ended, c.state)
        c.start(GuidedScenario.USAGE_HEAT)
        assertNotEquals(id, key(c).id); assertTrue(c.state.session!!.samples.isEmpty()); assertNull(c.state.assessment)
    }

    @Test fun restartClearsEveryResultAnswerCandidateErrorAndOldRequest() {
        val c = controller(); confirm(c, "one"); c.offerSample(sample("two")); val work = FakeWork(c)
        assertTrue(c.failWork(work.token, "import_failed")); val pending = FakeWork(c); val old = key(c)
        c.restart(GuidedScenario.USAGE_HEAT)
        assertTrue(pending.cancelled); assertFalse(pending.deliver()); assertNotEquals(old.id, key(c).id)
        assertEquals(GuidedAnswers(), c.state.session!!.answers); assertTrue(c.state.session!!.samples.isEmpty())
        assertNull(c.state.assessment); assertNull(c.state.availableSampleId); assertNull(c.state.errorId)
        assertEquals(0L, key(c).sampleRevision); assertEquals(0L, key(c).answerRevision)
    }

    @Test fun workErrorIsSeparateFromEvidenceAndExplicitStopRejectsLateCallback() {
        val c = controller(); confirm(c, "one"); val result = c.state.assessment; val session = c.state.session
        val work = FakeWork(c); assertTrue(c.failWork(work.token, "import_failed"))
        assertSame(result, c.state.assessment); assertSame(session, c.state.session)
        val next = FakeWork(c); c.cancelWork(); assertTrue(next.cancelled); assertFalse(next.deliver())
        assertEquals("import_failed", c.state.errorId)
    }

    @Test fun disposePreventsAnyLateMutationAndNewAndroidOwnerStartsEmpty() {
        val c = controller(); confirm(c, "one"); val work = FakeWork(c); c.dispose(); val state = c.state
        assertTrue(work.cancelled); assertFalse(work.deliver()); c.restart(GuidedScenario.USAGE_HEAT)
        assertSame(state, c.state)
        assertNull(GuidedDiagnosticViewModel().state.value.session)
        assertNull(GuidedDiagnosticViewModel().state.value.assessment)
    }

    @Test fun sessionCollectionsAreDefensiveAndActiveStartCannotSwitchScenarioSilently() {
        val source = mutableListOf(sample("one"))
        val s = GuidedDiagnosticSession("id", GuidedScenario.IDLE_DRAIN, 0, 0, 0, GuidedAnswers(), source, GuidedStage.READY)
        source.clear(); assertEquals(1, s.samples.size)
        try { (s.samples as MutableList).clear(); fail("mutable session samples") } catch (_: UnsupportedOperationException) {}
        val c = controller(); c.start(GuidedScenario.USAGE_HEAT)
        assertEquals(GuidedScenario.IDLE_DRAIN, c.state.session!!.scenario)
        assertEquals("guided.restart_confirmation_required", c.state.errorId)
    }

    @Test fun staleEvaluationAndEqualAnswerUpdateDoNotChangeCurrentResult() {
        val c = controller(); confirm(c, "one"); val old = key(c)
        c.updateAnswers(GuidedAnswers(reviewed = true)); assertFalse(c.evaluateLocal(old)); assertNull(c.state.assessment)
        assertTrue(c.evaluateLocal(key(c))); val state = c.state; val work = FakeWork(c)
        c.updateAnswers(GuidedAnswers(reviewed = true)); assertSame(state, c.state); assertFalse(work.cancelled)
    }

    @Test fun repeatedEvaluationDoesNotRearmAlreadyStartedAction() {
        val c = controller(); c.updateAnswers(GuidedAnswers(reviewed = true)); assertTrue(c.evaluateLocal(key(c)))
        assertTrue(c.markActionStarted(key(c), "idle.open_observation"))
        val waiting = c.state
        assertTrue(c.evaluateLocal(key(c)))
        assertSame(waiting, c.state)
        assertEquals(GuidedStage.AWAITING_USER_ACTION, c.state.session!!.stage)
        assertFalse(c.markActionStarted(key(c), "idle.open_observation"))
    }

    @Test fun sameConfirmationDoesNotRearmStartedActionOrCancelValidWork() {
        val c = controller(); c.updateAnswers(GuidedAnswers(reviewed = true)); confirm(c, "one")
        assertTrue(c.markActionStarted(key(c), "idle.open_observation"))
        val waiting = c.state; val work = FakeWork(c)
        val preview = c.previewSample("one")!!; assertTrue(c.confirmSample(preview))
        assertSame(waiting, c.state); assertFalse(work.cancelled)
    }

    @Test fun malformedAndOversizedCandidatesPreserveUsableDataAndCandidate() {
        val c = controller(); confirm(c, "one"); c.offerSample(sample("two"))
        val s = c.state.session; val result = c.state.assessment
        val original = sample("bad")
        c.offerSample(original.copy(evidence = original.evidence + original.evidence.first()))
        assertEquals("duplicate_evidence_id", c.state.errorId); assertSame(s, c.state.session); assertSame(result, c.state.assessment)
        assertEquals("two", c.state.availableSampleId)
        c.offerSample(original.copy(id = "x".repeat(50_000)))
        assertEquals("summary_too_large", c.state.errorId); assertEquals("two", c.state.availableSampleId)
    }

    @Test fun allVersionComponentsGateWorkAndCancellationTriggeredCallbackCannotResurrect() {
        val c = controller(); val k = key(c)
        for (wrong in listOf(k.copy(id = "other"), k.copy(revision = 1), k.copy(sampleRevision = 1), k.copy(answerRevision = 1))) {
            assertNull(c.beginWork(wrong) { fail("never owned") })
        }
        var token: GuidedWorkToken? = null
        var lateAccepted = true
        token = c.beginWork(k) { lateAccepted = c.offerSample(token!!, sample("late")) }
        c.end(); assertFalse(lateAccepted); assertNull(c.state.availableSampleId)
        assertTrue(c.state.session!!.samples.isEmpty())
    }

    @Test fun heatControllerUsesFixedLocalRuleAndSnapshotsStayImmutableAcrossChanges() {
        val c = controller(); c.restart(GuidedScenario.USAGE_HEAT)
        c.updateAnswers(GuidedAnswers(reviewed = true, stillHot = VerificationConfirmation.YES, activity = GuidedActivity.GAME))
        assertTrue(c.evaluateLocal(key(c))); assertEquals("heat.collect_current", c.state.assessment!!.primaryAction.id)
        assertEquals(GuidedStage.NEEDS_EVIDENCE, c.state.session!!.stage)
        val before = c.state.session!!; confirm(c, "sensor")
        assertTrue(before.samples.isEmpty()); assertEquals(0L, before.sampleRevision)
        assertEquals(1L, c.state.session!!.sampleRevision); assertEquals(1L, c.state.session!!.answerRevision)
        assertEquals("heat.observe_condition", c.state.assessment!!.primaryAction.id)
        assertEquals(GuidedStage.READY, c.state.session!!.stage)
    }

    @Test fun delayedFormAndRestartExitDialogsMustMatchTheirFullSessionKey() {
        val c = controller(); val formKey = key(c)
        assertTrue(c.updateAnswers(formKey, GuidedAnswers(reviewed = true)))
        val current = c.state
        assertFalse(c.updateAnswers(formKey, GuidedAnswers()))
        assertFalse(c.restart(formKey, GuidedScenario.USAGE_HEAT)); assertFalse(c.end(formKey)); assertSame(current, c.state)
        assertTrue(c.restart(key(c), GuidedScenario.USAGE_HEAT)); val restarted = c.state
        assertFalse(c.updateAnswers(formKey, GuidedAnswers(reviewed = true)))
        assertFalse(c.end(formKey)); assertSame(restarted, c.state)
        assertTrue(c.end(key(c))); assertEquals(GuidedStage.ENDED, c.state.session!!.stage)
    }
}
