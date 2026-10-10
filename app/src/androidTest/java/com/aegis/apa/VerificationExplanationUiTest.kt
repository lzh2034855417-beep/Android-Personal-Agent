package com.aegis.apa

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.*
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic callbacks only; compilation does not establish device UI behavior. */
class VerificationExplanationUiTest {
    @get:Rule val rule = createComposeRule()

    // Catches credential access/post at render or preview and implicit sharing of notes.
    @Test fun explicitScopePreviewPrecedesSendAndNotesDefaultOff() {
        var reads = 0; var posts = 0
        show(credentials = { reads++; StoredApiKey("DeepSeek", "fake", Long.MAX_VALUE) }, explain = { p, _, _ ->
            posts++; assertFalse(p.userPrompt.contains("SECRET_NOTE")); AgentReport("Synthetic explanation", emptyList(), "fake")
        })
        assertEquals(0, reads); assertEquals(0, posts)
        rule.onNodeWithTag("verification-explain-preview").performClick()
        assertEquals(0, reads); assertEquals(0, posts)
        rule.onNodeWithText("UNTRUSTED_VERIFICATION_DATA", substring = true).assertExists()
        rule.onNodeWithTag("verification-explain-send").performClick()
        assertEquals(1, reads); assertEquals(1, posts)
        rule.onNodeWithText("Synthetic explanation").assertExists()
        rule.onNodeWithText("cannot replace the local gate", substring = true).assertExists()
        rule.onNodeWithText("missing_retest", substring = true).assertExists()
    }

    // Catches loss of local results or transport use after credentials cannot be read.
    @Test fun credentialReadFailureKeepsLocalGateAndDoesNotPost() {
        var posts = 0
        show(credentials = { throw AgentFailureException(AgentFailure.EXPIRED_KEY) }, explain = { _, _, _ ->
            posts++; AgentReport("should never appear", emptyList(), "fake")
        })
        rule.onNodeWithTag("verification-explain-preview").performClick()
        rule.onNodeWithTag("verification-explain-send").performClick()
        assertEquals(0, posts)
        rule.onNodeWithText("expired", substring = true).assertExists()
        rule.onNodeWithText("missing_retest", substring = true).assertExists()
    }

    // Catches optional notes not appearing in the reviewed scope or a missing key reaching provider.
    @Test fun optInNotesArePreviewedButNoKeyNeverPosts() {
        var posts = 0
        show(credentials = { null }, explain = { _, _, _ -> posts++; AgentReport("bad", emptyList(), "fake") })
        rule.onNodeWithTag("verification-explain-notes").performClick()
        rule.onNodeWithTag("verification-explain-preview").performClick()
        rule.onNodeWithText("SECRET_NOTE", substring = true).assertExists()
        rule.onNodeWithTag("verification-explain-send").performClick()
        assertEquals(0, posts)
        rule.onNodeWithText("Save an API Key", substring = true).assertExists()
    }

    // Catches result ownership returning to the disposed lazy item, or automatically posting on re-entry.
    @Test fun scrollAwayRetainsExplanation() {
        var calls = 0
        showDetail(explain = { _, _, _ -> calls++; AgentReport("Retained after scroll", emptyList(), "synthetic") })
        requestFromDetail()
        detail("verification-explain-result").assertTextEquals("Retained after scroll")
        rule.onNodeWithTag("verification-detail-list").performScrollToIndex(0)
        rule.onNodeWithTag("verification-explain-result").assertDoesNotExist()
        detail("verification-explain-result").assertTextEquals("Retained after scroll")
        assertEquals(1, calls)
    }

