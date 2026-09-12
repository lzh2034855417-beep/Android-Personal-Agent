package com.aegis.apa

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgentMessageCopyUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun copyButtonCopiesOnlyTheCompleteAssistantReply() {
        var copiedText: String? = null
        val reply = "第一嫌疑：抖音\n依据：唤醒 180 次\n建议：限制后台活动"

        rule.setContent {
            AndroidPersonalAgentTheme {
                AgentChatScreen(
                    state = AgentPageState(),
                    messages = listOf(
                        AgentConversationMessage(MessageRole.USER, "为什么耗电"),
                        AgentConversationMessage(MessageRole.ASSISTANT, reply, source = "DS")
                    ),
                    onAnalyze = { _, _, _ -> },
                    isOnlineAnalyzing = false,
                    onOnlineAnalyze = { _, _, _, _, _, _ -> },
                    onClearConversation = {},
                    onOpenSettings = {},
                    powerDiagnosticState = PowerDiagnosticUiState.Idle,
                    powerDiagnostic = null,
                    onCollectPowerDiagnostic = {},
                    onRemovePowerDiagnostic = {},
                    onCopyPackage = {},
                    onCopyMessage = { copiedText = it }
                )
            }
        }

        rule.onAllNodesWithText("复制全文").assertCountEquals(1)
        rule.onNodeWithText("复制全文").performClick()

        assertEquals(reply, copiedText)
    }
}
