package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test

/** Pure semantic fixtures. Actual JSON decoding is exercised separately on Android, never mocked. */
class GuidedExplanationValidatorTest {
    private val payload = GuidedExplanationPayloadTest.payload()
    private fun document() = linkedMapOf<String, Any?>("version" to 1,
        "factNotes" to listOf(note("sample.temperature", "Battery sensor only.")),
        "candidateNotes" to emptyList<Any>(), "gapNotes" to emptyList<Any>(),
        "actionId" to "heat.observe_condition", "actionNote" to "Observe ordinary use voluntarily.")
    private fun note(id: String, text: String) = mapOf("ids" to listOf(id), "text" to text)
    private fun rejected(doc: Any?) = assertTrue("Expected rejected: $doc", GuidedExplanationValidator.validateDecoded(doc, payload) is GuidedExplanationResult.Rejected)

    // Catches decoding an oversized raw body or silently replacing invalid UTF8 before validation.
    @Test fun rawSizeAndUnicodeAreRejectedBeforeAndroidDecoder() {
        assertEquals("guided.ai.response_too_large", (GuidedExplanationValidator.validate("x".repeat(16385), payload) as GuidedExplanationResult.Rejected).reasonId)
        assertEquals("guided.ai.response_too_large", (GuidedExplanationValidator.validate("中".repeat(5462), payload) as GuidedExplanationResult.Rejected).reasonId)
        assertEquals("guided.ai.invalid_unicode", (GuidedExplanationValidator.validate("\uD800", payload) as GuidedExplanationResult.Rejected).reasonId)
    }

    // Android decodeLiteral ignores keyword case; this actual preflight must reject it before parse.
    @Test fun lexicalPreflightRejectsNonJsonKeywordCaseAndBom() {
        for (raw in listOf("{\"value\":NULL}", "{\"value\":True}", "{\"value\":truE}", "{\"value\":False}", "\uFEFF{}"))
            assertTrue(raw, runCatching { GuidedAndroidJson.checkLexical(raw) }.exceptionOrNull() is IllegalArgumentException)
        for (raw in listOf("{\"value\":null}", "{\"value\":true}", "{\"value\":false}", "{\"value\":1E10}", "{\"value\":\"NULL True False \\uFEFF\"}"))
            GuidedAndroidJson.checkLexical(raw)
    }

    // Android nextInObject accepts semicolons even in strict mode, including after array values.
    @Test fun lexicalPreflightRejectsUnquotedSemicolonsButPreservesQuotedText() {
        for (raw in listOf(
            """{"version":1,"factNotes":[];"candidateNotes":[],"gapNotes":[],"actionId":"heat.end","actionNote":""}""",
            """{"choices":[];"id":"fixture"}""",
            """{"content":[];"id":"fixture"}"""
        )) assertTrue(raw, runCatching { GuidedAndroidJson.checkLexical(raw) }.exceptionOrNull() is IllegalArgumentException)
        for (raw in listOf(
            """{"text":"Battery; sensor only."}""",
            """{"text":"Quoted \";\" remains data."}""",
            """{"text":"Escaped \u003b remains data."}"""
        )) GuidedAndroidJson.checkLexical(raw)
    }

    // Catches dropping valid supplementary Unicode or discarding accepted reference text.
    @Test fun validTypedReferencesPreserveOptionalText() {
        val result = GuidedExplanationValidator.validateDecoded(document(), payload) as GuidedExplanationResult.Accepted
        assertEquals(listOf(GuidedReferenceNote(listOf("sample.temperature"), "Battery sensor only.")), result.text.factNotes)
        assertEquals("Observe ordinary use voluntarily.", result.text.actionNote)
    }

    // Catches incomplete schemas, extra actions, version coercion and unknown object members.
    @Test fun requiredExactSchemaRejectsMissingExtraAndWrongTypes() {
        for (key in document().keys) rejected(document().apply { remove(key) })
        rejected(document().apply { put("actions", listOf("invented")) })
        for (version in listOf(null, "1", 2, true, 1.0)) rejected(document().apply { put("version", version) })
        for (value in listOf(null, "notes", mapOf("ids" to "sample.temperature"))) rejected(document().apply { put("factNotes", value) })
        rejected(document().apply { put("factNotes", listOf(mapOf("ids" to listOf("sample.temperature"), "text" to "text", "extra" to 1))) })
        rejected(document().apply { put("actionNote", 1) })
        rejected(emptyList<Any>())
    }

