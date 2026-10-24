package com.aegis.apa.agent

import java.io.IOException
import java.util.concurrent.CancellationException

/** Stateless optional explanation boundary; credentials are supplied only to POST, never prompts. */
class GuidedExplanationProvider(
    private val post: (CloudProviderConfig, StoredApiKey, String, CloudRequestCancellation) -> String =
        { config, key, body, cancellation -> CloudHttpTransport.post(config, key, body, cancellation) },
    private val findConfig: (String) -> CloudProviderConfig? = CloudProviderCatalog::find
) {
    fun explain(payload: GuidedPayloadResult.Ready, credentials: StoredApiKey,
        cancellation: CloudRequestCancellation): GuidedExplanationResult {
        fun reject(suffix: String): GuidedExplanationResult.Rejected {
            val reason = try { cancellation.ensureActive(); suffix }
            catch (_: CancellationException) { "cancelled" }
            return GuidedExplanationResult.Rejected("guided.ai.$reason")
        }
        try {
            cancellation.ensureActive()
            if (credentials.apiKey.isBlank()) return reject("missing_key")
            if (credentials.expiresAt <= System.currentTimeMillis()) return reject("expired_key")
            val config = findConfig(credentials.provider) ?: return reject("unknown_provider")
            if (config.protocol !in setOf("openai", "anthropic")) return reject("unknown_provider")
            val promptBytes = try { guidedUtf8Size(payload.systemPrompt).toLong() + guidedUtf8Size(payload.userPrompt) }
            catch (_: IllegalArgumentException) { return reject("invalid_unicode") }
            if (promptBytes > GuidedExplanationPayload.MAX_BYTES) return reject("payload_too_large")
            val user = mapOf("role" to "user", "content" to payload.userPrompt)
            // Preserve existing protocol/model shapes. Arbitrary compatible providers are not assumed
            // to support response_format/json_schema; local decoding and validation remain mandatory.
            val request = if (config.protocol == "anthropic") mapOf("model" to config.model,
                "max_tokens" to 4096, "system" to payload.systemPrompt, "messages" to listOf(user))
            else linkedMapOf<String, Any?>("model" to config.model, "messages" to listOf(
                mapOf("role" to "system", "content" to payload.systemPrompt), user
            )).apply { if (config.name == "DeepSeek") put("thinking", mapOf("type" to "disabled")) }
            val body = GuidedWireJson.encode(request)
            cancellation.ensureActive()
            // Expiry is checked again at transmission after request construction. Supplier ownership
            // and provider switching remain Task 12 responsibilities, not hidden credential storage.
            if (credentials.expiresAt <= System.currentTimeMillis()) return reject("expired_key")
            val response = post(config, credentials, body, cancellation)
            cancellation.ensureActive()
            // Real POST has already used CloudResponseReader. Keep injected transport fixtures under
            // that same envelope bound and forbid malformed UTF16 before converting them to UTF8.
            val responseBytes = try { guidedUtf8Size(response) }
            catch (_: IllegalArgumentException) { return reject("invalid_unicode") }
            if (responseBytes > CloudResponseReader.MAX_BYTES) return reject("response_too_large")
            val bounded = CloudResponseReader.read(response.byteInputStream(Charsets.UTF_8))
            val root = try { GuidedAndroidJson.decode(bounded) as? Map<*, *> }
            catch (_: Exception) { return reject("invalid_envelope") } ?: return reject("invalid_envelope")
            cancellation.ensureActive()
            val raw = if (config.protocol == "anthropic") {
                if (root["stop_reason"] == "refusal") return reject("refused")
                if (root["stop_reason"] != null && root["stop_reason"] != "end_turn") return reject("invalid_envelope")
                val blocks = root["content"] as? List<*> ?: return reject("invalid_envelope")
                val texts = mutableListOf<String>()
                for (item in blocks) {
                    val block = item as? Map<*, *> ?: return reject("invalid_envelope")
                    if (block["type"] == "refusal") return reject("refused")
                    if (block["type"] != "text" || block["text"] !is String) return reject("invalid_envelope")
                    texts += block["text"] as String
                }
                texts.joinToString("\n")
            } else {
                val choices = root["choices"] as? List<*> ?: return reject("invalid_envelope")
                if (choices.size != 1) return reject("invalid_envelope")
                val choice = choices.single() as? Map<*, *> ?: return reject("invalid_envelope")
                val message = choice["message"] as? Map<*, *> ?: return reject("invalid_envelope")
                if (message["refusal"] != null) return reject("refused")
                if (choice["finish_reason"] != null && choice["finish_reason"] != "stop") return reject("invalid_envelope")
                if (message["tool_calls"] != null || message["function_call"] != null) return reject("invalid_envelope")
                val content = message["content"]
                if (content == null) "" else content as? String ?: return reject("invalid_envelope")
            }
            if (raw.isBlank()) return reject("empty_response")
            cancellation.ensureActive()
            val result = GuidedExplanationValidator.validate(raw, payload)
            cancellation.ensureActive()
            return result
        } catch (_: CancellationException) { return reject("cancelled") }
        catch (_: ModelHttpException) { return reject("http_error") }
        catch (error: AgentFailureException) {
            return reject(when (error.reason) {
                AgentFailure.RESPONSE_TOO_LARGE -> "response_too_large"
                AgentFailure.INVALID_RESPONSE -> "invalid_unicode"
                else -> "invalid_envelope"
            })
        } catch (_: IOException) { return reject("network_error") }
        catch (_: Exception) { return reject("invalid_envelope") }
    }
}
