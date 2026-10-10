package com.aegis.apa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.agent.*
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import javax.crypto.spec.SecretKeySpec

/** Synthetic archive only. These assertions require a separately authorized device run. */
class VerificationPanelUiTest {
    @get:Rule val rule = createComposeRule()
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private lateinit var controller: VerificationController
    private var drain by mutableDoubleStateOf(100.0)

    // Catches saving without a scope preview, implicit YES, missing rollback, or lost local results.
    @Test fun offlineBaselineChangeRetestAndDeleteRemainLocal() {
        show()
        rule.onNodeWithTag("verification-create").performClick()
        rule.onNodeWithText("Encrypted on this phone", substring = true).assertExists()
        form("verification-preview")
        rule.onNodeWithTag("verification-preview").performClick()
        assertTrue(rule.onAllNodesWithText("unknown", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("verification-save-sample").performClick()
        assertEquals(VerificationOwnership.UNKNOWN, controller.state.records.single().baseline.context.device.ownership)
        assertEquals(VerificationConfirmation.UNKNOWN, controller.state.records.single().baseline.context.noCharging)

        detail("verification-change")
        rule.onNodeWithTag("verification-change").performClick()
        rule.onNodeWithTag("verification-description").performTextInput("Disable sync")
        form("verification-rollback")
        rule.onNodeWithTag("verification-rollback").performTextInput("Enable sync again")
        form("verification-confirm-change")
        rule.onNodeWithTag("verification-confirm-change").performClick()
        assertEquals(now, controller.state.records.single().change!!.confirmedAt)
        assertEquals(VerificationConfirmation.UNKNOWN, controller.state.records.single().change!!.similarScenario)
        assertEquals(VerificationConfirmation.UNKNOWN, controller.state.records.single().change!!.noOtherChanges)

        detail("verification-retest")
        rule.onNodeWithTag("verification-retest").performClick()
        form("verification-preview")
        rule.onNodeWithTag("verification-preview").performClick()
        rule.onNodeWithTag("verification-save-sample").performClick()
        detail("verification-result-1")
        rule.onNodeWithText("Numeric comparison unavailable", substring = true).assertExists()
        detail("verification-rollback-detail")
        rule.onNodeWithText("Enable sync again", substring = true).assertExists()
        detail("verification-close-completed")
        rule.onNodeWithTag("verification-close-completed").performClick()
        assertEquals(VerificationState.CLOSED, controller.state.records.single().state)
        detail("verification-delete")
        rule.onNodeWithTag("verification-delete").performClick()
        assertEquals(1, controller.state.records.size)
        rule.onNodeWithTag("verification-confirm-destructive").performClick()
        assertTrue(controller.state.records.isEmpty())
    }

    // Catches editing a change while silently retaining a comparison against the old change.
    @Test fun editingChangeRequiresAcknowledgingRetestRemoval() {
        show(record = record())
        rule.runOnIdle { controller.select("synthetic") }
        detail("verification-change")
        rule.onNodeWithTag("verification-change").performClick()
        form("verification-confirm-change")
        rule.onNodeWithTag("verification-confirm-change").performClick()
        assertNotNull(controller.state.records.single().retest)
        rule.onNodeWithText("This removes the saved retest", substring = true).assertExists()
        rule.onNodeWithTag("verification-confirm-invalidate").performClick()
        assertNull(controller.state.records.single().retest)
    }

    // Catches treating user declarations as a replacement for missing system window evidence.
    @Test fun affirmativeDeclarationsStillCannotCalculateUnknownWindows() {
        show()
        rule.onNodeWithTag("verification-create").performClick()
        form("verification-ownership-yes")
        rule.onNodeWithTag("verification-ownership-yes").performClick()
        form("verification-charging-yes")
        rule.onNodeWithTag("verification-charging-yes").performClick()
        form("verification-preview")
        rule.onNodeWithTag("verification-preview").performClick()
        rule.onNodeWithTag("verification-save-sample").performClick()
        detail("verification-change")
        rule.onNodeWithTag("verification-change").performClick()
        rule.onNodeWithTag("verification-description").performTextInput("Disable sync")
        form("verification-rollback")
        rule.onNodeWithTag("verification-rollback").performTextInput("Enable sync")
        form("verification-similar-yes")
        rule.onNodeWithTag("verification-similar-yes").performClick()
        form("verification-other-yes")
        rule.onNodeWithTag("verification-other-yes").performClick()
        form("verification-confirm-change")
        rule.onNodeWithTag("verification-confirm-change").performClick()
        rule.runOnIdle { drain = 80.0 }
        detail("verification-retest")
        rule.onNodeWithTag("verification-retest").performClick()
        form("verification-ownership-yes")
        rule.onNodeWithTag("verification-ownership-yes").performClick()
        form("verification-charging-yes")
        rule.onNodeWithTag("verification-charging-yes").performClick()
        form("verification-preview")
        rule.onNodeWithTag("verification-preview").performClick()
        rule.onNodeWithTag("verification-save-sample").performClick()
        detail("verification-result-1")
        rule.onNodeWithText("Before raw value: 100 mAh", substring = true).assertExists()
        rule.onNodeWithText("Retest raw value: 80 mAh", substring = true).assertExists()
        rule.onNodeWithText("Numeric comparison unavailable", substring = true).assertExists()
        rule.onNodeWithText("Statistics window is unknown", substring = true).assertExists()
        assertNull(controller.state.records.single().baseline.metrics.single().window.startedAt)
        assertNull(controller.state.records.single().retest!!.metrics.single().window.endedAt)
    }

    // Catches overwriting a damaged archive on load or on dismissing the recovery confirmation.
    @Test fun damagedArchiveRequiresExplicitClearAuthorization() {
        var clears = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                AndroidPersonalAgentTheme {
                    PanelFixture(VerificationUiState(loading = false, storageFailure = VerificationStorageFailure.DAMAGED),
                        onClear = { clears++ })
                }
            }
        }
        rule.onNodeWithTag("verification-create").assertIsNotEnabled()
        rule.onNodeWithText("Archive damaged", substring = true).assertExists()
        rule.onNodeWithTag("verification-clear").performClick()
        assertEquals(0, clears)
        rule.onNodeWithTag("verification-cancel-destructive").performClick()
        assertEquals(0, clears)
        rule.onNodeWithTag("verification-clear").performClick()
        rule.onNodeWithTag("verification-confirm-destructive").performClick()
        assertEquals(1, clears)
    }

