package com.aegis.apa

import com.aegis.apa.agent.ApiSession
import com.aegis.apa.agent.StoredApiKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ApiSessionTest {
    @After fun clearSession() { ApiSession.update(null) }

    @Test fun rejectsKeyAtExactExpirationEvenIfAppStayedOpen() {
        ApiSession.update(StoredApiKey("DeepSeek", "test-key", 100))
        assertEquals("test-key", ApiSession.requireValid(99).apiKey)
        assertThrows(IllegalStateException::class.java) { ApiSession.requireValid(100) }
        assertEquals("", ApiSession.apiKey)
    }

    @Test fun capturedCredentialDoesNotChangeWhenProviderChanges() {
        ApiSession.update(StoredApiKey("DeepSeek", "first", 100))
        val request = ApiSession.requireValid(50)
        ApiSession.update(StoredApiKey("OpenAI · GPT", "second", 100))
        assertEquals("DeepSeek", request.provider)
        assertEquals("first", request.apiKey)
    }
}
