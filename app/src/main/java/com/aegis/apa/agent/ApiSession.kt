package com.aegis.apa.agent

object ApiSession {
    @Volatile private var credential: StoredApiKey? = null
    val provider: String get() = credential?.provider.orEmpty()
    val apiKey: String get() = credential?.apiKey.orEmpty()

    @Synchronized fun update(value: StoredApiKey?) { credential = value }

    @Synchronized fun requireValid(now: Long = System.currentTimeMillis()): StoredApiKey {
        val value = checkNotNull(credential) { "请先在设置中保存 API Key" }
        if (value.expiresAt <= now || value.apiKey.isBlank()) {
            credential = null
            error("API Key 的本机保存期限已到，请在设置中重新保存")
        }
        return value
    }
}