    // Catches presenting a rejected candidate as durable, and allows no duplicate writes while busy.
    @Test fun failedSaveIsExplicitAndSavingDisablesActions() {
        rule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                AndroidPersonalAgentTheme {
                    PanelFixture(VerificationUiState(loading = false, saving = true,
                        storageFailure = VerificationStorageFailure.WRITE_FAILED, unsavedRecord = record()))
                }
            }
        }
        rule.onNodeWithText("Saving; wait", substring = true).assertExists()
        rule.onNodeWithText("Not saved: current changes", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("verification-create").assertIsNotEnabled()
        rule.onNodeWithTag("verification-clear").assertIsNotEnabled()
    }

    // Catches losing a failed new baseline after Back, or labeling it as a durable saved row.
    @Test fun unsavedBaselineRemainsMarkedAndCanReopenAfterBack() {
        val candidate = record().copy(state = VerificationState.BASELINE_SAVED, change = null, retest = null,
            title = "Unsaved baseline")
        rule.setContent {
            var state by remember { mutableStateOf(VerificationUiState(loading = false, selectedId = candidate.id,
                storageFailure = VerificationStorageFailure.WRITE_FAILED, unsavedRecord = candidate)) }
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                AndroidPersonalAgentTheme {
                    VerificationPanel(state, diagnostic(), { _, _, _ -> },
                        { state = state.copy(selectedId = it) }, { _, _, _ -> }, { _, _, _ -> },
                        { _, _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> })
                }
            }
        }
        rule.onNodeWithText("Back to records").performClick()
        rule.onNodeWithTag("verification-unsaved-record").assertExists()
        rule.onNodeWithTag("verification-unsaved-record").assertTextContains("Not saved: Unsaved baseline")
        rule.onNodeWithText("Baseline saved").assertDoesNotExist()
        rule.onNodeWithTag("verification-unsaved-record").performClick()
        rule.onNodeWithText("Back to records").assertExists()
        rule.onNodeWithText("Not saved: current changes", substring = true).assertExists()
    }

    // Catches fixed-height/non-scrolling form/results hiding actions with large text and the IME.
    @Test fun smallScreenLargeFontLongResultsAndImeKeepActionsReachable() {
        show(record = record(), compact = true)
        rule.runOnIdle { controller.select("synthetic") }
        detail("verification-delete")
        rule.onNodeWithTag("verification-delete").assertIsDisplayed()
        detail("verification-change")
        rule.onNodeWithTag("verification-change").performClick()
        form("verification-side-effects")
        rule.onNodeWithTag("verification-side-effects").performTextInput("Notification delay. ".repeat(26))
        form("verification-confirm-change")
        rule.onNodeWithTag("verification-confirm-change").assertIsDisplayed()
    }

    private fun form(tag: String) = rule.onNodeWithTag("verification-form-list").performScrollToNode(hasTestTag(tag))
    private fun detail(tag: String) = rule.onNodeWithTag("verification-detail-list").performScrollToNode(hasTestTag(tag))

    // Catches CLOSED disabling all recovery actions and a retry starting a new transition.
    @Test fun failedClosedCandidateCanRetryInEnglish() {
        show(record = record())
        rule.runOnIdle {
            controller.select("synthetic")
            assertTrue(File(archiveFile.parentFile, archiveFile.name + ".pending").mkdir())
        }
        detail("verification-close-completed")
        rule.onNodeWithTag("verification-close-completed").performClick()
        rule.onNodeWithTag("verification-close-completed").assertIsNotEnabled()
        detail("verification-retry-save")
        rule.onNodeWithText("Retry save").assertExists()
        rule.runOnIdle { assertTrue(File(archiveFile.parentFile, archiveFile.name + ".pending").delete()) }
        rule.onNodeWithTag("verification-retry-save").performClick()
        rule.runOnIdle {
            assertNull(controller.state.unsavedRecord)
            assertEquals(VerificationState.CLOSED, controller.state.records.single().state)
        }
        rule.onNodeWithTag("verification-retry-save").assertDoesNotExist()
    }

    // Catches discard deleting the durable record and missing Chinese recovery copy.
    @Test fun failedClosedCandidateCanDiscardInChinese() {
        show(record = record(), language = AppLanguage.ZH_CN)
        rule.runOnIdle {
            controller.select("synthetic")
            assertTrue(File(archiveFile.parentFile, archiveFile.name + ".pending").mkdir())
        }
        detail("verification-close-completed")
        rule.onNodeWithTag("verification-close-completed").performClick()
        detail("verification-discard-unsaved")
        rule.onNodeWithText("放弃未保存修改").assertExists()
        rule.onNodeWithTag("verification-discard-unsaved").performClick()
        rule.runOnIdle {
            assertNull(controller.state.unsavedRecord)
            assertEquals(VerificationState.RETEST_SAVED, controller.state.records.single().state)
            assertTrue(File(archiveFile.parentFile, archiveFile.name + ".pending").delete())
        }
        rule.onNodeWithTag("verification-discard-unsaved").assertDoesNotExist()
    }

    private lateinit var archiveFile: File
    private fun show(record: VerificationRecord? = null, compact: Boolean = false, language: AppLanguage = AppLanguage.EN) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "verification-ui-${UUID.randomUUID()}.enc")
        archiveFile = file
        val store = VerificationStore(file, { SecretKeySpec(ByteArray(32) { 7 }, "AES") }, { now })
        val scopeId = store.load().scopeId!!
        record?.let { assertNull(store.upsert(it.copy(scopeId = scopeId), store.mutationToken()).failure) }
        rule.setContent {
            var state by remember { mutableStateOf(VerificationUiState()) }
            remember { VerificationController(store, { now }, Executor { it.run() }, { state = it }).also { controller = it } }
            DisposableEffect(Unit) { onDispose { controller.dispose(); file.delete() } }
            val actualWindow = LocalWindowInfo.current
            val density = LocalDensity.current.density
            val window = remember(actualWindow, density, compact) {
                if (compact) object : WindowInfo by actualWindow {
                    override val containerSize: IntSize get() = IntSize((320 * density).toInt(), actualWindow.containerSize.height)
                } else actualWindow
            }
            CompositionLocalProvider(LocalAppLanguage provides language, LocalWindowInfo provides window,
                LocalDensity provides Density(LocalDensity.current.density, if (compact) 1.5f else 1f)) {
                AndroidPersonalAgentTheme {
                    Box(if (compact) Modifier.width(320.dp).height(520.dp) else Modifier) {
                        VerificationPanel(state, diagnostic(),
                            onCreateBaseline = { target, contextInput, title ->
                                controller.createBaseline(title, target, verificationSummary(diagnostic(), target, contextInput)!!)
                            }, onSelect = controller::select, onChange = controller::recordChange,
                            onRetest = { id, revision, contextInput -> controller.recordRetest(id, revision,
                                verificationSummary(diagnostic(), controller.state.records.single().target, contextInput)!!) },
                            onClose = controller::close, onDelete = controller::delete, onClear = controller::clear,
                            onRetrySave = controller::retrySave, onDiscardUnsaved = controller::discardUnsaved)
                    }
                }
            }
        }
    }

    @Composable private fun PanelFixture(state: VerificationUiState, onClear: () -> Unit = {}) = VerificationPanel(
        state, diagnostic(), { _, _, _ -> }, {}, { _, _, _ -> }, { _, _, _ -> }, { _, _, _ -> }, {}, onClear,
        { _, _ -> }, { _, _ -> })

    private fun diagnostic() = PowerDiagnosticSnapshot(now.minusSeconds(100), 10,
        mapOf("batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.AVAILABLE)),
        emptyList(), SystemPowerEvidence(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, drain, true)),
        emptyList(), inputSource = DiagnosticInputSource.BUGREPORT,
        verificationMetadata = DiagnosticVerificationMetadata(null,
            VerificationDeviceContext("Example", "Synthetic model", 36, "test-build", VerificationOwnership.UNKNOWN), emptyMap()))

    private fun record(): VerificationRecord {
        val context = VerificationSampleContext(VerificationDeviceContext(null, null, null, null, VerificationOwnership.UNKNOWN),
            VerificationScenario.OTHER, "synthetic", VerificationConfirmation.UNKNOWN)
        val summary = VerificationSummaryProjector.project(diagnostic(), DiagnosticVerificationMetadata(null, null, emptyMap()),
            context, VerificationTarget.Overall)!!
        return VerificationRecord("synthetic", 1, 1, "replaced-by-test-scope", now.minusSeconds(60), now, 2,
            "Synthetic long result", VerificationState.RETEST_SAVED, VerificationTarget.Overall, summary,
            VerificationChange("Disable sync", "Enable sync again", now.minusSeconds(30), VerificationConfirmation.UNKNOWN,
                VerificationConfirmation.UNKNOWN, "Notification delay. ".repeat(26)), summary.copy(fingerprint = "retest"), null)
    }
}
