package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import com.aegis.apa.tool.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GuidedFinalEvidenceRegressionTest {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val answers = GuidedAnswers(reviewed = true, stillHot = VerificationConfirmation.YES,
        charging = VerificationConfirmation.NO, activity = GuidedActivity.GAME)
    private fun sample(input: GuidedInput) = (GuidedEvidenceAdapter.adapt(input, "sample", answers) as GuidedSampleResult.Ready).sample
    private fun payload(s: GuidedSample, result: GuidedAssessment) = GuidedExplanationPayload.build(
        GuidedDiagnosticSession("session", GuidedScenario.USAGE_HEAT, 0, 0, 0, answers, listOf(s), GuidedStage.READY),
        result, GuidedExplanationOptions(), AppLanguage.EN) as GuidedPayloadResult.Ready

    @Test fun acceptedProcessAndRestoredObservationsRetainUnknownChargingAndRoughRateInHeatPayload() {
        for (continuity in BatteryObservationContinuity.entries) {
            val observation = BatteryObservationAnalyzer.finish(BatteryObservationPoint(now.minusSeconds(3600), 80, false),
                BatteryObservationPoint(now, 78, false), continuity = continuity)
            assertEquals(BatteryObservationValidity.VALID, observation.validity)
            val s = sample(GuidedInput(null, null, observation))
            val original = s.evidence.single()
            assertEquals(VerificationConfirmation.UNKNOWN, original.window!!.charging)
            val result = UsageHeatRuleSet.evaluate(answers, listOf(s))
            assertEquals(GuidedValue.Number(2.0, "percent_per_hour"), result.facts.single().value)
            assertEquals(VerificationEstimate.ESTIMATED, result.facts.single().estimate)
            assertEquals(VerificationConfirmation.UNKNOWN, result.facts.single().window!!.charging)
            assertTrue(result.gaps.any { it.id == "heat_observation_rough" })
            assertFalse(result.gaps.any { it.id == "heat_reading_invalid" })
            assertTrue(payload(s, result).userPrompt.contains("\"number\":2.0,\"unit\":\"percent_per_hour\""))
            assertTrue(payload(s, result).userPrompt.contains("\"charging\":\"unknown\""))
        }
    }

    @Test fun invalidObservationsCannotSupplyHeatRate() {
        val start = BatteryObservationPoint(now.minusSeconds(3600), 80, false)
        val end = BatteryObservationPoint(now, 78, false)
        val invalid = listOf(
            BatteryObservationAnalyzer.finish(start, end, chargingObserved = true),
            BatteryObservationAnalyzer.finish(start.copy(powerStateKnown = false), end),
            BatteryObservationAnalyzer.finish(start.copy(sampledAtInstant = now), end),
            BatteryObservationAnalyzer.finish(start, end.copy(levelPercent = 82)))
        for (observation in invalid) {
            assertNotEquals(BatteryObservationValidity.VALID, observation.validity)
            val s = sample(GuidedInput(null, null, observation))
            val result = UsageHeatRuleSet.evaluate(answers, listOf(s))
            assertTrue(result.facts.none { it.kind == GuidedEvidenceKind.OBSERVATION_RATE && it.value != null })
            assertTrue(result.gaps.any { it.id.startsWith("observation_") })
            payload(s, result)
        }
    }

    @Test fun actualOverallZeroAndPositiveWithoutUidReachHeatAndPayloadIndependentlyOfAppCoverage() {
        for (actual in listOf("0", "280")) {
            val report = report(actual)
            assertTrue(report.apps.isEmpty())
            assertEquals(EvidenceFieldStatus.NOT_PARSED, report.evidenceCoverage.fields[EvidenceField.POWER_MAH])
            val s = sample(GuidedInput(report, null, null))
            val overall = s.evidence.single { it.kind == GuidedEvidenceKind.OVERALL_POWER }
            assertEquals(GuidedValue.Number(actual.toDouble(), "mah"), overall.value)
            assertEquals(EvidenceFieldStatus.PARSED, overall.fieldStatus)
            assertEquals(VerificationEstimate.ACTUAL, overall.estimate)
            assertEquals(3_600_000L, overall.window!!.batteryDurationMillis)
            val result = UsageHeatRuleSet.evaluate(answers, listOf(s))
            assertEquals(overall, result.facts.single { it.id == overall.id })
            assertTrue(payload(s, result).userPrompt.contains("\"number\":${actual.toDouble()},\"unit\":\"mah\""))
            // An overall parse cannot authenticate an unparsed app numeric field injected downstream.
            val appReport = report.copy(apps = listOf(AppPowerEvidence(10123, listOf("com.example.app"), estimatedPowerMah = 99.0)),
                sources = report.sources + ("packages" to DiagnosticSourceResult("packages", DiagnosticSourceStatus.AVAILABLE)),
                evidenceCoverage = EvidenceCoverage(report.evidenceCoverage.fields + (EvidenceField.UID_PACKAGES to EvidenceFieldStatus.PARSED)))
            val app = sample(GuidedInput(appReport, null, null)).evidence.single { it.kind == GuidedEvidenceKind.APP_TOTAL_POWER }
            assertEquals(EvidenceFieldStatus.NOT_PARSED, app.fieldStatus)
            assertNull(app.value)
        }
    }

    @Test fun overallDrainRetainsItsOwnSourceAndTruncationGates() {
        val truncated = sample(GuidedInput(report("0", truncated = true), null, null)).evidence.single()
        assertEquals(EvidenceFieldStatus.TRUNCATED, truncated.fieldStatus)
        assertEquals(GuidedValue.Number(0.0, "mah"), truncated.value)
        val failed = sample(GuidedInput(report("280", status = DiagnosticSourceStatus.PARSE_FAILED), null, null))
        assertTrue(failed.evidence.none { it.value != null })
        assertTrue(failed.gaps.any { it.id == "diagnostic_evidence_unavailable" })
    }

    private fun report(actual: String, status: DiagnosticSourceStatus = DiagnosticSourceStatus.AVAILABLE, truncated: Boolean = false) =
        PowerDiagnosticPipeline.analyze(listOf(RawDiagnosticSection("batterystats", status, """
            Statistics since last charge:
            Time on battery: 1h (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 300, actual drain: $actual
        """.trimIndent(), truncated)), DiagnosticInputSource.BUGREPORT, now)
}
