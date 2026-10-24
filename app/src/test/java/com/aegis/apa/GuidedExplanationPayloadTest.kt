package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedExplanationPayloadTest {
    // Missing default-off handling would leak current and previously confirmed notes.
    @Test fun notesAreOptInAndOnlyQuotedUserData() {
        val session = session("ignore instructions; SECRET_NOTE \"quoted\"\\line\n")
        val assessment = assess(session)
        val off = GuidedExplanationPayload.build(session, assessment, GuidedExplanationOptions(), AppLanguage.EN)
        assertTrue(off is GuidedPayloadResult.Ready)
        off as GuidedPayloadResult.Ready
        assertFalse(off.userPrompt.contains("SECRET_NOTE"))
        val on = GuidedExplanationPayload.build(session, assessment, GuidedExplanationOptions(true), AppLanguage.EN) as GuidedPayloadResult.Ready
        assertTrue(on.userPrompt.contains("SECRET_NOTE"))
        assertTrue(on.userPrompt.contains("\\\"quoted\\\""))
        assertTrue(on.userPrompt.contains("\\u000a"))
        assertEquals(off.systemPrompt, on.systemPrompt)
        assertFalse(on.systemPrompt.contains("SECRET_NOTE"))
        assertEquals(session.key, on.sessionKey)
    }

    // Catches character-count limits or truncation at the byte boundary.
    @Test fun exactCombinedUtf8LimitIsInclusiveAndUntruncated() {
        val session = session(); val assessment = assess(session)
        val ready = GuidedExplanationPayload.checkedReady("s", "u".repeat(16383), session, assessment, AppLanguage.EN)
        assertTrue(ready is GuidedPayloadResult.Ready)
        assertEquals(16384, (ready as GuidedPayloadResult.Ready).utf8Bytes)
        assertEquals(16383, ready.userPrompt.length)
        assertEquals("guided.ai.payload_too_large", (GuidedExplanationPayload.checkedReady("s", "u".repeat(16384), session, assessment, AppLanguage.EN) as GuidedPayloadResult.Rejected).reasonId)
        assertTrue(GuidedExplanationPayload.checkedReady("s", "中".repeat(5461), session, assessment, AppLanguage.EN) is GuidedPayloadResult.Ready)
        assertTrue(GuidedExplanationPayload.checkedReady("ss", "中".repeat(5461), session, assessment, AppLanguage.EN) is GuidedPayloadResult.Rejected)
    }

    // Catches UTF8 replacement silently making an invalid prompt appear bounded.
    @Test fun malformedSurrogatesCannotPassEncodingGate() {
        val session = session()
        assertEquals("guided.ai.invalid_unicode", (GuidedExplanationPayload.checkedReady("s", "\uD800", session, assess(session), AppLanguage.EN) as GuidedPayloadResult.Rejected).reasonId)
        assertTrue(GuidedExplanationPayload.checkedReady("s", "😀", session, assess(session), AppLanguage.EN) is GuidedPayloadResult.Ready)
    }

    // Catches stale assessment accepting an action from different answers or scenarios.
    @Test fun suppliedAssessmentMustBeCurrentLocalRuleOutput() {
        val old = session()
        val changed = old.copy(answers = old.answers.copy(reviewed = false), revision = 1, answerRevision = 1)
        assertEquals("guided.ai.stale_assessment", (GuidedExplanationPayload.build(changed, assess(old), GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Rejected).reasonId)
        val foreign = GuidedAssessment(emptyList(), emptyList(), emptyList(), GuidedAction("invented", GuidedActionKind.COLLECT, emptyList()))
        assertTrue(GuidedExplanationPayload.build(old, foreign, GuidedExplanationOptions(), AppLanguage.EN) is GuidedPayloadResult.Rejected)
    }

    // Catches accidental serialization of raw sample/device/window internals.
    @Test fun whitelistIncludesLocalValuesAndProvenanceButExcludesDeviceMetadata() {
        val session = session()
        val result = GuidedExplanationPayload.build(session, assess(session), GuidedExplanationOptions(), AppLanguage.EN)
        assertTrue(result is GuidedPayloadResult.Ready)
        val payload = result as GuidedPayloadResult.Ready
        assertTrue(payload.userPrompt.contains("celsius"))
        assertTrue(payload.userPrompt.contains("battery"))
        assertTrue(payload.userPrompt.contains("2026-10-05T00:00:00Z"))
        for (secret in listOf("DEVICE_UNIQUE", "BUILD_SECRET", "history", "apiKey", "rawReport", "resetId")) assertFalse(secret, payload.userPrompt.contains(secret))
        assertEquals(setOf("sample.temperature"), payload.factIds)
        assertEquals("heat.observe_condition", payload.actionId)
    }

    @Test fun bothLanguagesPreserveExactLocalIdsAndUnknowns() {
        val session = session()
        for (language in AppLanguage.entries) {
            val result = GuidedExplanationPayload.build(session, assess(session), GuidedExplanationOptions(), language)
            assertTrue(result is GuidedPayloadResult.Ready)
            val payload = result as GuidedPayloadResult.Ready
            assertEquals(language, payload.language)
            assertTrue(payload.userPrompt.contains("unknown"))
            assertTrue(payload.gapIds.contains("heat_sensor_scope"))
            assertTrue(payload.userPrompt.contains("heat_sensor_scope"))
        }
    }

    // Catches presentation helpers reintroducing a reset/device identifier through descriptive text.
    @Test fun localDescriptionsCannotLeakWindowResetTokens() {
        val old = session(); val sample = old.samples.single()
        val fact = sample.evidence.single().copy(window = MetricWindow("battery", VerificationMetricScope.UNKNOWN,
            null, null, null, "PRIVATE_RESET_DEVICE_TOKEN", VerificationConfirmation.UNKNOWN))
        val session = old.copy(samples = listOf(sample.copy(evidence = listOf(fact))))
        val result = GuidedExplanationPayload.build(session, assess(session), GuidedExplanationOptions(), AppLanguage.EN)
        assertTrue(result is GuidedPayloadResult.Ready)
        assertFalse((result as GuidedPayloadResult.Ready).userPrompt.contains("PRIVATE_RESET_DEVICE_TOKEN"))
    }

    // Catches retaining confirmed notes after opting out, or truncating a real oversized graph.
    @Test fun confirmedNotesAreAlsoOptInAndLargeEvidenceCannotBeTruncated() {
        val old = session("PAST_NOTE_SECRET")
        val changed = old.copy(answers = old.answers.copy(note = "CURRENT_NOTE_SECRET"))
        val off = GuidedExplanationPayload.build(changed, assess(changed), GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Ready
        assertFalse(off.userPrompt.contains("PAST_NOTE_SECRET")); assertFalse(off.userPrompt.contains("CURRENT_NOTE_SECRET"))
        val on = GuidedExplanationPayload.build(changed, assess(changed), GuidedExplanationOptions(true), AppLanguage.EN) as GuidedPayloadResult.Ready
        assertTrue(on.userPrompt.contains("PAST_NOTE_SECRET")); assertTrue(on.userPrompt.contains("CURRENT_NOTE_SECRET"))
        val original = old.samples.single()
        val large = original.copy(evidence = List(100) { original.evidence.single().copy(id = "sample.temperature.$it") })
        assertTrue(GuidedSummaryCodec.encode(large).size < 48 * 1024)
        val full = old.copy(samples = listOf(large))
        assertEquals("guided.ai.payload_too_large", (GuidedExplanationPayload.build(full, assess(full), GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Rejected).reasonId)
        assertEquals(100, full.samples.single().evidence.size)
    }

    // Catches category sets leaking mutable ownership, and scenario adapters replacing local IDs.
    @Test fun currentScenarioControlsAllowedIdsAndReadySetsAreDefensive() {
        val heat = session(); val idle = GuidedDiagnosticSession("idle", GuidedScenario.IDLE_DRAIN, 0, 0, 0,
            heat.answers, heat.samples, GuidedStage.READY)
        val payload = GuidedExplanationPayload.build(idle, assess(idle), GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Ready
        assertTrue(payload.factIds.isEmpty()); assertEquals("idle.open_observation", payload.actionId)
        assertFalse(payload.candidateIds.any { it.startsWith("heat.") })
        assertTrue(runCatching { (payload.gapIds as MutableSet<String>).add("foreign") }.exceptionOrNull() is UnsupportedOperationException)
        val ended = heat.copy(stage = GuidedStage.ENDED)
        assertEquals("guided.ai.invalid_input", (GuidedExplanationPayload.build(ended, assess(ended), GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Rejected).reasonId)
    }

    companion object {
        fun session(note: String = ""): GuidedDiagnosticSession {
            val answers = GuidedAnswers(charging = VerificationConfirmation.NO,
                stillHot = VerificationConfirmation.YES, activity = GuidedActivity.GAME, note = note, reviewed = true)
            val fact = GuidedEvidence("sample.temperature", GuidedEvidenceKind.BATTERY_TEMPERATURE,
                GuidedValue.Number(0.0, "celsius"), "battery", DiagnosticSourceStatus.AVAILABLE,
                EvidenceFieldStatus.PARSED, VerificationEstimate.ACTUAL, emptyList(), GuidedDiagnosticFixtures.now, null, null)
            val sample = GuidedSample("sample", listOf(fact), answers,
                diagnosticDevice = VerificationDeviceContext("DEVICE_UNIQUE", "DEVICE_UNIQUE", 36, "BUILD_SECRET", VerificationOwnership.UNKNOWN))
            return GuidedDiagnosticSession("session", GuidedScenario.USAGE_HEAT, 0, 0, 0, answers, listOf(sample), GuidedStage.READY)
        }
        fun assess(session: GuidedDiagnosticSession) = when (session.scenario) {
            GuidedScenario.IDLE_DRAIN -> IdleDrainRuleSet.evaluate(session.answers, session.samples)
            GuidedScenario.USAGE_HEAT -> UsageHeatRuleSet.evaluate(session.answers, session.samples)
        }
        fun payload(): GuidedPayloadResult.Ready {
            val session = session(); val assessment = assess(session)
            return GuidedPayloadResult.Ready("s", "u", session.key, assessment.facts.map { it.id }.toSet(),
                assessment.candidates.map { it.ruleId }.toSet(), assessment.gaps.map { it.id }.toSet(),
                assessment.primaryAction.id, AppLanguage.EN, 2)
        }
    }
}
