package com.aegis.apa.agent

object CloudHistoryPolicy {
    fun select(messages: List<AgentConversationMessage>, provider: String): List<AgentConversationMessage> =
        messages.filter {
            provider.isNotBlank() && it.cloudProvider == provider &&
                (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT)
        }.takeLast(12)
}
