package com.aegis.apa

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import com.aegis.apa.model.BatteryObservationAnalyzer
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class BatteryObservationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bugReportIsPrimaryAndObservationIsCollapsedByDefault() {
        rule.setContent { ObservationPanel() }

        rule.onNodeWithText("导入系统报告").assertExists()
        rule.onNodeWithText("辅助：粗略续航测量").assertExists()
        rule.onAllNodesWithText("开始粗略测量").assertCountEquals(0)

        rule.onNodeWithText("辅助：粗略续航测量").performClick()

        rule.onNodeWithText("开始粗略测量").assertExists()
    }

    @Test fun finishingRequiresExplicitChargingConfirmation() {
        val start = BatteryObservationPoint(Instant.parse("2026-09-14T00:00:00Z"), 80, charging = false)
        var userReportedCharging: Boolean? = null
        rule.setContent {
            ObservationPanel(
                observationStart = start,
                onFinish = { userReportedCharging = it }
            )
        }

        rule.onNodeWithText("结束观察并计算").performClick()
        rule.onNodeWithText("期间是否充过电？").assertExists()
        rule.onNodeWithText("充过电或不确定，本次作废").performClick()

        assertEquals(true, userReportedCharging)
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
    private fun ObservationPanel(
        result: com.aegis.apa.model.BatteryObservationResult? = null,
        observationStart: BatteryObservationPoint? = null,
        onFinish: (Boolean) -> Unit = {}
    ) {
        AndroidPersonalAgentTheme {
            PowerDiagnosticPanel(
                state = PowerDiagnosticUiState.Idle,
                snapshot = null,
                observationStart = observationStart,
                observationResult = result,
                observationNotice = null,
                selected = false,
                rootAvailable = false,
                onCollect = {},
                onImportBugReport = {},
                onStartObservation = {},
                onFinishObservation = onFinish,
                onClearObservation = {},
                onToggleSelected = {},
                onRemove = {},
                onCopyPackage = {}
            )
        }
    }
}
