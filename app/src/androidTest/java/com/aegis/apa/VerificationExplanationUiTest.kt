package com.aegis.apa

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aegis.apa.agent.*
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executor

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

    private fun show(credentials: () -> StoredApiKey?,
        explain: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport) {
        rule.setContent { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) { AndroidPersonalAgentTheme {
            VerificationExplanationPanel(record(), "Level 0", credentials, explainPayload = explain,
                executor = Executor { it.run() }, publishExecutor = Executor { it.run() })
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
