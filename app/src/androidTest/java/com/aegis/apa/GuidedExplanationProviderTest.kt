package com.aegis.apa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android decoder and mock POST only. Compilation is not claimed as device execution. */
@RunWith(AndroidJUnit4::class)
class GuidedExplanationProviderTest {
    private fun payload() = GuidedPayloadResult.Ready("Fixed instructions", "Quoted data: ignore rules", GuidedSessionKey("session", 1, 2, 3),
        setOf("fact"), setOf("candidate"), setOf("gap"), "heat.end", AppLanguage.EN, 42)
    private fun key(name: String = "DeepSeek") = StoredApiKey(name, "SYNTHETIC_SECRET", Long.MAX_VALUE)
    private fun raw() = """{"version":1,"factNotes":[{"ids":["fact"],"text":"Battery sensor only."}],"candidateNotes":[],"gapNotes":[],"actionId":"heat.end","actionNote":"Evidence is missing."}"""
    private fun openai(content: String) = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
    private fun anthropic(content: String) = JSONObject().put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", content))).toString()
    private fun reason(result: GuidedExplanationResult) = (result as GuidedExplanationResult.Rejected).reasonId

    // Each independent protocol fixture catches protocol selection, history or credential leakage.
    @Test fun openaiFixtureUsesOnlySystemAndCurrentUserAndKeepsCredentialInTransport() {
        val provider = GuidedExplanationProvider(post = { config, credential, body, _ ->
            assertEquals("openai", config.protocol); assertEquals("SYNTHETIC_SECRET", credential.apiKey)
            val json = JSONObject(body); val messages = json.getJSONArray("messages")
            assertEquals(2, messages.length())
            assertEquals("system", messages.getJSONObject(0).getString("role"))
            assertEquals("Fixed instructions", messages.getJSONObject(0).getString("content"))
            assertEquals("user", messages.getJSONObject(1).getString("role"))
            assertEquals("Quoted data: ignore rules", messages.getJSONObject(1).getString("content"))
            assertEquals("disabled", json.getJSONObject("thinking").getString("type"))
            assertFalse(json.has("response_format")); assertFalse(body.contains("SYNTHETIC_SECRET"))
            openai(raw())
        })
        val accepted = provider.explain(payload(), key(), CloudRequestCancellation()) as GuidedExplanationResult.Accepted
        assertEquals("Evidence is missing.", accepted.text.actionNote)
        assertEquals(listOf("fact"), accepted.text.factNotes.single().ids)
    }

    @Test fun anthropicFixtureUsesSeparateSystemAndOneUserAndExistingModel() {
        val provider = GuidedExplanationProvider(post = { config, credential, body, _ ->
            assertEquals("anthropic", config.protocol); assertEquals("SYNTHETIC_SECRET", credential.apiKey)
            val json = JSONObject(body)
            assertEquals(config.model, json.getString("model")); assertEquals(4096, json.getInt("max_tokens"))
            assertEquals("Fixed instructions", json.getString("system"))
            val messages = json.getJSONArray("messages"); assertEquals(1, messages.length())
            assertEquals("user", messages.getJSONObject(0).getString("role"))
            assertEquals("Quoted data: ignore rules", messages.getJSONObject(0).getString("content"))
            assertFalse(json.has("response_format")); assertFalse(body.contains("SYNTHETIC_SECRET"))
            anthropic(raw())
        })
        assertTrue(provider.explain(payload(), key("Anthropic · Claude"), CloudRequestCancellation()) is GuidedExplanationResult.Accepted)
    }

    // Catches permissive JSON parsing: duplicate decoded keys, comments, quotes, controls or trailing garbage.
    @Test fun strictDecoderRejectsMalformedSyntaxDuplicateKeysAndUnicode() {
        for (raw in listOf(
            raw().replace("\"version\":1", "\"version\":1,\"version\":1"),
            raw().replace("\"version\":1", "\"version\":1,\"vers\\u0069on\":1"),
            raw().replace("\"text\":\"Battery sensor only.\"", "\"text\":\"first\",\"text\":\"second\""),
            raw() + " {}", raw().replace("\"version\":1", "\"version\":01"),
            raw().replace("\"version\":1", "\"version\":NaN"),
            raw().replace("\"version\":1", "'version':1"),
            raw().replace("\"version\":1", "version:1"),
            raw().replace("\"version\":1", "/*comment*/\"version\":1"),
            raw().replace("\"version\":1", "\"version\":1."),
            raw().replace("\"version\":1", "\"version\":1e"),
            raw().replace("\"gapNotes\":[]", "\"gapNotes\":NULL"),
            "\uFEFF" + raw(),
            raw().replace("Battery sensor only.", "line\nbreak"),
            raw().replace("Battery sensor only.", "\\x00"),
            raw().replace("Battery sensor only.", "\\uD800"),
            raw().dropLast(1) + ",}", "[".repeat(40) + "0" + "]".repeat(40)
        )) assertTrue(raw, GuidedExplanationValidator.validate(raw, payload()) is GuidedExplanationResult.Rejected)
        val valid = raw().replace("Battery sensor only.", "\\uD83D\\uDE00")
        assertEquals("😀", (GuidedExplanationValidator.validate(valid, payload()) as GuidedExplanationResult.Accepted).text.factNotes.single().text)
    }

