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
}
