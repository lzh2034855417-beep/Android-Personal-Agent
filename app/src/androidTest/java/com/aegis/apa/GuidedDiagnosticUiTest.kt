package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.GuidedDiagnosticController
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Compile here; execution requires an explicitly authorized device. No real Root/network adapter. */
class GuidedDiagnosticUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var controller: GuidedDiagnosticController
    private var routedActions = 0

    @Test fun guidedPowerFormPinsTypedTargetThroughPreviewAndSubmit() = verificationFormTarget(true)
    @Test fun originalPowerFormStillDefaultsToOverall() = verificationFormTarget(false)

    private fun verificationFormTarget(fixed: Boolean) {
        val now = Instant.parse("2026-10-01T10:00:00Z")
        val snapshot = PowerDiagnosticSnapshot(now, 10, mapOf("batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.AVAILABLE)),
            listOf(AppPowerEvidence(10123, listOf("com.example.app"), estimatedPowerMah = 10.0)),
            SystemPowerEvidence(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, 10.0, true)), emptyList())
        val target = VerificationTarget.AppGroup(listOf("com.example.app"))
        var submitted: VerificationTarget? = null
        rule.setContent { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) { AndroidPersonalAgentTheme {
            VerificationSampleForm(snapshot, null, true, {}, { value, _, _ -> submitted = value }, fixedTarget = if (fixed) target else null)
        } } }
        if (fixed) rule.onNodeWithText("Overall power").assertDoesNotExist() else {
            rule.onNodeWithTag("verification-form-list").performScrollToNode(hasText("Overall power"))
            rule.onNodeWithText("Overall power").assertExists()
        }
        rule.onNodeWithTag("verification-form-list").performScrollToNode(hasTestTag("verification-preview"))
        rule.onNodeWithTag("verification-preview").performClick()
        if (fixed) rule.onNodeWithText("This guide verifies power only, not heat.", substring = true).assertExists()
        rule.onNodeWithTag("verification-save-sample").performClick()
        rule.runOnIdle { assertEquals(if (fixed) target else VerificationTarget.Overall, submitted) }
    }

    @Test fun noKeyBothScenariosCompleteLocalReviewWithUnknownDeclarations() {
        show()
        rule.onNodeWithTag("guided-start-IDLE_DRAIN").performClick()
        scroll("guided-answer-charging-unknown"); rule.onNodeWithTag("guided-answer-charging-unknown").assertIsSelected()
        scroll("guided-answer-mostlyIdle-unknown"); rule.onNodeWithTag("guided-answer-mostlyIdle-unknown").assertIsSelected()
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").performClick()
        rule.runOnIdle { assertTrue(controller.state.session!!.answers.reviewed); assertNotNull(controller.state.assessment); assertEquals(0, routedActions) }
        scroll("guided-restart-USAGE_HEAT"); rule.onNodeWithTag("guided-restart-USAGE_HEAT").performClick()
        rule.runOnIdle { assertEquals(GuidedScenario.IDLE_DRAIN, controller.state.session!!.scenario) }
        rule.onNodeWithTag("guided-confirm-restart").performClick()
        scroll("guided-answer-stillHot-unknown"); rule.onNodeWithTag("guided-answer-stillHot-unknown").assertIsSelected()
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").performClick()
        rule.runOnIdle { assertNotNull(controller.state.assessment); assertEquals(0, routedActions) }
    }

    @Test fun malformedStartTimeRevokesReviewAndKeepsDraftUntilExplicitConfirmation() = malformedTimeEdit("guided-start-time")
    @Test fun malformedEndTimeRevokesReviewAndKeepsDraftUntilExplicitConfirmation() = malformedTimeEdit("guided-end-time")

    private fun malformedTimeEdit(tag: String) {
        show(GuidedScenario.IDLE_DRAIN)
        scroll(tag); rule.onNodeWithTag(tag).performTextReplacement("2026-10-01T10:00:00Z")
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").performClick()
        scroll("guided-primary-action"); rule.onNodeWithTag("guided-primary-action").performClick()
        lateinit var oldKey: GuidedSessionKey
        lateinit var preview: GuidedSamplePreview
        lateinit var work: GuidedWorkToken
        var cancelled = false
        rule.runOnIdle {
            assertTrue(controller.state.session!!.answers.reviewed)
            assertNotNull(controller.state.assessment)
            assertEquals(1, routedActions)
            controller.offerSample(sample("pending"))
            preview = controller.previewSample("pending")!!
            oldKey = controller.state.session!!.key
            work = controller.beginWork(oldKey) { cancelled = true }!!
        }
        val malformed = "2026-10-01T10:00:00"
        scroll(tag); rule.onNodeWithTag(tag).performTextReplacement(malformed)
        rule.runOnIdle {
            assertFalse(controller.state.session!!.answers.reviewed)
            assertNull(controller.state.assessment)
            assertNotEquals(oldKey, controller.state.session!!.key)
            assertTrue(cancelled)
            assertFalse(controller.markActionStarted(oldKey, "idle.open_observation"))
            assertFalse(controller.offerSample(work, sample("late")))
            assertFalse(controller.failWork(work, "late_error"))
            assertFalse(controller.confirmSample(preview))
            assertTrue(controller.state.session!!.samples.isEmpty())
        }
        rule.onNodeWithTag("guided-primary-action").assertDoesNotExist()
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").assertIsNotEnabled()
        scroll("guided-note"); rule.onNodeWithTag("guided-note").performTextInput("Keep the invalid time draft")
        scroll(tag); rule.onNodeWithTag(tag).assertTextContains(malformed)
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").assertIsNotEnabled()
        scroll(tag); rule.onNodeWithTag(tag).performTextClearance()
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").assertIsEnabled().performClick()
        rule.runOnIdle { assertTrue(controller.state.session!!.answers.reviewed); assertNotNull(controller.state.assessment) }
    }

    @Test fun returnedSamplesAreOffersAndReplacementRequiresSelectedSlotAndUncheckedPreview() {
        show(GuidedScenario.USAGE_HEAT)
        rule.runOnIdle { controller.updateAnswers(GuidedAnswers(reviewed = true)); controller.evaluateLocal(controller.state.session!!.key); controller.offerSample(sample("one")) }
        scroll("guided-preview-one"); rule.onNodeWithTag("guided-preview-one").performClick()
        rule.onNodeWithTag("guided-confirm-sample").assertIsNotEnabled()
        rule.onNodeWithTag("guided-preview-reviewed").assertIsOff().assertTextContains(
            "I reviewed and confirm this summary and declarations; replacement removes the selected old sample.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag("guided-preview-reviewed").performClick(); rule.onNodeWithTag("guided-confirm-sample").performClick()
        rule.runOnIdle { controller.offerSample(sample("two")) }
        scroll("guided-preview-two"); rule.onNodeWithTag("guided-preview-two").performClick()
        rule.onNodeWithTag("guided-preview-reviewed").performClick(); rule.onNodeWithTag("guided-confirm-sample").performClick()
        rule.runOnIdle { controller.offerSample(sample("three")); assertEquals(listOf("one", "two"), controller.state.session!!.samples.map { it.id }) }
        scroll("guided-preview-three"); rule.onNodeWithTag("guided-preview-three").performClick()
        rule.onNodeWithTag("guided-preview-reviewed").performClick(); rule.onNodeWithTag("guided-confirm-sample").assertIsNotEnabled()
        rule.onNodeWithTag("guided-replace-two").performClick(); rule.onNodeWithTag("guided-confirm-sample").performClick()
        rule.runOnIdle { assertEquals(listOf("one", "three"), controller.state.session!!.samples.map { it.id }) }
        scroll("guided-section-facts"); rule.onNodeWithText("Battery sensor temperature", substring = true).assertExists()
        scroll("guided-section-next"); rule.onNodeWithTag("guided-primary-action").assertExists()
        rule.runOnIdle { assertFalse(controller.state.assessment!!.primaryAction.kind == GuidedActionKind.OPEN_VERIFICATION) }
    }

    @Test fun answerOrCandidateChangeInvalidatesAnOpenPreviewAndChineseImeFormRemainsReachable() {
        show(GuidedScenario.USAGE_HEAT, AppLanguage.ZH_CN, compact = true)
        scroll("guided-note"); rule.onNodeWithTag("guided-note").performTextInput("历史体感与当前读数不同。".repeat(20))
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").performClick()
        rule.runOnIdle { controller.offerSample(sample("one")) }
        scroll("guided-preview-one"); rule.onNodeWithTag("guided-preview-one").performClick()
        rule.onNodeWithTag("guided-preview-reviewed").performClick()
        rule.runOnIdle { controller.updateAnswers(controller.state.session!!.answers.copy(charging = VerificationConfirmation.YES, reviewed = false)) }
        rule.onNodeWithTag("guided-confirm-sample").assertDoesNotExist()
        scroll("guided-preview-one"); rule.onNodeWithTag("guided-preview-one").performClick()
        rule.onNodeWithTag("guided-confirm-sample").assertIsNotEnabled()
        rule.runOnIdle { controller.offerSample(sample("two")) }
        rule.onNodeWithTag("guided-confirm-sample").assertDoesNotExist()
        scroll("guided-section-gaps"); rule.onNodeWithText("尚缺证据").assertExists()
    }

    @Test fun delayedSceneAndExitConfirmationsKeepTheirDisplayedFullKey() {
        show(GuidedScenario.IDLE_DRAIN)
        scroll("guided-restart-USAGE_HEAT"); rule.onNodeWithTag("guided-restart-USAGE_HEAT").performClick()
        rule.runOnIdle { controller.updateAnswers(GuidedAnswers(charging = VerificationConfirmation.YES)) }
        rule.onNodeWithTag("guided-confirm-restart").performClick()
        rule.runOnIdle { assertEquals(GuidedScenario.IDLE_DRAIN, controller.state.session!!.scenario) }
        scroll("guided-exit"); rule.onNodeWithTag("guided-exit").performClick()
        rule.runOnIdle { controller.updateAnswers(GuidedAnswers(charging = VerificationConfirmation.NO)) }
        rule.onNodeWithTag("guided-confirm-exit").performClick()
        rule.runOnIdle { assertNotEquals(GuidedStage.ENDED, controller.state.session!!.stage) }
        scroll("guided-exit"); rule.onNodeWithTag("guided-exit").performClick(); rule.onNodeWithTag("guided-confirm-exit").performClick()
        rule.runOnIdle { assertEquals(GuidedStage.ENDED, controller.state.session!!.stage) }
    }

    @Test fun collectionPauseRemountRetainsSessionAndResultAndRestartResetsDeclarations() {
        show(GuidedScenario.USAGE_HEAT)
        scroll("guided-confirm-answers"); rule.onNodeWithTag("guided-confirm-answers").performClick()
        rule.runOnIdle { controller.offerSample(sample("after-collection")) }
        scroll("guided-evidence-entries"); rule.onNodeWithTag("guided-evidence-entries").performClick()
        rule.onNodeWithTag("guided-resume").performClick()
        scroll("guided-preview-after-collection"); rule.onNodeWithTag("guided-preview-after-collection").performClick()
        rule.onNodeWithTag("guided-confirm-sample").assertIsNotEnabled()
        rule.onNodeWithTag("guided-preview-reviewed").performClick(); rule.onNodeWithTag("guided-confirm-sample").performClick()
        listOf("facts", "candidates", "gaps", "next").forEach { part -> rule.onNodeWithTag("guided-section-$part").assertExists() }
        scroll("guided-section-facts"); rule.onNodeWithText("Source: Basic battery snapshot", substring = true).assertExists()
        scroll("guided-restart-USAGE_HEAT"); rule.onNodeWithTag("guided-restart-USAGE_HEAT").performClick()
        rule.onNodeWithTag("guided-confirm-restart").performClick()
        rule.runOnIdle {
            assertTrue(controller.state.session!!.samples.isEmpty()); assertFalse(controller.state.session!!.answers.reviewed)
            assertEquals(VerificationConfirmation.UNKNOWN, controller.state.session!!.answers.charging)
            assertEquals(GuidedActivity.UNKNOWN, controller.state.session!!.answers.activity)
        }
    }

    private fun scroll(tag: String) = rule.onNodeWithTag(tag).performScrollTo()
    private fun show(scenario: GuidedScenario? = null, language: AppLanguage = AppLanguage.EN, compact: Boolean = false) {
        rule.setContent {
            var state by remember { mutableStateOf(GuidedUiState()) }
            var preview by remember { mutableStateOf<GuidedSamplePreview?>(null) }
            var paused by remember { mutableStateOf(false) }
            remember { GuidedDiagnosticController(onState = { state = it }).also { controller = it; scenario?.let(it::start) } }
            LaunchedEffect(state.session?.key, state.availableSampleId) { preview = null }
            CompositionLocalProvider(LocalAppLanguage provides language,
                LocalDensity provides Density(LocalDensity.current.density, if (compact) 1.5f else 1f)) {
                AndroidPersonalAgentTheme {
                    Box(if (compact) Modifier.width(320.dp).height(520.dp) else Modifier.fillMaxSize()) {
                        if (paused) androidx.compose.material3.Button(onClick = { paused = false }, modifier = Modifier.testTag("guided-resume")) {
                            androidx.compose.material3.Text("Return to guided diagnosis")
                        } else GuidedDiagnosticScreen(state) { event -> when (event) {
                            is GuidedUiEvent.Start -> controller.start(event.scenario)
                            is GuidedUiEvent.UpdateAnswers -> { controller.updateAnswers(event.key, event.answers); if (event.answers.reviewed) controller.evaluateLocal(controller.state.session!!.key) }
                            is GuidedUiEvent.PreviewSample -> if (state.session?.key == event.key) preview = controller.previewSample(event.sampleId)
                            is GuidedUiEvent.Restart -> controller.restart(event.key, event.scenario)
                            is GuidedUiEvent.Exit -> controller.end(event.key)
                            is GuidedUiEvent.RequestAction, is GuidedUiEvent.RetryAction, is GuidedUiEvent.RefreshHeatSnapshot ->
                                dispatchGuidedAction(controller, event) { routedActions++ }
                            is GuidedUiEvent.RequestExplanation -> Unit
                            is GuidedUiEvent.PauseForEvidence -> if (state.session?.key == event.key) paused = true
                        } }
                        preview?.let { token -> GuidedSamplePreviewForm(token, state.session!!.samples,
                            { preview = null }, { replacement -> controller.confirmSample(token, replacement); preview = null }) }
                    }
                }
            }
        }
    }
    private fun sample(id: String) = GuidedSample(id, listOf(GuidedEvidence("$id:temperature", GuidedEvidenceKind.BATTERY_TEMPERATURE,
        GuidedValue.Number(32.0, "celsius"), "battery", DiagnosticSourceStatus.AVAILABLE, EvidenceFieldStatus.PARSED,
        VerificationEstimate.ACTUAL, emptyList(), Instant.parse("2026-10-01T10:00:00Z"), null, null)), GuidedAnswers())
}
