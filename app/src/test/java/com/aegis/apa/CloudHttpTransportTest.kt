package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

class CloudHttpTransportTest {
    private val credentials = StoredApiKey("DeepSeek", "fixture-key", Long.MAX_VALUE)
    private fun config(protocol: String) = CloudProviderConfig("Fixture", "F", "fixture-model", "https://fixture.invalid", protocol)

    private class Connection(val acquire: () -> Unit = {}, private val status: Int = 200) : HttpURLConnection(URL("https://fixture.invalid")) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        var errorBodyRead = false
        override fun disconnect() { disconnected = true }
        override fun connect() { }
        override fun usingProxy() = false
        override fun getOutputStream(): OutputStream { acquire(); return sent }
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream("{\"ok\":true}".toByteArray())
        override fun getErrorStream(): java.io.InputStream {
            errorBodyRead = true
            return ByteArrayInputStream("fixture-key".toByteArray())
        }
    }

    @Test fun cancellationDuringLazyAcquisitionPreventsBodyTransmission() {
        val cancellation = CloudRequestCancellation()
        val connection = Connection(acquire = { cancellation.cancel() })
        assertThrows(CancellationException::class.java) {
            CloudHttpTransport.post(config("openai"), credentials, "private report", cancellation) { connection }
        }
        assertEquals(0, connection.sent.size())
        assertTrue(connection.disconnected)
    }

    @Test fun protocolsUseTheirOwnAuthenticationAndCloseSuccessfulConnections() {
        for (protocol in listOf("openai", "anthropic")) {
            val connection = Connection()
            assertEquals("{\"ok\":true}", CloudHttpTransport.post(config(protocol), credentials, "{\"messages\":[]}", CloudRequestCancellation()) { connection })
            assertEquals("{\"messages\":[]}", connection.sent.toString("UTF-8"))
            assertEquals("POST", connection.requestMethod)
            if (protocol == "openai") {
                assertEquals("Bearer fixture-key", connection.getRequestProperty("Authorization"))
                assertNull(connection.getRequestProperty("x-api-key"))
            } else {
                assertEquals("fixture-key", connection.getRequestProperty("x-api-key"))
                assertNull(connection.getRequestProperty("Authorization"))
            }
            assertTrue(connection.disconnected)
        }
    }

    @Test fun httpFailureDoesNotReadOrEchoUntrustedErrorBody() {
        val connection = Connection(status = 401)
        val error = assertThrows(ModelHttpException::class.java) {
            CloudHttpTransport.post(config("openai"), credentials, "{}", CloudRequestCancellation()) { connection }
        }
        assertFalse(error.toString().contains("fixture-key"))
        assertFalse(connection.errorBodyRead)
        assertTrue(connection.disconnected)
    }
}
