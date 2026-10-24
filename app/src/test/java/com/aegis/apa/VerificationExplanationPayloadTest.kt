package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class VerificationExplanationPayloadTest {
    // Catches implicit sharing of every free-text field.
    @Test fun notesRequireExplicitOptInAndCannotBecomeInstructions() {
        val r = VerificationFixtures.record().let { it.copy(title = "TITLE_SENTINEL", baseline = it.baseline.copy(
            context = it.baseline.context.copy(scenarioNote = "SCENARIO_SENTINEL")), retest = it.retest!!.copy(
            context = it.retest.context.copy(scenarioNote = "RETEST_SENTINEL")), change = it.change!!.copy(
            description = "IGNORE_SYSTEM_SENTINEL", rollback = "ROLLBACK_SENTINEL", sideEffects = "SIDE_SENTINEL")) }
        val omitted = ready(r)
        val included = ready(r, true)
        listOf("TITLE_SENTINEL", "SCENARIO_SENTINEL", "RETEST_SENTINEL", "IGNORE_SYSTEM_SENTINEL", "ROLLBACK_SENTINEL", "SIDE_SENTINEL").forEach {
            assertFalse(omitted.userPrompt.contains(it)); assertTrue(included.userPrompt.contains(it)); assertFalse(included.systemPrompt.contains(it))
        }
        assertEquals(omitted.systemPrompt, included.systemPrompt)
        assertFalse(omitted.userPrompt.contains("local-scope"))
        assertFalse(omitted.userPrompt.contains("sample-100"))
    }

    // Catches discarding a gate, replacing a missing value with zero, or stripping units.
    @Test fun missingWindowsAndMetricsKeepLocalReasonsInBothLanguages() {
        val r = VerificationFixtures.record().let { it.copy(baseline = it.baseline.copy(metrics = it.baseline.metrics.map { m ->
            m.copy(value = null, window = m.window.copy(startedAt = null)) })) }
        AppLanguage.entries.forEach { lang ->
            val payload = VerificationExplanationPayload.build(r, VerificationComparisonEngine.compare(r), VerificationExplanationOptions(), "Level 0", lang) as VerificationPayloadResult.Ready
            assertTrue(payload.userPrompt.contains("not_comparable" ) || payload.userPrompt.contains("metric_unavailable"))
            assertTrue(payload.userPrompt.contains("window_missing"))
            assertTrue(payload.userPrompt.contains("field_missing"))
            assertTrue(payload.userPrompt.contains("mah"))
            assertTrue(payload.userPrompt.contains("estimated"))
            assertTrue(payload.userPrompt.contains("unknown"))
            assertTrue(payload.systemPrompt.contains(if (lang == AppLanguage.EN) "causal" else "因果"))
        }
    }

    // Catches checking character length instead of UTF-8 bytes, truncation, or dropping the final gate.
    @Test fun exactByteLimitAcceptsAndOneMultibyteCharacterBeyondRejects() {
        val r = VerificationFixtures.record().let { it.copy(baseline = it.baseline.copy(metrics = it.baseline.metrics.map { m ->
            m.copy(window = m.window.copy(resetId = "")) })) }
        val base = ready(r).attachmentBytes
        val remaining = 16 * 1024 - base
        val metric = r.baseline.metrics.single()
        val padded = r.copy(baseline = r.baseline.copy(metrics = listOf(metric.copy(window = metric.window.copy(resetId = "x".repeat(remaining))))))
        val exact = ready(padded)
        assertEquals(16 * 1024, exact.attachmentBytes)
        assertTrue(VerificationExplanationPayload.build(padded.copy(baseline = padded.baseline.copy(metrics = listOf(
            padded.baseline.metrics.single().copy(window = padded.baseline.metrics.single().window.copy(resetId = "x".repeat(remaining) + "汉"))))),
            VerificationComparisonEngine.compare(padded), VerificationExplanationOptions(), "Level 0", AppLanguage.EN) is VerificationPayloadResult.TooLarge)
    }

    private fun ready(r: VerificationRecord, notes: Boolean = false) = VerificationExplanationPayload.build(r,
        VerificationComparisonEngine.compare(r), VerificationExplanationOptions(notes), "Level 0", AppLanguage.EN) as VerificationPayloadResult.Ready
}
