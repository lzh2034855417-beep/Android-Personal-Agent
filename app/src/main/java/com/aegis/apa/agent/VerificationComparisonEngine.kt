package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.math.BigInteger
import java.time.Duration

enum class VerificationComparisonStatus(val wireId: String) {
    CHANGE_OBSERVED("change_observed"), NO_NUMERIC_CHANGE("no_numeric_change"), NOT_COMPARABLE("not_comparable"), METRIC_UNAVAILABLE("metric_unavailable")
}

enum class VerificationReason(val wireId: String) {
    MISSING_CHANGE("missing_change"), MISSING_RETEST("missing_retest"), LOCAL_SCOPE_UNKNOWN("local_scope_unknown"),
    INPUT_SOURCE_MISMATCH("input_source_mismatch"), DEVICE_CONFLICT("device_conflict"), DEVICE_CONTEXT_MISSING("device_context_missing"),
    OWNERSHIP_UNCONFIRMED("ownership_unconfirmed"), SCENARIO_UNCONFIRMED("scenario_unconfirmed"), OTHER_CHANGES_UNCONFIRMED("other_changes_unconfirmed"),
    NO_CHARGING_UNCONFIRMED("no_charging_unconfirmed"), DUPLICATE_SUMMARY("duplicate_summary"),
    WINDOW_MISSING("window_missing"), WINDOW_INVALID("window_invalid"), WINDOW_OVERLAP("window_overlap"),
    CHANGE_OUTSIDE_WINDOWS("change_outside_windows"), SAME_CUMULATIVE_CYCLE("same_cumulative_cycle"), SAMPLE_TOO_SHORT("sample_too_short"),
    BATTERY_DURATION_MISSING("battery_duration_missing"), BATTERY_DURATION_INVALID("battery_duration_invalid"), BATTERY_DURATION_RATIO("battery_duration_ratio"),
    FIELD_MISSING("field_missing"), FIELD_UNAVAILABLE("field_unavailable"), FIELD_TRUNCATED("field_truncated"), VALUE_INVALID("value_invalid"),
    SOURCE_UNAVAILABLE("source_unavailable"), METRIC_SOURCE_MISMATCH("metric_source_mismatch"), ESTIMATE_MISMATCH("estimate_mismatch"), ESTIMATE_UNKNOWN("estimate_unknown"),
    UNIT_MISMATCH("unit_mismatch"), SCOPE_MISMATCH("scope_mismatch"), SCOPE_UNKNOWN("scope_unknown"), GROUP_CHANGED("group_changed"),
    WINDOW_CHARGING("window_charging"), METRIC_AMBIGUOUS("metric_ambiguous")
}

data class VerificationMetricComparison(
    val key: VerificationMetricKey, val before: VerificationMetric?, val after: VerificationMetric?,
    val status: VerificationComparisonStatus, val reasons: List<VerificationReason>,
    val beforeRate: Double?, val afterRate: Double?, val delta: Double?, val relativeChange: Double?, val rateUnit: String
)

data class VerificationComparison(val ruleVersion: Int, val commonReasons: List<VerificationReason>, val metrics: List<VerificationMetricComparison>)

object VerificationComparisonEngine {
    private const val RULE_VERSION = 1
    private const val MIN_DURATION_MILLIS = 1_800_000L

    fun compare(record: VerificationRecord): VerificationComparison {
        val common = commonReasons(record)
        val beforeGroups = record.baseline.metrics.groupBy { it.key }
        val afterGroups = record.retest?.metrics.orEmpty().groupBy { it.key }
        val keys = (beforeGroups.keys + afterGroups.keys).distinct()
        val metrics = keys.map { key ->
            val before = beforeGroups[key]?.firstOrNull()
            val after = afterGroups[key]?.firstOrNull()
            // Global context is checked before any rate or change can be computed.
            if (common.isNotEmpty()) return@map unavailable(key, before, after, common, VerificationComparisonStatus.NOT_COMPARABLE)
            val reasons = mutableListOf<VerificationReason>()
            if (beforeGroups[key].orEmpty().size > 1 || afterGroups[key].orEmpty().size > 1) reasons += VerificationReason.METRIC_AMBIGUOUS
            if (before == null || after == null) {
                reasons += VerificationReason.FIELD_MISSING
                val otherKeys = if (before == null) beforeGroups.keys else afterGroups.keys
                if (key.packageNames.isNotEmpty() && otherKeys.any {
                    it.kind == key.kind && it.packageNames != key.packageNames && it.packageNames.any(key.packageNames::contains)
                }) reasons += VerificationReason.GROUP_CHANGED
            }
            if (before != null) validateMetric(before, record.baseline, reasons)
            if (after != null) validateMetric(after, record.retest!!, reasons)
            if (before != null && after != null) validatePair(before, after, record.change!!, reasons)
            if (reasons.isNotEmpty()) {
                val missing = reasons.any { it in unavailableReasons }
                return@map unavailable(key, before, after, reasons.distinct(),
                    if (missing) VerificationComparisonStatus.METRIC_UNAVAILABLE else VerificationComparisonStatus.NOT_COMPARABLE)
            }
            val beforeRate = rate(before!!)
            val afterRate = rate(after!!)
            if (!beforeRate.isFinite() || !afterRate.isFinite()) {
                return@map unavailable(key, before, after, listOf(VerificationReason.VALUE_INVALID), VerificationComparisonStatus.METRIC_UNAVAILABLE)
            }
            val delta = afterRate - beforeRate
            val relative = if (beforeRate == 0.0) null else (delta / beforeRate).takeIf { it.isFinite() }
            VerificationMetricComparison(key, before, after,
                if (beforeRate == afterRate) VerificationComparisonStatus.NO_NUMERIC_CHANGE else VerificationComparisonStatus.CHANGE_OBSERVED,
                emptyList(), beforeRate, afterRate, delta, relative, rateUnit(before.unit))
        }
        return VerificationComparison(RULE_VERSION, common, metrics)
    }

