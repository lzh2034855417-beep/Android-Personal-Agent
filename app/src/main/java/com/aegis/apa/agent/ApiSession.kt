package com.aegis.apa.agent

object ApiSession {
    @Volatile private var credential: StoredApiKey? = null
    val provider: String get() = credential?.provider.orEmpty()
    val apiKey: String get() = credential?.apiKey.orEmpty()

    @Synchronized fun update(value: StoredApiKey?) { credential = value }

    @Synchronized fun requireValid(now: Long = System.currentTimeMillis()): StoredApiKey {
        val value = credential ?: throw AgentFailureException(AgentFailure.MISSING_KEY)
        if (value.expiresAt <= now || value.apiKey.isBlank()) {
            credential = null
            throw AgentFailureException(AgentFailure.EXPIRED_KEY)
        }
        return value
    }
}
