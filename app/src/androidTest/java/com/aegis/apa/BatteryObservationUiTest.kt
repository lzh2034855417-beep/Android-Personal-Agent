package com.aegis.apa

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aegis.apa.model.BatteryObservationAnalyzer
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class BatteryObservationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun ordinaryUserCanSeeTheStartEntryPoint() {
        rule.setContent { ObservationPanel() }

        rule.onNodeWithText("普通用户续航观察").assertExists()
        rule.onNodeWithText("开始续航观察").assertExists()
    }

    @Test fun completedObservationShowsTheMeasuredRate() {
        val start = Instant.parse("2026-09-14T00:00:00Z")
        val result = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 80, charging = false),
            BatteryObservationPoint(start.plusSeconds(2 * 60 * 60), 74, charging = false)
        )
        rule.setContent { ObservationPanel(result = result) }

        rule.onNodeWithText("平均 3.00%/小时", substring = true).assertExists()
        rule.onNodeWithText("重新开始").assertExists()
        rule.onNodeWithText("清除").assertExists()
    }

    @Test fun importHelpExplainsBothXiaomiAndStandardAndroidPaths() {
        rule.setContent { ObservationPanel() }

        rule.onNodeWithText("怎么生成系统报告").performClick()

        rule.onNodeWithText("*#*#284#*#*", substring = true).assertExists()
        rule.onNodeWithText("开发者选项", substring = true).assertExists()
        rule.onNodeWithText("敏感内容", substring = true).assertExists()
    }

    @Composable
    private fun ObservationPanel(result: com.aegis.apa.model.BatteryObservationResult? = null) {
        AndroidPersonalAgentTheme {
            PowerDiagnosticPanel(
                state = PowerDiagnosticUiState.Idle,
                snapshot = null,
                observationStart = null,
                observationResult = result,
                observationNotice = null,
                selected = false,
                rootAvailable = false,
                onCollect = {},
                onImportBugReport = {},
                onStartObservation = {},
                onFinishObservation = {},
                onClearObservation = {},
                onToggleSelected = {},
                onRemove = {},
                onCopyPackage = {}
            )
        }
    }
}
