package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import com.aegis.apa.tool.PowerDiagnosticParser
import com.aegis.apa.tool.RawDiagnosticSection
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UsageHeatRuleSetTest {
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private val answers = GuidedAnswers(stillHot = VerificationConfirmation.YES,
        charging = VerificationConfirmation.NO, activity = GuidedActivity.GAME, reviewed = true)

    private fun evidence(id: String, kind: GuidedEvidenceKind, value: GuidedValue?,
        source: String = when (kind) {
            GuidedEvidenceKind.THERMAL_STATUS -> "thermalservice"
            GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.CHARGING -> "battery"
            GuidedEvidenceKind.OBSERVATION_RATE -> "observation"
            else -> "batterystats"
        }, time: Instant = now) = GuidedEvidence(id, kind, value, source,
        DiagnosticSourceStatus.AVAILABLE, EvidenceFieldStatus.PARSED, VerificationEstimate.ACTUAL,
        emptyList(), time, null, null)

    private fun temp(id: String = "one:temperature", number: Double = 40.0, time: Instant = now) =
        evidence(id, GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedValue.Number(number, "celsius"), time = time)
    private fun thermal(code: String, id: String = "one:thermal") =
        evidence(id, GuidedEvidenceKind.THERMAL_STATUS, GuidedValue.Code(code))
    private fun sample(vararg items: GuidedEvidence, id: String = "one", context: GuidedAnswers = answers) =
        GuidedSample(id, items.toList(), context)
    private fun evaluate(context: GuidedAnswers = answers, vararg samples: GuidedSample) =
        UsageHeatRuleSet.evaluate(context, samples.toList()).also(::checkReferences)

    private fun checkReferences(result: GuidedAssessment) {
        val facts = result.facts.map { it.id }.toSet()
        val gaps = result.gaps.map { it.id }.toSet()
        val candidates = result.candidates.map { it.ruleId }.toSet()
        assertEquals(result.facts.size, facts.size)
        assertEquals(result.gaps.size, gaps.size)
        assertEquals(result.candidates.size, candidates.size)
        assertTrue(result.candidates.size <= 3)
        result.gaps.forEach { assertTrue(facts.containsAll(it.evidenceIds)) }
        result.candidates.forEach {
            assertTrue(facts.containsAll(it.evidenceIds)); assertTrue(gaps.containsAll(it.gapIds))
        }
        assertTrue((facts + gaps + candidates).containsAll(result.primaryAction.reasonIds))
    }

    // Catches treating reviewed UNKNOWN as an unanswered form and collecting historical sensations.
    @Test fun touchOnlyBranchesRespectExplicitReviewAndWhetherHeatIsStillHappening() {
        val cases = listOf(
            answers.copy(reviewed = false) to GuidedActionKind.ANSWER,
            answers to GuidedActionKind.COLLECT,
            answers.copy(stillHot = VerificationConfirmation.NO) to GuidedActionKind.END,
            GuidedAnswers(reviewed = true) to GuidedActionKind.END)
        cases.forEach { (context, action) ->
            val result = evaluate(context)
            assertEquals(action, result.primaryAction.kind)
            assertTrue(result.facts.isEmpty())
            assertTrue(result.gaps.any { it.id == "heat_sensor_missing" })
        }
    }

    // Catches absent/unsafe readings being silently converted to zero or a universal temperature cutoff.
    @Test fun batterySensorRetainsZeroAndNegativeValuesWithoutTemperatureWarningThresholds() {
        listOf(0.0, -5.0, 40.0, 1000.0).forEach { number ->
            val result = evaluate(answers, sample(temp(number = number)))
            assertEquals(GuidedValue.Number(number, "celsius"), result.facts.single().value)
            assertEquals(GuidedActionKind.OBSERVE_CONDITION, result.primaryAction.kind)
            assertTrue(result.candidates.any { it.ruleId.startsWith("heat.battery_sensor_only:") })
            assertTrue(result.gaps.any { it.id == "heat_sensor_scope" })
        }
    }

    @Test fun nonfiniteAndUnknownUnitsRemainUnknownAndRequestOnlyAnActiveManualRefresh() {
        val values = listOf(GuidedValue.Number(Double.NaN, "celsius"),
            GuidedValue.Number(Double.POSITIVE_INFINITY, "celsius"),
            GuidedValue.Number(Double.NEGATIVE_INFINITY, "celsius"), GuidedValue.Number(42.0, ""),
            GuidedValue.Number(42.0, "fahrenheit"))
        values.forEach { value ->
            val result = evaluate(answers, sample(temp().copy(value = value)))
            assertNull(result.facts.single().value)
            assertEquals(VerificationEstimate.UNKNOWN, result.facts.single().estimate)
            assertEquals(GuidedActionKind.COLLECT, result.primaryAction.kind)
            assertTrue(result.candidates.isEmpty())
            assertTrue(result.gaps.any { it.id in setOf("heat_reading_invalid", "heat_unit_unknown") })
        }
    }

    // Official constants: https://developer.android.com/reference/android/os/PowerManager#THERMAL_STATUS_NONE
    // Local guidance marks the four named severe states; it does not prove a notification was displayed.
    @Test fun explicitOfficialThermalStatesDriveGuidanceButUnknownCodesNeverUseNumericOrdering() {
        val cases = listOf("0" to false, "1" to false, "2" to false, "3" to true,
            "4" to true, "5" to true, "6" to true, "7" to false, "99" to false,
            "-1" to false, "03" to false, "hot" to false)
        cases.forEach { (code, warning) ->
            val result = evaluate(answers, sample(thermal(code), temp()))
            assertEquals("code=$code", warning, result.primaryAction.kind == GuidedActionKind.VIEW_SYSTEM_WARNING)
            if (code !in setOf("0", "1", "2", "3", "4", "5", "6")) {
                assertNull(result.facts.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
                assertTrue(result.gaps.any { it.id == "thermal_status_unknown" })
            }
        }
    }

    @Test fun knownSevereStateOverridesUnreviewedTimeConflictAndLaterLowReading() {
        val context = answers.copy(startedAt = now, endedAt = now.minusSeconds(1), reviewed = false)
        val result = evaluate(context, sample(thermal("4")),
            sample(temp("two:temperature", 20.0, now.plusSeconds(3600)), id = "two"))
        assertEquals("heat.view_system_warning", result.primaryAction.id)
        assertEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, result.primaryAction.kind)
        assertEquals(listOf("heat_recognized_severe_state"), result.primaryAction.reasonIds)
        assertEquals(GuidedValue.Code("4"), result.facts.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
    }

    @Test fun thermalProvenanceAndUnavailableStatusCannotAuthorizeWarnings() {
        val bad = listOf(thermal("6").copy(source = "battery"),
            thermal("6").copy(sourceStatus = DiagnosticSourceStatus.UNSUPPORTED),
            thermal("6").copy(fieldStatus = EvidenceFieldStatus.NOT_PARSED),
            thermal("6").copy(estimate = VerificationEstimate.ESTIMATED),
            thermal("6").copy(packages = listOf("test.app")))
        bad.forEach {
            val result = evaluate(answers, sample(it, temp()))
            assertNull(result.facts.single { item -> item.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
            assertNotEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, result.primaryAction.kind)
        }
        val contradictory = sample(thermal("6"), temp()).copy(sources = mapOf("thermalservice" to DiagnosticSourceStatus.PARSE_FAILED))
        val result = evaluate(answers, contradictory)
        assertEquals("heat.answer_conflict", result.primaryAction.id)
        assertNull(result.facts.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
    }

    @Test fun truncatedExplicitThermalStateStillOffersConservativeSystemGuidance() {
        val item = thermal("3").copy(sourceStatus = DiagnosticSourceStatus.TRUNCATED, fieldStatus = EvidenceFieldStatus.TRUNCATED)
        val result = evaluate(answers, sample(item))
        assertEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, result.primaryAction.kind)
        assertEquals(DiagnosticSourceStatus.TRUNCATED, result.facts.single().sourceStatus)
        assertTrue(result.gaps.any { it.id == "heat_evidence_incomplete" })
    }

    @Test fun parserAdapterThermalServiceUsesPlatformSeverityProvenance() {
        // AOSP source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/core/java/com/android/server/power/ThermalManagerService.java
        listOf("Current Thermal Status: 3", "Temperature{mValue=42.0, mType=3, mName=skin, mStatus=4}").forEach { text ->
            val report = PowerDiagnosticParser.parse(listOf(RawDiagnosticSection("thermalservice", DiagnosticSourceStatus.AVAILABLE, text)), now)
            val adapted = GuidedEvidenceAdapter.adapt(GuidedInput(report, null, null), "raw", answers)
            assertTrue(adapted is GuidedSampleResult.Ready)
            val result = evaluate(answers, (adapted as GuidedSampleResult.Ready).sample)
            assertEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, result.primaryAction.kind)
            assertEquals("thermalservice", result.facts.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.source)
        }
    }

    @Test fun currentTemperatureAndHistoricalReportStaySeparateEvenWhenImportTimesMatch() {
        val power = evidence("one:power", GuidedEvidenceKind.OVERALL_POWER, GuidedValue.Number(100.0, "mah"))
            .copy(reportedAt = now.minusSeconds(86400))
        val result = evaluate(answers, sample(temp(number = 20.0), power))
        assertEquals(2, result.facts.size)
        assertEquals(now.minusSeconds(86400), result.facts.single { it.id == "one:power" }.reportedAt)
        assertTrue(result.gaps.any { it.id == "heat_event_unlinked" })
        assertTrue(result.candidates.any { it.ruleId.startsWith("heat.separate_observations:") })
        assertTrue(result.candidates.none { it.ruleId.contains("app") || it.ruleId.contains("cause") })
        assertEquals(GuidedActionKind.COLLECT, result.primaryAction.kind)
    }

    @Test fun chargingUseIsTwoDeclaredFactorsAndOnlySuggestsVoluntaryConditionObservation() {
        val context = answers.copy(charging = VerificationConfirmation.YES, activity = GuidedActivity.CHARGING_USE)
        val result = evaluate(context, sample(temp(), context = context))
        assertEquals(GuidedActionKind.OBSERVE_CONDITION, result.primaryAction.kind)
        assertTrue(result.candidates.any { it.ruleId == "heat.charging_use" })
        assertTrue(result.gaps.any { it.id == "heat_conditions_user_reported" })
        assertTrue(result.gaps.any { it.id == "heat_factors_not_separated" })
    }

    @Test fun endedTaskReadingsRemainTwoFactsWithoutImprovementOrThermalVerification() {
        val ended = answers.copy(stillHot = VerificationConfirmation.NO)
        val result = evaluate(ended, sample(temp("first:temperature", 44.0), id = "first"),
            sample(temp("second:temperature", 31.0, now.plusSeconds(600)), id = "second", context = ended))
        assertEquals(listOf(44.0, 31.0), result.facts.map { (it.value as GuidedValue.Number).value })
        assertEquals(listOf(now, now.plusSeconds(600)), result.facts.map { it.collectedAt })
        assertEquals(GuidedActionKind.END, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "heat_event_unlinked" })
        assertTrue(result.candidates.none { it.ruleId.contains("improvement") || it.ruleId.contains("effective") })
    }

    @Test fun duplicateReferencesAndSourceContradictionsChooseConflictBeforeMissingEvidence() {
        val duplicates = evaluate(answers.copy(reviewed = false), sample(temp()), sample(temp(), id = "two"))
        assertEquals("heat.answer_conflict", duplicates.primaryAction.id)
        assertTrue(duplicates.facts.isEmpty())
        val conflict = evaluate(answers, sample(temp()).copy(sources = mapOf("battery" to DiagnosticSourceStatus.UNSUPPORTED)))
        assertEquals("heat.answer_conflict", conflict.primaryAction.id)
        assertNull(conflict.facts.single().value)
        val warning = evaluate(answers, sample(temp()).copy(sources = mapOf("battery" to DiagnosticSourceStatus.UNSUPPORTED)),
            sample(thermal("5", "two:thermal"), id = "two"))
        assertEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, warning.primaryAction.kind)
    }

    @Test fun reviewedUncertaintyAndChangedAnswersDoNotCauseUnansweredFormLoops() {
        val context = GuidedAnswers(reviewed = true)
        val result = evaluate(context, sample(temp()))
        assertEquals(GuidedActionKind.END, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "heat_conditions_unconfirmed" })
        assertTrue(result.gaps.any { it.id == "heat_answers_changed" })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind)
    }

    @Test fun unsupportedSensorsEndWithoutPromisingRepeatedRefreshCanRepairCapability() {
        val result = evaluate(answers, sample().copy(sources = mapOf("battery" to DiagnosticSourceStatus.UNSUPPORTED,
            "thermalservice" to DiagnosticSourceStatus.PERMISSION_DENIED)))
        assertEquals(GuidedActionKind.END, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "heat_sensor_missing" })
    }

    @Test fun allRatesRemainRoughAndCannotEstablishTemperatureOrHeatAttribution() {
        val rate = evidence("one:rate", GuidedEvidenceKind.OBSERVATION_RATE, GuidedValue.Number(2.0, "percent_per_hour"))
            .copy(window = MetricWindow("observation", VerificationMetricScope.EXPLICIT_WINDOW,
                now.minusSeconds(3600), now, 3600000, null, VerificationConfirmation.NO))
        val result = evaluate(answers, sample(rate))
        assertEquals(GuidedActionKind.COLLECT, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "heat_observation_rough" && it.evidenceIds == listOf("one:rate") })
        assertTrue(result.candidates.any { it.ruleId.startsWith("heat.rough_observation_rate:") &&
            "heat_observation_rough" in it.gapIds })
    }

    @Test fun candidateCapAndTraversalStayDeterministicAndScenarioActionsDiffer() {
        val context = answers.copy(charging = VerificationConfirmation.YES)
        val one = sample(temp(), thermal("2"), context = context)
        val two = sample(temp("two:temperature", 35.0), thermal("1", "two:thermal"), id = "two", context = context)
        val result = evaluate(context, one, two)
        assertEquals(result, evaluate(context, two.copy(evidence = two.evidence.reversed()), one.copy(evidence = one.evidence.reversed())))
        assertEquals(3, result.candidates.size)
        assertNotEquals(IdleDrainRuleSet.evaluate(context, listOf(one)).primaryAction.kind,
            evaluate(context, one).primaryAction.kind)
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind)
    }

    @Test fun chargingUseWithExplicitNoChargingIsAnAnswerConflictButLaterChargingDoesNotRewriteHistory() {
        val contradiction = answers.copy(activity = GuidedActivity.CHARGING_USE, charging = VerificationConfirmation.NO)
        assertEquals("heat.answer_conflict", evaluate(contradiction, sample(temp(), context = contradiction)).primaryAction.id)
        val historical = answers.copy(stillHot = VerificationConfirmation.NO, endedAt = now.minusSeconds(3600))
        val laterCharging = evidence("one:charging", GuidedEvidenceKind.CHARGING, GuidedValue.Code("yes"))
        val result = evaluate(historical, sample(temp(), laterCharging, context = historical))
        assertEquals(GuidedActionKind.END, result.primaryAction.kind)
        assertTrue(result.gaps.none { it.id == "heat_conditions_conflict" })
        assertEquals(GuidedValue.Code("yes"), result.facts.single { it.kind == GuidedEvidenceKind.CHARGING }.value)
    }

    @Test fun malformedRateWindowsCannotCreateUsableRateContext() {
        val rate = evidence("one:rate", GuidedEvidenceKind.OBSERVATION_RATE, GuidedValue.Number(2.0, "percent_per_hour"))
        val windows = listOf(null, MetricWindow("observation", VerificationMetricScope.EXPLICIT_WINDOW,
            now, now.minusSeconds(60), 60000, null, VerificationConfirmation.NO),
            MetricWindow("observation", VerificationMetricScope.EXPLICIT_WINDOW,
                now.minusSeconds(60), now, 0, null, VerificationConfirmation.NO))
        windows.forEach { window ->
            val result = evaluate(answers, sample(rate.copy(window = window)))
            assertNull(result.facts.single().value)
            assertTrue(result.candidates.isEmpty())
            assertTrue(result.gaps.any { it.id == "heat_reading_invalid" })
        }
    }
}
