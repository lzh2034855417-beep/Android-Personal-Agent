package com.aegis.apa.agent

object CloudHistoryPolicy {
    fun select(
        messages: List<AgentConversationMessage>,
        provider: String,
        freshDiagnostic: Boolean = false,
        adviceScope: CloudAdviceScope? = null
    ): List<AgentConversationMessage> {
        if (freshDiagnostic) return emptyList()
        return messages.filter {
            provider.isNotBlank() && it.cloudProvider == provider &&
                (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT)
        }.filter { message ->
            adviceScope == null || message.cloudAdviceScope == adviceScope
        }.takeLast(12)
    }
}
