package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedActionDispatchTest {
    private class Host {
        val controller = GuidedDiagnosticController(newId = { "session" })
        var dispatches = 0
        var token: GuidedWorkToken? = null
        init {
            controller.start(GuidedScenario.USAGE_HEAT)
            controller.updateAnswers(GuidedAnswers(reviewed = true, stillHot = VerificationConfirmation.YES,
                charging = VerificationConfirmation.NO, activity = GuidedActivity.GAME))
            controller.evaluateLocal(key())
        }
        fun key() = controller.state.session!!.key
        fun event() = GuidedUiEvent.RequestAction(controller.state.assessment!!.primaryAction.id, key())
        fun dispatch(event: GuidedUiEvent) = dispatchGuidedAction(controller, event) { action ->
            assertEquals(GuidedActionKind.COLLECT, action.kind)
            dispatches++
            token = controller.beginWork(key()) {}
        }
        fun complete(id: String, temperature: Double) {
            assertTrue(offerGuidedBasicSnapshot(controller, token!!, GuidedDiagnosticFixtures.device(temperature), id))
        }
        fun confirm(id: String) = assertTrue(controller.confirmSample(controller.previewSample(id)!!))
    }

    @Test fun primaryActionDispatchesOnceAcrossRecompositionAndCompletedWork() {
        val host = Host()
        val originalClick = host.event()
        assertTrue(host.dispatch(originalClick))
        assertFalse(host.dispatch(originalClick)) // An already queued old UI callback.
        assertFalse(host.dispatch(host.event())) // Same awaiting action after state publication/recomposition.
        host.complete("first", 45.0)
        val offer = host.controller.state
        assertFalse(host.dispatch(host.event())) // Completed job cannot re-collect and replace the offer.
        assertSame(offer, host.controller.state)
        assertEquals(1, host.dispatches)
    }

    @Test fun failedOrCancelledCollectionRequiresSeparateVersionedRetry() {
        for (cancel in listOf(false, true)) {
            val host = Host()
            assertTrue(host.dispatch(host.event()))
            if (cancel) host.controller.cancelWork() else assertTrue(host.controller.failWork(host.token!!, "guided.collection_failed"))
            assertFalse(host.dispatch(host.event()))
            val retry = GuidedUiEvent.RetryAction("heat.collect_current", host.key())
            assertTrue(host.dispatch(retry))
            assertFalse(host.dispatch(retry))
            assertEquals(2, host.dispatches)
            host.complete("recovered", 40.0)
            host.confirm("recovered")
            assertEquals(listOf("recovered"), host.controller.state.session!!.samples.map { it.id })
        }
    }

    @Test fun secondHeatReadingUsesManualEventOfferAndExplicitConfirmationAfterTaskEnds() {
        for (taskEnded in listOf(false, true)) {
            val host = Host()
            assertTrue(host.dispatch(host.event()))
            host.complete("first", 45.0)
            assertTrue(host.controller.state.session!!.samples.isEmpty())
            host.confirm("first")
            if (taskEnded) {
                host.controller.updateAnswers(host.controller.state.session!!.answers.copy(stillHot = VerificationConfirmation.NO))
                host.controller.evaluateLocal(host.key())
                assertEquals("heat.end", host.controller.state.assessment!!.primaryAction.id)
            }
            assertTrue(canRefreshGuidedHeatSnapshot(host.controller.state))
            val refresh = GuidedUiEvent.RefreshHeatSnapshot(host.key())
            val first = host.controller.state.session!!.samples.single()
            assertTrue(host.dispatch(refresh))
            assertFalse(host.dispatch(refresh))
            assertEquals(2, host.dispatches)
            host.complete("second", 35.0)
            assertEquals(listOf(first), host.controller.state.session!!.samples) // Delivery is only an offer.
            val preview = host.controller.previewSample("second")!!
            assertEquals(host.controller.state.session!!.answers, preview.sample.answersAtConfirmation)
            assertTrue(host.controller.confirmSample(preview))
            val temperatures = host.controller.state.assessment!!.facts.filter { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }
            assertEquals(listOf(45.0, 35.0), temperatures.map { (it.value as GuidedValue.Number).value })
            assertEquals(2, host.controller.state.session!!.samples.size)
            host.controller.end()
            assertFalse(canRefreshGuidedHeatSnapshot(host.controller.state))
            assertFalse(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(host.key())))
        }
    }

    @Test fun warningPriorityAndFullKeyPreventManualRefresh() {
        val host = Host()
        val stale = GuidedUiEvent.RefreshHeatSnapshot(host.key())
        val report = com.aegis.apa.tool.PowerDiagnosticParser.parse(listOf(com.aegis.apa.tool.RawDiagnosticSection(
            "thermalservice", DiagnosticSourceStatus.AVAILABLE, "Current Thermal Status: 3")), GuidedDiagnosticFixtures.now)
        val s = (GuidedEvidenceAdapter.adapt(GuidedInput(report, GuidedDiagnosticFixtures.device(), null), "warning",
            host.controller.state.session!!.answers) as GuidedSampleResult.Ready).sample
        host.controller.offerSample(s); host.confirm("warning")
        assertEquals("heat.view_system_warning", host.controller.state.assessment!!.primaryAction.id)
        assertFalse(canRefreshGuidedHeatSnapshot(host.controller.state))
        assertFalse(host.dispatch(stale))
        assertFalse(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(host.key())))
        assertEquals(0, host.dispatches)
    }

    @Test fun manualRefreshKeepsWorkOwnershipFullKeyAndTwoSampleReplacementContract() {
        val host = Host()
        assertTrue(host.dispatch(host.event())); host.complete("first", 45.0); host.confirm("first")
        val current = host.key()
        for (wrong in listOf(current.copy(id = "old"), current.copy(revision = current.revision + 1),
            current.copy(sampleRevision = current.sampleRevision + 1), current.copy(answerRevision = current.answerRevision + 1))) {
            assertFalse(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(wrong)))
        }
        assertTrue(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(current)))
        val token = host.token
        assertFalse(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(host.key()))) // Running collection is never cancelled/replaced.
        assertSame(token, host.token)
        host.complete("second", 35.0)
        assertFalse(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(host.key()))) // Pending offer keeps its identity.
        assertEquals("second", host.controller.state.availableSampleId)
        host.confirm("second")
        assertTrue(host.dispatch(GuidedUiEvent.RefreshHeatSnapshot(host.key())))
        host.complete("third", 30.0)
        val preview = host.controller.previewSample("third")!!
        assertFalse(host.controller.confirmSample(preview))
        assertEquals(listOf("first", "second"), host.controller.state.session!!.samples.map { it.id })
        assertTrue(host.controller.confirmSample(preview, "first"))
        assertEquals(listOf("third", "second"), host.controller.state.session!!.samples.map { it.id })
        assertEquals(3, host.dispatches)
    }
}
