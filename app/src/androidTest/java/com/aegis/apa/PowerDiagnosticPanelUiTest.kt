package com.aegis.apa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.model.*
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PowerDiagnosticPanelUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun englishShowsStructuredConclusionAndKeepsPackageCopyComplete() {
        var copied: String? = null
        showPanel(AppLanguage.EN) { copied = it }

        rule.onNodeWithText("Conclusion").assertExists()
        rule.onNodeWithText("Total consumption").assertExists()
        rule.onNodeWithText("Background anomaly suspects").assertExists()
        rule.onNodeWithText("Next step").assertExists()
        rule.onNodeWithText("Copy all package names").performScrollTo().performClick()

        assertEquals("com.example.one\ncom.example.two", copied)
    }

    @Test fun chineseShowsSourceLimitsAndPreservesUnknownEvidence() {
        showPanel(AppLanguage.ZH_CN) {}

        rule.onNodeWithText("本地结论").assertExists()
        assertTrue(rule.onAllNodesWithText("battery：超时", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("原始设备证据不可替换", substring = true).assertExists()
    }

    @Test fun additionalEvidenceKeepsWindowValuesAndDisclosesEstimatedDrain() {
        showPanel(AppLanguage.EN) {}
        rule.onAllNodesWithText("Window duration:", substring = true).assertCountEquals(0)

        rule.onNodeWithText("Show additional evidence").performScrollTo().performClick()

        rule.onNodeWithText("Window duration: 120 min").assertExists()
        rule.onNodeWithText("Window drain: 800.0 mAh · Capacity: 4000.0 mAh").assertExists()
        rule.onNodeWithText("Drain is a system estimate.").assertExists()
    }

    private fun showPanel(language: AppLanguage, onCopy: (String) -> Unit) {
        val candidate = RankedPowerCandidate(
            uid = 10123, packageNames = listOf("com.example.one", "com.example.two"),
            facts = listOf("原始设备证据不可替换"), confidence = DiagnosticConfidence.LOW,
            maxAdviceLevel = AdviceLevel.OBSERVE, reason = "共享 UID 原始限制"
        )
        val snapshot = PowerDiagnosticSnapshot(
            sampledAtInstant = Instant.parse("2026-10-02T01:00:00Z"), collectionDurationMillis = 100,
            sources = mapOf("battery" to DiagnosticSourceResult("battery", DiagnosticSourceStatus.TIMED_OUT)),
            apps = emptyList(), system = SystemPowerEvidence(
                batteryDrainWindow = BatteryDrainWindowEvidence(7_200_000, 4000.0, 800.0, false)
            ), findings = emptyList(),
            localVerdict = LocalPowerVerdict(PowerVerdictType.SUFFICIENT, listOf(candidate), emptyList(), null, emptyList())
        )
        rule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                AndroidPersonalAgentTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        PowerDiagnosticPanel(
                            state = PowerDiagnosticUiState.Ready, snapshot = snapshot,
                            observationStart = null, observationResult = null, observationNotice = null,
                            selected = false, rootAvailable = false, onCollect = {}, onImportBugReport = {},
                            onStartObservation = {}, onFinishObservation = {}, onClearObservation = {},
                            onToggleSelected = {}, onRemove = {}, onCopyPackage = onCopy
                        )
                    }
                }
            }
        }
    }
}
