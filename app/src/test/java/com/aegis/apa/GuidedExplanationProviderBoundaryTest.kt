package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Early boundaries run on JVM; no Android JSON method is called or mocked by these fixtures. */
class GuidedExplanationProviderBoundaryTest {
    private val payload = GuidedExplanationPayloadTest.payload()
    private fun key(provider: String = "DeepSeek") = StoredApiKey(provider, "SYNTHETIC_SECRET", Long.MAX_VALUE)
    private fun reason(result: GuidedExplanationResult) = (result as GuidedExplanationResult.Rejected).reasonId

    // Missing preflight/cancellation/expiry checks would dispatch a request lacking authorization.
    @Test fun unavailableKeysUnsupportedProtocolAndCancellationNeverTransmit() {
        var posts = 0
        val provider = GuidedExplanationProvider(post = { _, _, _, _ -> posts++; error("Not authorized") })
        assertEquals("guided.ai.missing_key", reason(provider.explain(payload, key().copy(apiKey = ""), CloudRequestCancellation())))
        assertEquals("guided.ai.expired_key", reason(provider.explain(payload, key().copy(expiresAt = 0), CloudRequestCancellation())))
        assertEquals("guided.ai.unknown_provider", reason(provider.explain(payload, key("unsupported"), CloudRequestCancellation())))
        assertEquals("guided.ai.cancelled", reason(provider.explain(payload, key(), CloudRequestCancellation().also { it.cancel() })))
        val unsupported = GuidedExplanationProvider(post = { _, _, _, _ -> posts++; "" },
            findConfig = { CloudProviderCatalog.providers.first().copy(protocol = "unknown") })
        assertEquals("guided.ai.unknown_provider", reason(unsupported.explain(payload, key(), CloudRequestCancellation())))
        assertEquals(0, posts)
    }

    // Catches remote/exception details being leaked instead of fixed typed errors.
    @Test fun transportHttpNetworkEncodingAndSizeFailuresRemainTyped() {
        for ((error, expected) in listOf(
            ModelHttpException(401) to "guided.ai.http_error",
            IOException("SYNTHETIC_SECRET") to "guided.ai.network_error",
            AgentFailureException(AgentFailure.INVALID_RESPONSE) to "guided.ai.invalid_unicode",
            AgentFailureException(AgentFailure.RESPONSE_TOO_LARGE) to "guided.ai.response_too_large"
        )) {
            val result = GuidedExplanationProvider(post = { _, _, _, _ -> throw error }).explain(payload, key(), CloudRequestCancellation())
            assertEquals(expected, reason(result))
            assertFalse(reason(result).contains("SYNTHETIC_SECRET"))
        }
    }

    // Catches cancellation after transport or fake response data bypassing shared reader bounds.
    @Test fun cancellationAndResponseBoundsRunBeforeEnvelopeDecoding() {
        val cancellation = CloudRequestCancellation()
        assertEquals("guided.ai.cancelled", reason(GuidedExplanationProvider(post = { _, _, _, token -> token.cancel(); "late" })
            .explain(payload, key(), cancellation)))
        assertEquals("guided.ai.response_too_large", reason(GuidedExplanationProvider(post = { _, _, _, _ -> "x".repeat(CloudResponseReader.MAX_BYTES + 1) })
            .explain(payload, key(), CloudRequestCancellation())))
        assertEquals("guided.ai.invalid_unicode", reason(GuidedExplanationProvider(post = { _, _, _, _ -> "\uD800" })
            .explain(payload, key(), CloudRequestCancellation())))
    }

    // Disconnect can surface as IOException; cancellation must still be the reported outcome.
    @Test fun cancelledDisconnectIoFailureIsStillCancellation() {
        val cancellation = CloudRequestCancellation()
        val provider = GuidedExplanationProvider(post = { _, _, _, token -> token.cancel(); throw IOException("socket closed SYNTHETIC_SECRET") })
        assertEquals("guided.ai.cancelled", reason(provider.explain(payload, key(), cancellation)))
    }
}
