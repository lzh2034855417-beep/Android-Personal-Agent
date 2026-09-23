package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test

class CloudHistoryPolicyTest {
    @Test fun localSceneAndLocalQuestionsNeverEnterCloudHistory() {
        val messages = listOf(
            AgentConversationMessage(MessageRole.USER, "local question"),
            AgentConversationMessage(MessageRole.ASSISTANT, "private Scene app data"),
            AgentConversationMessage(MessageRole.USER, "cloud question", cloudProvider = "DeepSeek")
        )
        assertEquals(listOf("cloud question"), CloudHistoryPolicy.select(messages, "DeepSeek").map { it.content })
    }

    @Test fun switchingProvidersDoesNotForwardAnotherProvidersHistory() {
        val messages = listOf(
            AgentConversationMessage(MessageRole.ASSISTANT, "old provider answer", cloudProvider = "DeepSeek"),
            AgentConversationMessage(MessageRole.USER, "new question", cloudProvider = "OpenAI · GPT"),
            AgentConversationMessage(MessageRole.ERROR, "transport error", cloudProvider = "OpenAI · GPT")
        )
        assertEquals(listOf("new question"), CloudHistoryPolicy.select(messages, "OpenAI · GPT").map { it.content })
    }

    @Test fun historyWindowIsAppliedAfterPrivacyFilter() {
        val cloud = (0..19).map { AgentConversationMessage(MessageRole.USER, "message $it", cloudProvider = "DeepSeek") }
        val local = List(15) { AgentConversationMessage(MessageRole.ASSISTANT, "local") }
        val result = CloudHistoryPolicy.select(cloud + local, "DeepSeek")
        assertEquals(12, result.size)
        assertEquals("message 8", result.first().content)
        assertEquals("message 19", result.last().content)
    }

    @Test fun freshDiagnosticDoesNotReuseEarlierAssistantConclusions() {
        val messages = listOf(
            AgentConversationMessage(MessageRole.USER, "之前为什么耗电", cloudProvider = "DeepSeek"),
            AgentConversationMessage(MessageRole.ASSISTANT, "之前证据不足", cloudProvider = "DeepSeek")
        )

        val result = CloudHistoryPolicy.select(
            messages = messages,
            provider = "DeepSeek",
            freshDiagnostic = true
        )
        assertTrue(result.isEmpty())
    }

    @Test fun noReportChatDoesNotReuseEarlierDeviceAdviceHistory() {
        val messages = listOf(
            AgentConversationMessage(
                MessageRole.USER, "给出 Root 建议", attachedReportLabel = "L2",
                cloudProvider = "DeepSeek", cloudAdviceScope = CloudAdviceScope.LEVEL_2
            ),
            AgentConversationMessage(
                MessageRole.ASSISTANT, "运行 su -c command", attachedReportLabel = "L2",
                cloudProvider = "DeepSeek", cloudAdviceScope = CloudAdviceScope.LEVEL_2
            ),
            AgentConversationMessage(
                MessageRole.USER, "聊聊术语", attachedReportLabel = "续航观察（本地）",
                cloudProvider = "DeepSeek", cloudAdviceScope = CloudAdviceScope.GENERAL
            ),
            AgentConversationMessage(
                MessageRole.ASSISTANT, "Root 是权限概念", attachedReportLabel = "续航观察（本地）",
                cloudProvider = "DeepSeek", cloudAdviceScope = CloudAdviceScope.GENERAL
            )
        )

        val result = CloudHistoryPolicy.select(
            messages = messages,
            provider = "DeepSeek",
            adviceScope = CloudAdviceScope.GENERAL
        )

        assertEquals(listOf("聊聊术语", "Root 是权限概念"), result.map { it.content })
    }

    @Test fun displayLabelCannotMoveGeneralChatIntoDeviceHistory() {
        val mislabeledGeneral = AgentConversationMessage(
            role = MessageRole.USER,
            content = "普通聊天",
            attachedReportLabel = "续航观察（本地）",
            cloudProvider = "DeepSeek",
            cloudAdviceScope = CloudAdviceScope.GENERAL
        )

        assertTrue(CloudHistoryPolicy.select(listOf(mislabeledGeneral), "DeepSeek", adviceScope = CloudAdviceScope.LEVEL_0).isEmpty())
        assertEquals(listOf(mislabeledGeneral), CloudHistoryPolicy.select(listOf(mislabeledGeneral), "DeepSeek", adviceScope = CloudAdviceScope.GENERAL))
    }

    @Test fun levelTwoHistoryCannotFlowIntoLevelZeroRequest() {
        val messages = listOf(
            AgentConversationMessage(
                MessageRole.ASSISTANT,
                "L2 advanced command",
                cloudProvider = "DeepSeek",
                cloudAdviceScope = CloudAdviceScope.LEVEL_2
            ),
            AgentConversationMessage(
                MessageRole.ASSISTANT,
                "L0 ordinary advice",
                cloudProvider = "DeepSeek",
                cloudAdviceScope = CloudAdviceScope.LEVEL_0
            )
        )

        val result = CloudHistoryPolicy.select(messages, "DeepSeek", adviceScope = CloudAdviceScope.LEVEL_0)

        assertEquals(listOf("L0 ordinary advice"), result.map { it.content })
    }
}