    // Catches scrolling cancelling an active transport, dropping its reply, or resending on re-entry.
    @Test fun scrollAwayDoesNotCancelInFlightRequest() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val cancelled = AtomicBoolean(false); val calls = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        try {
            showDetail(worker, Executor { main.post(it) }) { _, _, cancellation ->
                calls.incrementAndGet()
                cancellation.register { cancelled.set(true) }
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Synthetic response not released" }
                cancellation.ensureActive()
                AgentReport("Completed while off screen", emptyList(), "synthetic")
            }
            requestFromDetail()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            rule.onNodeWithTag("verification-detail-list").performScrollToIndex(0)
            rule.onNodeWithTag("verification-explain-cancel").assertDoesNotExist()
            assertFalse(cancelled.get())
            release.countDown()
            rule.waitUntil(5_000) { explanation.value.request.report != null }
            detail("verification-explain-result").assertTextEquals("Completed while off screen")
            assertEquals(1, calls.get())
            assertFalse(cancelled.get())
        } finally { release.countDown(); worker.shutdownNow() }
    }

    // Catches re-creating the detail composition (as during rotation) invalidating the page owner.
    @Test fun detailCompositionRecreationRetainsExplanation() {
        var calls = 0
        showDetail(explain = { _, _, _ -> calls++; AgentReport("Retained after recreation", emptyList(), "synthetic") })
        requestFromDetail()
        rule.runOnIdle { detailVisible.value = false }
        rule.onNodeWithTag("verification-detail-list").assertDoesNotExist()
        rule.runOnIdle { detailVisible.value = true }
        detail("verification-explain-result").assertTextEquals("Retained after recreation")
        assertEquals(1, calls)
    }

    private val explanation = mutableStateOf(VerificationExplanationUiState())
    private val detailVisible = mutableStateOf(true)
    private lateinit var owner: VerificationExplanationOwner

    private fun requestFromDetail() {
        detail("verification-explain-preview").performClick()
        rule.onNodeWithTag("verification-explain-send").performClick()
    }

    private fun detail(tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("verification-detail-list").performScrollToNode(hasTestTag(tag))
        return rule.onNodeWithTag(tag).performScrollTo()
    }

    private fun showDetail(executor: Executor = Executor { it.run() },
        publishExecutor: Executor = Executor { it.run() },
        explain: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport) {
        val record = record()
        owner = VerificationExplanationOwner(executor, publishExecutor, { explanation.value = it }, explain)
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        rule.setContent { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) { AndroidPersonalAgentTheme {
            if (detailVisible.value) Box(Modifier.height(300.dp)) {
                VerificationRecordDetail(record, null, true, { _, _, _ -> }, { _, _, _ -> }, { _, _, _ -> }, {},
                    false, true, { _, _ -> }, { _, _ -> }, explanation = explanation.value,
                    onExplanationPreview = owner::preview, onExplanationDismiss = owner::dismissPreview,
                    onExplanationNotes = owner::setIncludeNotes,
                    onExplanationSend = { owner.send { StoredApiKey("DeepSeek", "synthetic-only", Long.MAX_VALUE) } },
                    onExplanationStop = owner::stop)
            }
        } } }
    }

    private fun show(credentials: () -> StoredApiKey?,
        explain: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport) {
        val record = record()
        owner = VerificationExplanationOwner(Executor { it.run() }, onState = { explanation.value = it }, explainPayload = explain)
        owner.updateContext(record, "Level 0", AppLanguage.EN)
        rule.setContent { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) { AndroidPersonalAgentTheme {
            VerificationExplanationPanel(record, explanation.value, owner::preview, owner::dismissPreview,
                owner::setIncludeNotes, { owner.send(credentials) }, owner::stop)
        } } }
    }
    private fun record(): VerificationRecord {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val context = VerificationSampleContext(VerificationDeviceContext(null, null, null, null, VerificationOwnership.UNKNOWN),
            VerificationScenario.OTHER, "SECRET_NOTE", VerificationConfirmation.UNKNOWN)
        val summary = DiagnosticVerificationSummary(DiagnosticInputSource.BUGREPORT, now, null, context, emptyMap(), emptyList(), "fake")
        return VerificationRecord("fake", 1, 1, "local", now, now, 0, "SECRET_NOTE", VerificationState.BASELINE_SAVED,
            VerificationTarget.Overall, summary, null, null, null)
    }
}
