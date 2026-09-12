package com.aegis.apa.agent

object AgentMessageCopyPolicy {
    fun copyText(message: AgentConversationMessage): String? =
        message.content.takeIf {
            message.role == MessageRole.ASSISTANT && it.isNotBlank()
        }
}
