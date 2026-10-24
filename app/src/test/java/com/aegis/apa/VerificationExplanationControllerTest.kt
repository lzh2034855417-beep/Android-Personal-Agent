package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class VerificationExplanationControllerTest {
    // Catches late work from an old record/revision being displayed, including queued UI publication.
    @Test fun selectionDeletionRevisionCancelAndDisposeInvalidateLateReplies() {
        for (invalidate in listOf("selection", "delete", "revision", "close", "cancel", "dispose")) {
            val work = Queue(); val publish = Queue()
            var record: com.aegis.apa.model.VerificationRecord? = VerificationFixtures.record()
            val controller = VerificationExplanationController({ record }, { "Level 0" }, { AppLanguage.EN }, work, publish,
                explainPayload = { _, _, _ -> AgentReport("late reply", emptyList(), "fake") })
            controller.explain("record-1", 2, VerificationExplanationOptions(), key())
            assertTrue(controller.state.loading)
            work.run()
            when (invalidate) {
                "selection" -> record = record!!.copy(id = "other")
                "delete" -> record = null
                "revision" -> record = record!!.copy(revision = 3)
                "close" -> record = record!!.copy(revision = 3, state = com.aegis.apa.model.VerificationState.CLOSED,
                    closeReason = com.aegis.apa.model.VerificationCloseReason.COMPLETED)
                "cancel" -> controller.cancel()
                "dispose" -> controller.dispose()
            }
            publish.run()
            assertNull("$invalidate leaked", controller.state.report)
            assertFalse(controller.state.loading)
        }
    }

    // Catches a missing-key request reaching the network boundary or mutating local verification.
    @Test fun noKeyNeverCallsProviderAndValidResultLivesOnlyInController() {
        val record = VerificationFixtures.record(); var calls = 0
        val c = VerificationExplanationController({ record }, { "Level 0" }, { AppLanguage.EN }, Executor { it.run() },
            explainPayload = { _, _, _ -> calls++; AgentReport("explanation", emptyList(), "fake") })
        c.explain(record.id, record.revision, VerificationExplanationOptions(), null)
        assertEquals(0, calls); assertEquals(AgentFailure.MISSING_KEY, (c.state.error as AgentFailureException).reason)
        c.explain(record.id, record.revision, VerificationExplanationOptions(), key())
        assertEquals("explanation", c.state.report!!.summary); assertEquals(1, calls)
        assertEquals(2L, record.revision)
        c.cancel(); assertNull(c.state.report)
    }

    // Catches revision mismatches and double clicks posting duplicate requests.
    @Test fun staleRequestAndRepeatedClickDoNotStartWork() {
        val record = VerificationFixtures.record(); val queue = Queue(); var calls = 0
        val c = VerificationExplanationController({ record }, { null }, { AppLanguage.EN }, queue,
            explainPayload = { _, _, _ -> calls++; AgentReport("ok", emptyList(), "fake") })
        c.explain(record.id, 1, VerificationExplanationOptions(), key()); assertFalse(c.state.loading)
        c.explain(record.id, 2, VerificationExplanationOptions(), key())
        c.explain(record.id, 2, VerificationExplanationOptions(), key()); queue.run(); assertEquals(1, calls)
    }

    // Catches queued requests sending evidence after deletion or switching the selected record.
    @Test fun deletionBeforeWorkerStartsNeverCallsProvider() {
        var record: com.aegis.apa.model.VerificationRecord? = VerificationFixtures.record()
        val queue = Queue(); var calls = 0
        val c = VerificationExplanationController({ record }, { "Level 0" }, { AppLanguage.EN }, queue,
            explainPayload = { _, _, _ -> calls++; AgentReport("late", emptyList(), "fake") })
        c.explain("record-1", 2, VerificationExplanationOptions(), key())
        record = null
        queue.run()
        assertEquals(0, calls); assertNull(c.state.report); assertFalse(c.state.loading)
    }

    // Catches stale outputs after authorization/language changes or incomplete lifecycle cleanup.
    @Test fun capabilityLanguageAndCancelInvalidatePendingWork() {
        for (change in listOf("capability", "language", "cancel")) {
            val record = VerificationFixtures.record(); val queue = Queue(); val ui = Queue()
            var level = "Level 2"; var lang = AppLanguage.EN
            val c = VerificationExplanationController({ record }, { level }, { lang }, queue, ui,
                explainPayload = { _, _, _ -> AgentReport("late", emptyList(), "fake") })
            c.explain(record.id, record.revision, VerificationExplanationOptions(), key())
            queue.run()
            when (change) { "capability" -> level = "Level 0"; "language" -> lang = AppLanguage.ZH_CN; else -> c.cancel() }
            ui.run()
            assertNull(c.state.report); assertFalse(c.state.loading)
        }
    }

    // Catches expired keys and oversized attachments reaching provider despite visible refusal.
    @Test fun expiredKeyAndOversizedAttachmentNeverCallProvider() {
        val record = VerificationFixtures.record().let { r -> r.copy(baseline = r.baseline.copy(metrics =
            r.baseline.metrics.map { it.copy(window = it.window.copy(resetId = "汉".repeat(6000))) })) }
        var calls = 0
        val c = VerificationExplanationController({ record }, { "Level 0" }, { AppLanguage.EN }, Executor { it.run() },
            explainPayload = { _, _, _ -> calls++; AgentReport("bad", emptyList(), "fake") })
        c.explain(record.id, record.revision, VerificationExplanationOptions(), key().copy(expiresAt = 0))
        assertEquals(AgentFailure.EXPIRED_KEY, (c.state.error as AgentFailureException).reason)
        c.explain(record.id, record.revision, VerificationExplanationOptions(), key())
        assertTrue(c.state.tooLarge); assertEquals(0, calls)
    }
    private fun key() = StoredApiKey("DeepSeek", "synthetic-only", Long.MAX_VALUE)
    private class Queue : Executor { private val tasks = ArrayDeque<Runnable>(); override fun execute(command: Runnable) { tasks.add(command) }
        fun run() { while (tasks.isNotEmpty()) tasks.removeFirst().run() } }
}