    // Catches union-of-IDs validation permitting cross-category or cross-session references.
    @Test fun typedIdsAndExactActionCannotBeReplaced() {
        for (id in listOf("other.temperature", "heat_sensor_scope", "invented")) rejected(document().apply { put("factNotes", listOf(note(id, "Text"))) })
        rejected(document().apply { put("gapNotes", listOf(note("sample.temperature", "Text"))) })
        rejected(document().apply { put("candidateNotes", listOf(note("heat_sensor_scope", "Text"))) })
        rejected(document().apply { put("actionId", "heat.collect_current") })
        rejected(document().apply { put("factNotes", listOf(mapOf("ids" to listOf(1), "text" to "Text"))) })
        rejected(document().apply { put("factNotes", listOf(mapOf("ids" to emptyList<String>(), "text" to "Text"))) })
        rejected(document().apply { put("factNotes", listOf(mapOf("ids" to listOf("sample.temperature", "sample.temperature"), "text" to "Text"))) })
    }

    // Catches missing per-category list and reference-count bounds.
    @Test fun categoryAndIdCountsAreBounded() {
        val note = note("sample.temperature", "Text")
        rejected(document().apply { put("factNotes", List(21) { note }) })
        rejected(document().apply { put("gapNotes", List(21) { note("heat_sensor_scope", "Text") }) })
        rejected(document().apply { put("candidateNotes", List(4) { note(payload.candidateIds.first(), "Text") }) })
        rejected(document().apply { put("factNotes", listOf(mapOf("ids" to List(21) { "sample.temperature" }, "text" to "Text"))) })
        assertTrue(GuidedExplanationValidator.validateDecoded(document().apply { put("factNotes", List(20) { note }) }, payload) is GuidedExplanationResult.Accepted)
    }

    // Catches UTF16 counting or surrogate replacement bypassing text limits.
    @Test fun textLimitUsesCodePointsAndRejectsMalformedUnicode() {
        for (text in listOf("a".repeat(501), "😀".repeat(501), "\uD800", "\uDC00")) {
            rejected(document().apply { put("actionNote", text) })
            rejected(document().apply { put("factNotes", listOf(note("sample.temperature", text))) })
        }
        for (text in listOf("a".repeat(500), "😀".repeat(500), "")) assertTrue(GuidedExplanationValidator.validateDecoded(document().apply { put("actionNote", text) }, payload) is GuidedExplanationResult.Accepted)
    }

    // Catches only validating IDs while allowing executable capability escalation.
    @Test fun knownReferencesDoNotBypassExistingCapabilityGuard() {
        for (text in listOf("Freeze com.example.app.", "Run adb shell pm disable com.example.app.", "建议冻结应用。")) rejected(document().apply { put("actionNote", text) })
        val accepted = GuidedExplanationValidator.validateDecoded(document().apply { put("actionNote", "Do not freeze com.example.app.") }, payload)
        assertTrue(accepted is GuidedExplanationResult.Accepted)
    }

    // Documents the trust boundary: valid IDs/schema and lexical checks cannot prove prose accuracy.
    @Test fun structuralAcceptanceDoesNotClaimSemanticAccuracy() {
        val text = "The current battery reading proves the app caused yesterday's heat."
        val result = GuidedExplanationValidator.validateDecoded(document().apply { put("factNotes", listOf(note("sample.temperature", text))) }, payload)
        assertTrue(result is GuidedExplanationResult.Accepted)
        assertEquals(text, (result as GuidedExplanationResult.Accepted).text.factNotes.single().text)
        assertTrue(runCatching { (result.text.factNotes as MutableList<GuidedReferenceNote>).clear() }.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(runCatching { (result.text.factNotes.single().ids as MutableList<String>).add("foreign") }.exceptionOrNull() is UnsupportedOperationException)
    }
}
