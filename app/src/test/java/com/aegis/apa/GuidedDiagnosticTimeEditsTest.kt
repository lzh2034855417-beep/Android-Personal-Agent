package com.aegis.apa

import com.aegis.apa.agent.GuidedDiagnosticController
import com.aegis.apa.agent.GuidedEvidenceAdapter
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GuidedDiagnosticTimeEditsTest {
    @Test fun malformedStartTimeRevokesReviewedAssessmentActionWorkAndPreview() = malformedTimeEdit(true)
    @Test fun malformedEndTimeRevokesReviewedAssessmentActionWorkAndPreview() = malformedTimeEdit(false)

    private fun malformedTimeEdit(startBound: Boolean) {
        // A valid-only edit callback would leave the reviewed controller state authorized.
        val controller = GuidedDiagnosticController(newId = { "time-edit" })
        controller.start(GuidedScenario.IDLE_DRAIN)
        val start = Instant.parse("2026-10-01T10:00:00Z")
        val end = Instant.parse("2026-10-01T11:00:00Z")
        controller.updateAnswers(GuidedAnswers(startedAt = start, endedAt = end, reviewed = true))
        assertTrue(controller.evaluateLocal(controller.state.session!!.key))
        val action = controller.state.assessment!!.primaryAction.id
        assertTrue(controller.markActionStarted(controller.state.session!!.key, action))
        val sample = (GuidedEvidenceAdapter.adapt(GuidedInput(null, GuidedDiagnosticFixtures.device(), null),
            "candidate", GuidedAnswers()) as GuidedSampleResult.Ready).sample
        controller.offerSample(sample)
        val preview = controller.previewSample("candidate")!!
        val oldKey = controller.state.session!!.key
        var cancelled = false
        val work = controller.beginWork(oldKey) { cancelled = true }!!

        editGuidedTimeAnswer(if (startBound) "2026-10-01T10:00:00" else "2026-10-01T11:00:00") { parsed ->
            val answers = controller.state.session!!.answers
            controller.updateAnswers(oldKey, (if (startBound) answers.copy(startedAt = parsed)
                else answers.copy(endedAt = parsed)).copy(reviewed = false))
        }

        val edited = controller.state
        assertFalse("Malformed edits must revoke review", edited.session!!.answers.reviewed)
        assertNull(edited.assessment)
        assertEquals(GuidedStage.ANSWERING, edited.session!!.stage)
        assertEquals(oldKey.answerRevision + 1, edited.session!!.key.answerRevision)
        assertEquals(oldKey.sampleRevision, edited.session!!.key.sampleRevision)
        assertTrue(cancelled)
        assertFalse(controller.markActionStarted(oldKey, action))
        assertFalse(controller.markActionStarted(edited.session!!.key, action))
        assertFalse(controller.offerSample(work, sample.copy(id = "late")))
        assertFalse(controller.failWork(work, "late_error"))
        assertSame(edited, controller.state)
        assertFalse(controller.confirmSample(preview))
        assertEquals("guided.preview_stale", controller.state.errorId)
        assertTrue(controller.state.session!!.samples.isEmpty())
        assertNull(if (startBound) edited.session!!.answers.startedAt else edited.session!!.answers.endedAt)
        assertEquals(if (startBound) end else start,
            if (startBound) edited.session!!.answers.endedAt else edited.session!!.answers.startedAt)
    }

    @Test fun validAndBlankDraftsStillEmitTheirParsedOrUnknownBound() {
        var bound: Instant? = null
        editGuidedTimeAnswer(" 2026-10-01T10:00:00Z ") { bound = it }
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), bound)
        editGuidedTimeAnswer(" ") { bound = it }
        assertNull(bound)
    }
}
