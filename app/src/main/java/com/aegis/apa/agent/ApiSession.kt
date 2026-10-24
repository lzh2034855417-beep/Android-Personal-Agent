package com.aegis.apa.agent

object ApiSession {
    @Volatile private var credential: StoredApiKey? = null
    private var initialized = false
    @Volatile var configurationRevision: Long = 0L
        private set
    val provider: String get() = credential?.provider.orEmpty()
    val apiKey: String get() = credential?.apiKey.orEmpty()

    /** Stored credentials seed the process once; an explicit selection, including null, wins thereafter. */
    @Synchronized fun initialize(load: () -> StoredApiKey?) {
        if (!initialized) update(load())
    }

    @Synchronized fun update(value: StoredApiKey?) {
        initialized = true
        if (credential != value) { credential = value; configurationRevision++ }
    }

    @Synchronized fun requireValid(now: Long = System.currentTimeMillis()): StoredApiKey {
        val value = credential ?: throw AgentFailureException(AgentFailure.MISSING_KEY)
        if (value.expiresAt <= now || value.apiKey.isBlank()) {
            update(null)
            throw AgentFailureException(AgentFailure.EXPIRED_KEY)
        }
        return value
    }
}
