package com.aegis.apa

import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedDiagnosticCopyTest {
    @Test fun bothLanguagesCoverEveryLocalRuleGapActionAndErrorWithoutShowingOpaqueIds() {
        val ids = listOf(
            "idle.answer", "idle.answer_conflict", "idle.open_observation", "idle.import_report",
            "idle.observe_condition", "idle.verify_background", "idle.end", "guided.end",
            "heat.answer", "heat.answer_conflict", "heat.collect_current", "heat.collect_same_session",
            "heat.observe_condition", "heat.view_system_warning", "heat.end", "heat.charging_use",
            "idle.rough_observation_rate", "idle.background_activity_correlation:s:diagnostic:2",
            "heat.separate_observations:s:thermal", "heat.battery_sensor_only:s:battery_temperature",
            "heat.rough_observation_rate:s:observation_rate",
            "idle_answers_unreviewed", "idle_answers_changed", "idle_conditions_unconfirmed", "idle_conditions_conflict",
            "idle_evidence_conflict", "idle_window_unknown", "idle_app_evidence_incomplete", "idle_observation_rough",
            "idle_quantification_missing", "idle_report_missing", "heat_answers_unreviewed", "heat_answers_changed",
            "heat_conditions_unconfirmed", "heat_conditions_conflict", "heat_evidence_conflict", "heat_sensor_missing",
            "heat_sensor_scope", "heat_reading_invalid", "heat_unit_unknown", "heat_evidence_incomplete",
            "heat_event_unlinked", "heat_observation_rough", "heat_conditions_user_reported", "heat_factors_not_separated",
            "heat_recognized_severe_state", "answer_time_range_reversed", "app_groups_omitted", "report_device_unknown",
            "report_time_unknown", "diagnostic_evidence_unavailable", "thermal_status_unknown",
            "observation_invalid_rate", "observation_missing_battery_level", "observation_unknown_power_state",
            "observation_started_while_charging", "observation_charging_during_observation", "observation_ended_while_charging",
            "observation_continuity_lost", "observation_invalid_time_range", "observation_too_short",
            "observation_battery_increased", "observation_no_measurable_drop", "guided.restart_confirmation_required",
            "guided.invalid_sample_id", "guided.sample_id_conflict", "guided.preview_required", "guided.preview_stale",
            "guided.invalid_replacement", "guided.replacement_required", "duplicate_evidence_id", "invalid_summary",
            "summary_too_large", "guided.collection_failed", "guided.verification_unavailable"
        )
        ids.forEach { id -> AppLanguage.entries.forEach { language ->
            val text = GuidedDiagnosticCopy.text(id, language)
            assertTrue("Missing $id in $language", GuidedDiagnosticCopy.hasCopy(id))
            assertTrue(text.isNotBlank()); assertFalse(text.contains(id))
            if (language == AppLanguage.EN) assertFalse(text.any { it in '\u4e00'..'\u9fff' })
        } }
    }

    @Test fun familyMatchingIsRestrictedAndObservationThermalAndPowerLimitsAreExplicit() {
        assertFalse(GuidedDiagnosticCopy.hasCopy("idle.background_activity_correlation"))
        assertFalse(GuidedDiagnosticCopy.hasCopy("unknown:model:command"))
        assertTrue(GuidedDiagnosticCopy.text("idle.rough_observation_rate", AppLanguage.EN).contains("rough", true))
        assertTrue(GuidedDiagnosticCopy.text("heat.rough_observation_rate:x", AppLanguage.EN).contains("rough", true))
        val thermal = GuidedDiagnosticCopy.text("heat_recognized_severe_state", AppLanguage.EN)
        assertTrue(thermal.contains("reported", true)); assertTrue(thermal.contains("notification", true))
        val action = GuidedDiagnosticCopy.action("idle.verify_background", AppLanguage.EN)
        assertTrue(action.contains("power", true)); assertTrue(action.contains("heat", true))
        listOf("idle.open_observation", "heat.collect_current", "heat.view_system_warning", "heat.observe_condition").forEach {
            val body = GuidedDiagnosticCopy.action(it, AppLanguage.EN)
            listOf("Purpose:", "Prerequisite:", "Stop:", "On return:").forEach { label -> assertTrue(body.contains(label)) }
        }
    }

    @Test fun unavailableTruncatedAndUnparsedCoverageKeepTheirDistinctStatusLabels() {
        mapOf("TIMED_OUT" to "Timed out", "PARSE_FAILED" to "Parse failed", "NOT_PRESENT" to "Not present",
            "SOURCE_UNAVAILABLE" to "Source unavailable", "TRUNCATED" to "Truncated", "NOT_PARSED" to "Not parsed").forEach { (status, expected) ->
            assertEquals(expected, GuidedDiagnosticCopy.status(status, AppLanguage.EN))
            assertFalse(GuidedDiagnosticCopy.status(status, AppLanguage.ZH_CN).contains("未知"))
        }
    }

    @Test fun factRenderingKeepsZeroUnitSourceSeparateTimesAndReportedThermalProvenance() {
        val fact = GuidedEvidence("zero", GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedValue.Number(0.0, "celsius"),
            "battery", DiagnosticSourceStatus.AVAILABLE, EvidenceFieldStatus.PARSED, VerificationEstimate.ACTUAL,
            emptyList(), GuidedDiagnosticFixtures.now, null, null)
        val text = GuidedDiagnosticCopy.fact(fact, AppLanguage.EN)
        assertTrue(text.contains("0.0 °C")); assertTrue(text.contains("Basic battery snapshot"))
        assertTrue(text.contains("Report event time: Unknown")); assertTrue(text.contains("Statistics window: Unknown"))
        val thermal = fact.copy(kind = GuidedEvidenceKind.THERMAL_STATUS, source = "thermalservice", value = GuidedValue.Code("3"))
        val output = GuidedDiagnosticCopy.fact(thermal, AppLanguage.EN)
        assertTrue(output.contains("Reported thermal state (sensor / global provenance unavailable): Severe"))
        assertFalse(output.contains("CPU temperature")); assertFalse(output.contains("notification displayed"))
    }
}
