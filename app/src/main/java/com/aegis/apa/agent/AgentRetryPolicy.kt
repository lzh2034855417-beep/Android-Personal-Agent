package com.aegis.apa.agent

object AgentRetryPolicy {
    fun question(messages: List<AgentConversationMessage>): String? {
        if (messages.lastOrNull()?.role != MessageRole.ERROR) return null
        val index = messages.indexOfLast { it.role == MessageRole.USER }
        if (index < 0 || messages.drop(index + 1).any { it.role == MessageRole.ASSISTANT }) return null
        return messages[index].content
    }

    fun beforeRetry(messages: List<AgentConversationMessage>): List<AgentConversationMessage> =
        if (question(messages) == null) messages else messages.take(messages.indexOfLast { it.role == MessageRole.USER })
}
