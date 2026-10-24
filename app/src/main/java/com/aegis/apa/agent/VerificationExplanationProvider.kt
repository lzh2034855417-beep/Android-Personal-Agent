package com.aegis.apa.agent

import org.json.JSONArray
import org.json.JSONObject

/** Stateless request boundary. It has no access to chat history, stores, snapshots or report URIs. */
class VerificationExplanationProvider(
    private val post: (CloudProviderConfig, StoredApiKey, String, CloudRequestCancellation) -> String =
        { config, key, body, cancellation -> CloudHttpTransport.post(config, key, body, cancellation) },
    private val findConfig: (String) -> CloudProviderConfig? = CloudProviderCatalog::find
) {
    fun explain(payload: VerificationPayloadResult.Ready, credentials: StoredApiKey, cancellation: CloudRequestCancellation): AgentReport {
        cancellation.ensureActive()
        if (credentials.apiKey.isBlank()) throw AgentFailureException(AgentFailure.MISSING_KEY)
        if (credentials.expiresAt <= System.currentTimeMillis()) throw AgentFailureException(AgentFailure.EXPIRED_KEY)
        val config = findConfig(credentials.provider) ?: throw AgentFailureException(AgentFailure.UNKNOWN_PROVIDER)
        if (config.protocol !in setOf("openai", "anthropic")) throw AgentFailureException(AgentFailure.UNKNOWN_PROVIDER)
        val user = JSONObject().put("role", "user").put("content", payload.userPrompt)
        val body = if (config.protocol == "anthropic") JSONObject().put("model", config.model).put("max_tokens", 4096)
            .put("system", payload.systemPrompt).put("messages", JSONArray().put(user))
        else JSONObject().put("model", config.model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", payload.systemPrompt)).put(user))
            .apply { if (config.name == "DeepSeek") put("thinking", JSONObject().put("type", "disabled")) }
        val response = post(config, credentials, body.toString(), cancellation)
        cancellation.ensureActive()
        val boundedResponse = CloudResponseReader.read(response.byteInputStream(Charsets.UTF_8))
        val raw = try {
            val json = JSONObject(boundedResponse)
            if (config.protocol == "anthropic") {
                val blocks = json.optJSONArray("content") ?: JSONArray()
                buildList {
                    for (index in 0 until blocks.length()) {
                        val block = blocks.optJSONObject(index) ?: continue
                        if (block.optString("type") == "text") (block.opt("text") as? String)?.let { add(it) }
                    }
                }.joinToString("\n")
            } else json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").opt("content") as? String ?: ""
        } catch (_: org.json.JSONException) { throw AgentFailureException(AgentFailure.INVALID_RESPONSE) }
        val content = sanitizeCloudAnalysisResponse(raw, payload.selectedLevel, language = payload.language)
        if (content.isBlank()) throw AgentFailureException(AgentFailure.EMPTY_RESPONSE)
        cancellation.ensureActive()
        return AgentReport(content, emptyList(), "${config.shortLabel} · ${config.model}")
    }
}