    private val unavailableReasons = setOf(VerificationReason.FIELD_MISSING, VerificationReason.FIELD_UNAVAILABLE,
        VerificationReason.FIELD_TRUNCATED, VerificationReason.VALUE_INVALID, VerificationReason.SOURCE_UNAVAILABLE, VerificationReason.METRIC_AMBIGUOUS)

    private fun commonReasons(record: VerificationRecord): List<VerificationReason> = buildList {
        if (record.scopeId.isBlank()) add(VerificationReason.LOCAL_SCOPE_UNKNOWN)
        if (record.change == null) add(VerificationReason.MISSING_CHANGE)
        if (record.retest == null) add(VerificationReason.MISSING_RETEST)
        val before = record.baseline
        val after = record.retest
        val summaries = listOfNotNull(before, after)
        if (after != null) {
            if (before.inputSource != after.inputSource) add(VerificationReason.INPUT_SOURCE_MISMATCH)
            if (before.fingerprint.isNotBlank() && before.fingerprint == after.fingerprint) add(VerificationReason.DUPLICATE_SUMMARY)
            val a = before.context.device
            val b = after.context.device
            if (listOf(a.manufacturer to b.manufacturer, a.modelCode to b.modelCode, a.buildVersion to b.buildVersion)
                    .any { (left, right) -> !left.isNullOrBlank() && !right.isNullOrBlank() && left != right } ||
                a.apiLevel != null && b.apiLevel != null && a.apiLevel != b.apiLevel) add(VerificationReason.DEVICE_CONFLICT)
        }
        if (summaries.any {
            val device = it.context.device
            device.modelCode.isNullOrBlank() || device.apiLevel == null || device.apiLevel <= 0 || device.buildVersion.isNullOrBlank()
        }) add(VerificationReason.DEVICE_CONTEXT_MISSING)
        if (summaries.any {
            val required = if (it.inputSource == DiagnosticInputSource.ROOT) VerificationOwnership.LOCAL_COLLECTION else VerificationOwnership.USER_CONFIRMED
            it.context.device.ownership != required
        }) add(VerificationReason.OWNERSHIP_UNCONFIRMED)
        if (summaries.any { it.context.noCharging != VerificationConfirmation.YES }) add(VerificationReason.NO_CHARGING_UNCONFIRMED)
        record.change?.let {
            if (it.similarScenario != VerificationConfirmation.YES) add(VerificationReason.SCENARIO_UNCONFIRMED)
            if (it.noOtherChanges != VerificationConfirmation.YES) add(VerificationReason.OTHER_CHANGES_UNCONFIRMED)
        }
    }.distinct()

    private fun validateMetric(metric: VerificationMetric, summary: DiagnosticVerificationSummary, reasons: MutableList<VerificationReason>) {
        if (summary.sources[metric.window.source] != DiagnosticSourceStatus.AVAILABLE) reasons += VerificationReason.SOURCE_UNAVAILABLE
        if (metric.window.source != expectedSource(metric.key.kind)) reasons += VerificationReason.METRIC_SOURCE_MISMATCH
        when (metric.status) {
            EvidenceFieldStatus.PARSED -> Unit
            EvidenceFieldStatus.TRUNCATED -> reasons.add(VerificationReason.FIELD_TRUNCATED)
            else -> reasons.add(VerificationReason.FIELD_UNAVAILABLE)
        }
        if (metric.value == null) reasons += VerificationReason.FIELD_MISSING
        else if (!metric.value.isFinite() || metric.value < 0) reasons += VerificationReason.VALUE_INVALID
        if (metric.unit != expectedUnit(metric.key.kind)) reasons += VerificationReason.UNIT_MISMATCH
        if (metric.estimate == VerificationEstimate.UNKNOWN) reasons += VerificationReason.ESTIMATE_UNKNOWN
        val window = metric.window
        if (window.scope == VerificationMetricScope.UNKNOWN) reasons += VerificationReason.SCOPE_UNKNOWN
        if (window.charging != VerificationConfirmation.NO) reasons += VerificationReason.WINDOW_CHARGING
        val duration = windowDuration(window)
        if (window.startedAt == null || window.endedAt == null) reasons += VerificationReason.WINDOW_MISSING
        else if (duration == null || duration <= 0) reasons += VerificationReason.WINDOW_INVALID
        else if (duration < MIN_DURATION_MILLIS) reasons += VerificationReason.SAMPLE_TOO_SHORT
        val battery = window.batteryDurationMillis
        if (battery == null) reasons += VerificationReason.BATTERY_DURATION_MISSING
        else {
            if (battery <= 0 || duration != null && battery > duration) reasons += VerificationReason.BATTERY_DURATION_INVALID
            if (battery < MIN_DURATION_MILLIS) reasons += VerificationReason.SAMPLE_TOO_SHORT
        }
    }

