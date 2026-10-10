package com.aegis.apa

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.*
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

/** Mock provider only. Compilation is not device lifecycle or model compliance evidence. */
class GuidedExplanationUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var owner: GuidedDiagnosticViewModel
    private var mounted by mutableStateOf(true)
    private var reads = 0
    private var keyless = false
    private val worker = Queue()
    private class Queue : Executor {
        val tasks = java.util.ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private fun show(language: AppLanguage = AppLanguage.EN) {
        owner = GuidedDiagnosticViewModel()
        owner.configureExplanation({ reads++; if (keyless) null else StoredApiKey("fixture", "secret", Long.MAX_VALUE) },
            { "fixture:1" }, worker, Executor { it.run() },
            { _, _, _ -> GuidedExplanationResult.Accepted(GuidedExplanationText(emptyList(), emptyList(), emptyList(), "Fixture optional explanation")) })
        owner.updateExplanationLanguage(language)
        owner.start(GuidedScenario.USAGE_HEAT)
        owner.updateAnswers(GuidedAnswers(reviewed = true))
        owner.evaluateLocal(owner.state.value.session!!.key)
        rule.setContent { CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f, 1.5f)) {
            AndroidPersonalAgentTheme { if (mounted) androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
                GuidedDiagnosticScreen(owner.state.value, owner.explanationState.value,
                    owner::setExplanationOptions, owner::dismissExplanationPreview, owner::sendExplanation, owner::cancelExplanation) { event ->
                    when (event) {
                        is GuidedUiEvent.RequestExplanation -> owner.previewExplanation(event.key)
                        is GuidedUiEvent.UpdateAnswers -> owner.updateAnswers(event.key, event.answers)
                        else -> Unit
                    }
                }
            } }
        } }
    }
    private fun scroll(tag: String) { rule.onNodeWithTag("guided-list").performScrollToNode(hasTestTag(tag)) }
    private fun preview() { scroll("guided-explanation-preview"); rule.onNodeWithTag("guided-explanation-preview").performClick() }
    private fun send() { rule.onNodeWithTag("guided-explanation-send").performClick() }

    @Test fun explicitPreviewDefaultsNotesOffAndShowsExactBytesBeforeCredentials() {
        show(); scroll("guided-explanation-notes"); rule.onNodeWithTag("guided-explanation-notes").assertIsOff()
        preview(); rule.onNodeWithTag("guided-explanation-payload").assertExists()
        rule.onNodeWithText("UTF-8 prompt bytes:", substring = true).assertExists()
        rule.runOnIdle { assertEquals(0, reads); assertTrue(owner.explanationState.value.previewVisible) }
        send(); rule.runOnIdle { assertEquals(1, reads); worker.drain() }
        rule.onNodeWithText("Fixture optional explanation").assertExists()
    }

    @Test fun scrollingAndPauseRemountRetainResultInSamePageOwner() {
        show(); preview(); send(); rule.runOnIdle { worker.drain() }
        scroll("guided-section-facts"); scroll("guided-explanation-result")
        rule.onNodeWithText("Fixture optional explanation").assertExists()
        rule.runOnIdle { mounted = false }; rule.runOnIdle { mounted = true }
        scroll("guided-explanation-result"); rule.onNodeWithText("Fixture optional explanation").assertExists()
    }

    @Test fun keylessErrorKeepsAllLocalSectionsAndManualRetry() {
        keyless = true; show(); preview(); send()
        rule.onNodeWithTag("guided-explanation-error").assertExists()
        for (tag in listOf("facts", "candidates", "gaps", "next")) { scroll("guided-section-$tag"); rule.onNodeWithTag("guided-section-$tag").assertExists() }
        rule.runOnIdle { keyless = false }; preview(); send(); rule.runOnIdle { worker.drain() }
        rule.onNodeWithText("Fixture optional explanation").assertExists()
    }

    @Test fun explicitStopRejectsQueuedRequestAndRetryNeedsNewPreview() {
        show(); preview(); send(); scroll("guided-explanation-stop"); rule.onNodeWithTag("guided-explanation-stop").performClick()
        rule.runOnIdle { worker.drain(); assertNull(owner.explanationState.value.text); assertEquals(1, reads) }
        rule.onNodeWithText("the remote service may have received", substring = true).assertExists()
        preview(); send(); rule.runOnIdle { worker.drain(); assertEquals(2, reads) }
        rule.onNodeWithText("Fixture optional explanation").assertExists()
    }

    @Test fun answerAndSceneChangesClearCompletedTextAndFreshOwnerIsEmpty() {
        show(AppLanguage.ZH_CN); preview(); send(); rule.runOnIdle { worker.drain(); assertNotNull(owner.explanationState.value.text)
            owner.updateAnswers(GuidedAnswers(reviewed = false)); assertNull(owner.explanationState.value.text)
            owner.restart(GuidedScenario.IDLE_DRAIN); assertNull(owner.explanationState.value.payload)
            owner.end(); assertNull(owner.explanationState.value.text)
            assertNull(GuidedDiagnosticViewModel().state.value.session) }
    }

    @Test fun oversizedPayloadDisablesSendAndShowsFixedReasonWithoutReadingKey() {
        show()
        rule.runOnIdle {
            val answers = owner.state.value.session!!.answers
            val temperature = GuidedEvidence("sample.temperature", GuidedEvidenceKind.BATTERY_TEMPERATURE,
                GuidedValue.Number(30.0, "celsius"), "battery", DiagnosticSourceStatus.AVAILABLE,
                EvidenceFieldStatus.PARSED, VerificationEstimate.ACTUAL, emptyList(), java.time.Instant.parse("2026-10-05T00:00:00Z"), null, null)
            owner.offerSample(GuidedSample("sample", List(100) { temperature.copy(id = "sample.temperature.$it") }, answers))
            assertTrue(owner.confirmSample(owner.previewSample("sample")!!))
        }
        preview(); rule.onNodeWithTag("guided-explanation-send").assertIsNotEnabled()
        rule.onNodeWithTag("guided-explanation-preview-error").assertExists()
        rule.runOnIdle { assertEquals(0, reads); assertEquals(100, owner.state.value.assessment!!.facts.size) }
    }
}
