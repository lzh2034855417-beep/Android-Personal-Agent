package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class VerificationExplanationOwnerTest {
    // Catches a host refresh resetting a retained request/result or issuing a second post.
    @Test fun sameContextRetainsInFlightRequestAndCompletedExplanation() {
        val work = Queue(); val publish = Queue(); var calls = 0
        val owner = VerificationExplanationOwner(work, publish, explainPayload = { _, _, _ ->
            calls++; AgentReport("retained explanation", emptyList(), "synthetic")
        })
        val record = VerificationFixtures.record()
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        owner.preview()
        assertTrue("Preview must be owned outside the lazy item", owner.state.preview)
        owner.send(::key)
        assertTrue(owner.state.request.loading)
        owner.updateContext(record.copy(), "Level 0", AppLanguage.EN)
        assertTrue(owner.state.request.loading)
        work.run(); publish.run()
        owner.updateContext(record.copy(), "Level 0", AppLanguage.EN)
        assertEquals("retained explanation", owner.state.request.report?.summary)
        assertEquals(1, calls)
    }

    // Catches only rejecting a reply later while leaving a stale request/loading/result visible now.
    @Test fun recordRevisionLevelLanguageAndExitImmediatelyInvalidateAndRejectLatePublication() {
        for (change in listOf("record", "revision", "level", "language", "exit", "dispose")) {
            val work = Queue(); val publish = Queue()
            var disconnected = false
            val owner = VerificationExplanationOwner(work, publish, explainPayload = { _, _, cancellation ->
                cancellation.register { disconnected = true }
                AgentReport("stale explanation", emptyList(), "synthetic")
            })
            val record = VerificationFixtures.record()
            owner.updateContext(record, "Level 0", AppLanguage.EN)
            owner.preview(); assertTrue(owner.state.preview); owner.send(::key); work.run()
            when (change) {
                "record" -> owner.updateContext(record.copy(id = "other"), "Level 0", AppLanguage.EN)
                "revision" -> owner.updateContext(record.copy(revision = 3), "Level 0", AppLanguage.EN)
                "level" -> owner.updateContext(record, "Level 2", AppLanguage.EN)
                "language" -> owner.updateContext(record, "Level 0", AppLanguage.ZH_CN)
                "exit" -> owner.exit()
                else -> owner.dispose()
            }
            assertTrue("$change must cancel transport", disconnected)
            assertFalse("$change must stop loading immediately", owner.state.request.loading)
            publish.run()
            assertNull("$change must reject old publication", owner.state.request.report)
        }
    }

    // Catches comparing only the final key, allowing a reply from a prior visit to reappear on reopening.
    @Test fun returningToSameRecordBeforeOldPublicationStillRejectsPriorVisit() {
        for (exit in listOf("record", "page")) {
            val work = Queue(); val publish = Queue()
            val owner = VerificationExplanationOwner(work, publish, explainPayload = { _, _, _ ->
                AgentReport("prior visit", emptyList(), "synthetic")
            })
            val record = VerificationFixtures.record()
            owner.updateContext(record, "Level 0", AppLanguage.EN)
            owner.preview(); owner.send(::key); work.run()
            if (exit == "record") owner.updateContext(record.copy(id = "other"), "Level 0", AppLanguage.EN)
            else owner.exit()
            owner.updateContext(record, "Level 0", AppLanguage.EN)
            publish.run()
            assertNull("$exit must invalidate the previous visit", owner.state.request.report)
            assertFalse(owner.state.request.loading)
        }
    }

    // Catches a closed/deleted detail leaking an old result even when no request remains in flight.
    @Test fun exitClearsCompletedResultAndReopeningStartsWithNotesOff() {
        val owner = VerificationExplanationOwner(Executor { it.run() }, explainPayload = { _, _, _ ->
            AgentReport("previous detail", emptyList(), "synthetic")
        })
        val record = VerificationFixtures.record()
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        owner.setIncludeNotes(true); owner.preview(); assertTrue(owner.state.preview); owner.send(::key)
        assertEquals("previous detail", owner.state.request.report?.summary)
        owner.exit()
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        assertNull(owner.state.request.report)
        assertFalse(owner.state.includeNotes)
        assertFalse(owner.state.preview)
    }

    // Catches reading credentials on preview, duplicate sends, or accidentally sending unchecked notes.
    @Test fun onlyConfirmedPreviewReadsCredentialsAndSendsReviewedNotesOnce() {
        val work = Queue(); var reads = 0; var calls = 0
        val prompts = mutableListOf<String>()
        val owner = VerificationExplanationOwner(work, explainPayload = { payload, _, _ ->
            calls++; prompts += payload.userPrompt
            AgentReport("ok", emptyList(), "synthetic")
        })
        owner.updateContext(VerificationFixtures.record().copy(title = "PRIVATE_TITLE"), "Level 0", AppLanguage.EN)
        val credentials = { reads++; key() }
        owner.send(credentials)
        assertEquals(0, reads)
        owner.preview()
        assertTrue(owner.state.preview)
        assertEquals(0, reads)
        assertFalse((owner.state.payload as VerificationPayloadResult.Ready).userPrompt.contains("PRIVATE_TITLE"))
        owner.dismissPreview(); owner.setIncludeNotes(true); owner.preview()
        assertTrue((owner.state.payload as VerificationPayloadResult.Ready).userPrompt.contains("PRIVATE_TITLE"))
        owner.send(credentials); owner.send(credentials)
        assertEquals(1, reads)
        work.run()
        assertEquals(1, calls)
        assertTrue(prompts.single().contains("PRIVATE_TITLE"))
    }

    // Catches a context change preserving an obsolete scope-confirmation dialog or queued transport.
    @Test fun changedContextRequiresFreshPreviewAndPreventsQueuedProviderCall() {
        val work = Queue(); var calls = 0; var reads = 0
        val owner = VerificationExplanationOwner(work, explainPayload = { _, _, _ ->
            calls++; AgentReport("bad", emptyList(), "synthetic")
        })
        val record = VerificationFixtures.record()
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        owner.preview(); assertTrue(owner.state.preview); owner.send(::key)
        owner.updateContext(record.copy(revision = 3), "Level 0", AppLanguage.EN)
        owner.send { reads++; key() }
        work.run()
        assertEquals(0, calls); assertEquals(0, reads)
        assertFalse(owner.state.preview); assertFalse(owner.state.request.loading)
    }

    // Catches credential exceptions escaping the owner or corrupting local evidence/context.
    @Test fun credentialReadFailureIsVisibleAndCanBeRetriedFromNewPreview() {
        var calls = 0
        val owner = VerificationExplanationOwner(Executor { it.run() }, explainPayload = { _, _, _ ->
            calls++; AgentReport("retry explanation", emptyList(), "synthetic")
        })
        owner.updateContext(VerificationFixtures.record(), "Level 0", AppLanguage.EN)
        owner.preview(); assertTrue(owner.state.preview); owner.send { throw AgentFailureException(AgentFailure.EXPIRED_KEY) }
        assertEquals(AgentFailure.EXPIRED_KEY, (owner.state.request.error as AgentFailureException).reason)
        assertEquals(0, calls)
        owner.preview(); owner.send(::key)
        assertEquals("retry explanation", owner.state.request.report?.summary)
    }

    private fun key() = StoredApiKey("DeepSeek", "synthetic-only", Long.MAX_VALUE)
    private class Queue : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun run() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
}
