package com.aegis.apa

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.localization.withAppLanguage
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure Compose fixtures: no Activity session, disk history, provider, or Root execution. */
class AgentChatTimelineUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun newestThirtyAreReachableAndOlderHistoryRequiresExplicitLoading() {
        val state = AgentPageState()
        val messages = history()
        showChat(state, messages)

        rule.onNodeWithText(text(99)).assertIsDisplayed()
        rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText(text(70)))
        rule.onNodeWithText(text(70)).assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText(text(69)))
        }

        loadOlder()

        rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText(text(40)))
        rule.onNodeWithText(text(40)).assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText(text(39)))
        }
    }

    @Test fun retainedHistoryAppendKeepsReaderViewportUntilLatestIsRequested() {
        val state = AgentPageState()
        val messages = history()
        showChat(state, messages)
        loadOlder()
        rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText(text(50)))
        rule.onNodeWithText(text(50)).assertIsDisplayed()
        val before = rule.onNodeWithText(text(50)).fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { assertFalse(state.followLatest) }

        // Mirror the bounded session update while keeping identities of retained messages.
        rule.runOnIdle {
            messages.removeAt(0)
            messages.add(AgentConversationMessage(MessageRole.USER, text(100)))
        }

        rule.onNodeWithText(text(50)).assertIsDisplayed()
        val after = rule.onNodeWithText(text(50)).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.left, after.left, 1f)
        rule.onNodeWithText("Latest messages").performClick()

        rule.onNodeWithText(text(100)).assertIsDisplayed()
        rule.runOnIdle { assertTrue(state.followLatest) }
    }

    @Test fun longAssistantReplyCopiesCompleteTextFromVirtualizedTimeline() {
        val reply = (1..120).joinToString("\n") { "Evidence paragraph $it: keep the original details." }
        val messages = history().apply {
            removeAt(0)
            add(AgentConversationMessage(MessageRole.ASSISTANT, reply, source = "Fixture"))
        }
        var copied: String? = null
        showChat(AgentPageState(), messages) { copied = it }

        rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText("Copy full text"))
        rule.onNodeWithText("Copy full text").performClick()

        assertEquals(reply, copied)
    }

    private fun loadOlder() {
        rule.onNodeWithTag("chat_timeline").performScrollToNode(hasText("Load older messages"))
        rule.onNodeWithText("Load older messages").performClick()
    }

    private fun history(): SnapshotStateList<AgentConversationMessage> = mutableStateListOf<AgentConversationMessage>().apply {
        addAll((0 until 100).map { AgentConversationMessage(MessageRole.USER, text(it)) })
    }

    private fun text(index: Int) = "History ${index.toString().padStart(3, '0')}"

    private fun showChat(
        state: AgentPageState,
        messages: SnapshotStateList<AgentConversationMessage>,
        onCopy: (String) -> Unit = {}
    ) {
        rule.setContent {
            val originalContext = LocalContext.current
            val englishContext = remember(originalContext) { originalContext.withAppLanguage(AppLanguage.EN) }
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides englishContext.resources.configuration,
                LocalAppLanguage provides AppLanguage.EN
            ) {
                AndroidPersonalAgentTheme {
                    AgentChatScreen(
                        state = state, messages = messages,
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
                        onCopyPackage = {}, onCopyMessage = onCopy
                    )
                }
            }
        }
    }
}
