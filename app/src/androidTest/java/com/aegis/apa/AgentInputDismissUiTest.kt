package com.aegis.apa

import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgentInputDismissUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun outsideTapDismissesLongPressWithoutClearingDraft() {
        val menuOpen = AtomicBoolean(false)
        val state = AgentPageState()
        state.draft.value = "保留草稿"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var activity: ComponentActivity? = null
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity").close()
        rule.waitUntil(10_000) {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<ComponentActivity>().firstOrNull()
            }
            activity != null
        }
        instrumentation.runOnMainSync { activity!!.setContent {
            val nativeMenu = LocalTextContextMenuToolbarProvider.current
            val observedMenu = remember(nativeMenu) { nativeMenu?.let { delegate ->
                object : TextContextMenuProvider {
                    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                        menuOpen.set(true)
                        try { delegate.showTextContextMenu(dataProvider) }
                        finally { menuOpen.set(false) }
                    }
                }
            } }
            CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides observedMenu) {
            AndroidPersonalAgentTheme {
                AgentChatScreen(
                    state = state, messages = emptyList(),
                    onAnalyze = { _, _, _ -> }, isOnlineAnalyzing = false,
                    onOnlineAnalyze = { _, _, _, _, _, _ -> },
                    onPreflightMessage = { _, _, _ -> }, onLocalEvidenceMessage = { _, _, _ -> },
                    onClearConversation = {}, onOpenSettings = {},
                    powerDiagnosticState = PowerDiagnosticUiState.Idle, powerDiagnostic = null,
                    batteryObservationStart = null, batteryObservationResult = null,
                    batteryObservationNotice = null, rootAvailable = false,
                    onCollectPowerDiagnostic = {}, onImportBugReport = {},
                    onStartBatteryObservation = {}, onFinishBatteryObservation = {},
                    onClearBatteryObservation = {}, onRemovePowerDiagnostic = {},
                    onCopyPackage = {}, onCopyMessage = {}
                )
            }
        }
        }
        }
        try {
        val input = rule.onNode(hasSetTextAction())
        input.performClick()
        // The center of a wide editor is blank, not the short draft text.
        input.performTouchInput { longClick(Offset(width * 0.1f, center.y)) }
        org.junit.Assert.assertFalse(input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
        rule.waitUntil(5_000) { menuOpen.get() }
        input.assertIsFocused()
        rule.onNodeWithTag("agent_chat_header").performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(8f, 8f)) }
        // Closing the actual native menu must not also collapse the keyboard and resize the page.
        rule.waitUntil(5_000) { !menuOpen.get() }
        input.assertIsFocused()
        rule.runOnIdle { assertEquals("保留草稿", state.draft.value) }
        rule.onNodeWithTag("agent_chat_header").performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(8f, 8f)) }
        input.assertIsNotFocused()
        rule.runOnIdle { assertEquals("保留草稿", state.draft.value) }
        input.performClick()
        input.performTextInput("继续输入")
        rule.runOnIdle { org.junit.Assert.assertTrue(state.draft.value.contains("继续输入")) }
        // After the IME closes, Back exits editing instead of leaving the page.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        input.assertIsNotFocused()
        rule.onNodeWithTag("agent_chat_header").assertExists()
        rule.runOnIdle { org.junit.Assert.assertTrue(state.draft.value.contains("继续输入")) }
        } finally {
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }
}