    private fun validatePair(before: VerificationMetric, after: VerificationMetric, change: VerificationChange, reasons: MutableList<VerificationReason>) {
        if (before.unit != after.unit) reasons += VerificationReason.UNIT_MISMATCH
        if (before.estimate != after.estimate) reasons += VerificationReason.ESTIMATE_MISMATCH
        val a = before.window
        val b = after.window
        if (a.source != b.source) reasons += VerificationReason.METRIC_SOURCE_MISMATCH
        if (a.scope != b.scope) reasons += VerificationReason.SCOPE_MISMATCH
        if (!a.resetId.isNullOrBlank() && a.resetId == b.resetId &&
            (a.scope != VerificationMetricScope.EXPLICIT_WINDOW || b.scope != VerificationMetricScope.EXPLICIT_WINDOW)) reasons += VerificationReason.SAME_CUMULATIVE_CYCLE
        if (a.startedAt != null && a.endedAt != null && b.startedAt != null && b.endedAt != null) {
            if (a.startedAt < b.endedAt && b.startedAt < a.endedAt) reasons += VerificationReason.WINDOW_OVERLAP
            if (a.endedAt > change.confirmedAt || b.startedAt < change.confirmedAt) reasons += VerificationReason.CHANGE_OUTSIDE_WINDOWS
        }
        val firstBattery = a.batteryDurationMillis
        val secondBattery = b.batteryDurationMillis
        if (firstBattery != null && firstBattery > 0 && secondBattery != null && secondBattery > 0) {
            // Cross multiplication keeps the inclusive boundaries exact without overflowing Long.
            val first = BigInteger.valueOf(firstBattery)
            val second = BigInteger.valueOf(secondBattery)
            if (second * BigInteger.valueOf(5) < first * BigInteger.valueOf(4) ||
                second * BigInteger.valueOf(4) > first * BigInteger.valueOf(5)) reasons += VerificationReason.BATTERY_DURATION_RATIO
        }
    }

    private fun windowDuration(window: MetricWindow): Long? {
        val start = window.startedAt ?: return null
        val end = window.endedAt ?: return null
        return try { Duration.between(start, end).toMillis() } catch (_: ArithmeticException) { null }
    }

    private fun rate(metric: VerificationMetric): Double {
        val value = metric.value!!
        val quantity = if (metric.unit == VerificationUnit.MILLIS) value / 60_000.0 else value
        return quantity / (metric.window.batteryDurationMillis!! / 3_600_000.0)
    }

    private fun unavailable(key: VerificationMetricKey, before: VerificationMetric?, after: VerificationMetric?, reasons: List<VerificationReason>, status: VerificationComparisonStatus) =
        VerificationMetricComparison(key, before, after, status, reasons, null, null, null, null, rateUnit((before ?: after)!!.unit))

    private fun rateUnit(unit: VerificationUnit) = when (unit) {
        VerificationUnit.MAH -> "mAh/battery-hour"
        VerificationUnit.MILLIS -> "minutes/battery-hour"
        VerificationUnit.COUNT -> "count/battery-hour"
    }

    private fun expectedSource(kind: VerificationMetricKind) = when (kind) {
        VerificationMetricKind.WAKEUP_COUNT, VerificationMetricKind.ALARM_COUNT -> "alarm"
        VerificationMetricKind.JOB_COUNT -> "jobscheduler"
        else -> "batterystats"
    }

    private fun expectedUnit(kind: VerificationMetricKind) = when (kind) {
        VerificationMetricKind.OVERALL_POWER, VerificationMetricKind.APP_TOTAL_POWER, VerificationMetricKind.APP_FOREGROUND_POWER, VerificationMetricKind.APP_BACKGROUND_POWER -> VerificationUnit.MAH
        VerificationMetricKind.FOREGROUND_DURATION, VerificationMetricKind.BACKGROUND_DURATION, VerificationMetricKind.WAKELOCK_DURATION -> VerificationUnit.MILLIS
        else -> VerificationUnit.COUNT
    }
}