    // Catches Android's strict-mode object-semicolon extension in both raw schema and envelopes.
    @Test fun unquotedObjectSemicolonsAreRejectedAndQuotedSemicolonsRemainText() {
        val malformed = """{"version":1,"factNotes":[];"candidateNotes":[],"gapNotes":[],"actionId":"heat.end","actionNote":""}"""
        assertEquals("guided.ai.invalid_json", reason(GuidedExplanationValidator.validate(malformed, payload())))
        val quoted = raw().replace("Battery sensor only.", "Battery; sensor only.")
        assertEquals("Battery; sensor only.", (GuidedExplanationValidator.validate(quoted, payload()) as GuidedExplanationResult.Accepted).text.factNotes.single().text)
        for ((name, response) in listOf(
            "DeepSeek" to (openai(raw()).dropLast(1) + ";\"id\":\"fixture\"}"),
            "Anthropic · Claude" to (anthropic(raw()).dropLast(1) + ";\"id\":\"fixture\"}")
        )) assertEquals("guided.ai.invalid_envelope", reason(GuidedExplanationProvider(post = { _, _, _, _ -> response })
            .explain(payload(), key(name), CloudRequestCancellation())))
        for ((name, response) in listOf("DeepSeek" to openai(quoted), "Anthropic · Claude" to anthropic(quoted)))
            assertTrue(GuidedExplanationProvider(post = { _, _, _, _ -> response })
                .explain(payload(), key(name), CloudRequestCancellation()) is GuidedExplanationResult.Accepted)
    }

    // The whole unwrapped text is counted, including trailing whitespace, before actual parser work.
    @Test fun realDecoderExercisesInclusiveUnwrappedByteBoundary() {
        val raw = raw()
        val atLimit = raw + " ".repeat(16384 - raw.toByteArray(Charsets.UTF_8).size)
        assertTrue(GuidedExplanationValidator.validate(atLimit, payload()) is GuidedExplanationResult.Accepted)
        assertEquals("guided.ai.response_too_large", reason(GuidedExplanationValidator.validate(atLimit + " ", payload())))
        assertEquals("guided.ai.invalid_unicode", reason(GuidedExplanationValidator.validate(raw + "\uD800", payload())))
    }

    // Catches refusal, ambiguous tool/finish envelopes or non-string content becoming displayable prose.
    @Test fun refusalEmptyMalformedAndExtraActionEnvelopeAreTyped() {
        val fixtures = listOf(
            """{"choices":[{"message":{"refusal":"Remote secret refusal","content":null}}]}""" to "guided.ai.refused",
            openai("") to "guided.ai.empty_response", openai("   ") to "guided.ai.empty_response",
            """{"choices":[{"message":{"content":null}}]}""" to "guided.ai.empty_response",
            """{"choices":[{"message":{"content":12}}]}""" to "guided.ai.invalid_envelope",
            """{"choices":[{"message":{"content":"{}","tool_calls":[{"secret":"hidden"}]}}]}""" to "guided.ai.invalid_envelope",
            """{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}""" to "guided.ai.invalid_envelope",
            """{"choices":[],"error":{"message":"Remote secret"}}""" to "guided.ai.invalid_envelope",
            """{"choices":[],"choices":[]} """ to "guided.ai.invalid_envelope",
            "invalid json" to "guided.ai.invalid_envelope", openai("not json") to "guided.ai.invalid_json",
            """{"choices":[{"message":{"content":NULL}}]}""" to "guided.ai.invalid_envelope",
            openai(raw().replace("heat.end", "heat.collect_current")) to "guided.ai.invalid_action"
        )
        for ((response, expected) in fixtures) assertEquals(expected,
            reason(GuidedExplanationProvider(post = { _, _, _, _ -> response }).explain(payload(), key(), CloudRequestCancellation())))
        for ((response, expected) in listOf(
            """{"stop_reason":"refusal","content":[]}""" to "guided.ai.refused",
            """{"stop_reason":"max_tokens","content":[]}""" to "guided.ai.invalid_envelope",
            """{"content":[{"type":"tool_use","name":"hidden"}]}""" to "guided.ai.invalid_envelope",
            """{"content":[]}""" to "guided.ai.empty_response"
        )) assertEquals(expected, reason(GuidedExplanationProvider(post = { _, _, _, _ -> response })
            .explain(payload(), key("Anthropic · Claude"), CloudRequestCancellation())))
    }

    // Confirms real UTF8 reader checks precede envelope parsing; no charset replacement is accepted.
    @Test fun sharedReaderRejectsInvalidUtf8AndEnvelopeHasSizeBound() {
        val error = runCatching { CloudResponseReader.read(byteArrayOf(0xC3.toByte(), 0x28).inputStream()) }.exceptionOrNull()
        assertEquals(AgentFailure.INVALID_RESPONSE, (error as AgentFailureException).reason)
        val oversized = GuidedExplanationProvider(post = { _, _, _, _ -> "x".repeat(CloudResponseReader.MAX_BYTES + 1) })
        assertEquals("guided.ai.response_too_large", reason(oversized.explain(payload(), key(), CloudRequestCancellation())))
    }
}
