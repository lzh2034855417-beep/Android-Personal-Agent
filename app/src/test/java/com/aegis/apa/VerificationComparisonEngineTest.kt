package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class VerificationComparisonEngineTest {
    // These tests catch unsafe gates, wrong denominators, zero-filling, and package identity mistakes.
    private val record = VerificationFixtures.record()
    private fun metric(record: VerificationRecord = this.record) = VerificationComparisonEngine.compare(record).metrics.first()
    private fun replace(before: Boolean = false, transform: (VerificationMetric) -> VerificationMetric): VerificationRecord {
        val summary = if (before) record.baseline else record.retest!!
        val changed = summary.copy(metrics = summary.metrics.map(transform))
        return if (before) record.copy(baseline = changed) else record.copy(retest = changed)
    }
    private fun blocked(result: VerificationMetricComparison, reason: VerificationReason) {
        assertTrue("Expected ${reason.wireId}: ${result.reasons}", reason in result.reasons)
        assertNull(result.beforeRate); assertNull(result.afterRate); assertNull(result.delta); assertNull(result.relativeChange)
    }

    @Test fun appPowerUsesBatteryHoursAndUnroundedDelta() {
        val result = metric()
        assertEquals(100.0, result.beforeRate!!, 0.000001)
        assertEquals(80.0, result.afterRate!!, 0.000001)
        assertEquals(-20.0, result.delta!!, 0.000001)
        assertEquals(-0.2, result.relativeChange!!, 0.000001)
        assertEquals(VerificationComparisonStatus.CHANGE_OBSERVED, result.status)
        assertEquals("mAh/battery-hour", result.rateUnit)
        assertSame(record.baseline.metrics.first(), result.before)
        assertSame(record.retest!!.metrics.first(), result.after)
        assertEquals(1, VerificationComparisonEngine.compare(record.copy(ruleVersion = 99)).ruleVersion)
    }

    @Test fun overallActualPowerIsASeparateMetricAndTarget() {
        fun overall(summary: DiagnosticVerificationSummary) = summary.copy(metrics = summary.metrics.map {
            it.copy(key = VerificationMetricKey(VerificationMetricKind.OVERALL_POWER, emptyList()), estimate = VerificationEstimate.ACTUAL)
        })
        val result = metric(record.copy(target = VerificationTarget.Overall, baseline = overall(record.baseline), retest = overall(record.retest!!)))
        assertEquals(VerificationMetricKind.OVERALL_POWER, result.key.kind)
        assertEquals(100.0, result.beforeRate!!, 0.0)
        assertEquals(VerificationEstimate.ACTUAL, result.before!!.estimate)
    }

    @Test fun commonContextFailuresBlockEveryMetricBeforeArithmetic() {
        val retest = record.retest!!
        val device = retest.context.device
        val cases = listOf(
            record.copy(change = null) to VerificationReason.MISSING_CHANGE,
            record.copy(retest = null) to VerificationReason.MISSING_RETEST,
            record.copy(scopeId = " ") to VerificationReason.LOCAL_SCOPE_UNKNOWN,
            record.copy(retest = retest.copy(inputSource = DiagnosticInputSource.ROOT)) to VerificationReason.INPUT_SOURCE_MISMATCH,
            record.copy(retest = retest.copy(fingerprint = record.baseline.fingerprint)) to VerificationReason.DUPLICATE_SUMMARY,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(modelCode = "other")))) to VerificationReason.DEVICE_CONFLICT,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(apiLevel = 35)))) to VerificationReason.DEVICE_CONFLICT,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(buildVersion = "other")))) to VerificationReason.DEVICE_CONFLICT,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(manufacturer = "other")))) to VerificationReason.DEVICE_CONFLICT,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(modelCode = null)))) to VerificationReason.DEVICE_CONTEXT_MISSING,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(apiLevel = null)))) to VerificationReason.DEVICE_CONTEXT_MISSING,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(buildVersion = null)))) to VerificationReason.DEVICE_CONTEXT_MISSING,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(ownership = VerificationOwnership.UNKNOWN)))) to VerificationReason.OWNERSHIP_UNCONFIRMED,
            record.copy(retest = retest.copy(context = retest.context.copy(device = device.copy(ownership = VerificationOwnership.LOCAL_COLLECTION)))) to VerificationReason.OWNERSHIP_UNCONFIRMED
        )
        cases.forEach { (input, reason) ->
            val result = VerificationComparisonEngine.compare(input)
            assertTrue(reason.wireId, reason in result.commonReasons)
            result.metrics.forEach { blocked(it, reason); assertEquals(VerificationComparisonStatus.NOT_COMPARABLE, it.status) }
        }
    }

    @Test fun noOrUnknownUserConfirmationsCannotBeOverriddenBySameScenario() {
        for (value in listOf(VerificationConfirmation.NO, VerificationConfirmation.UNKNOWN)) {
            val cases = listOf(
                record.copy(change = record.change!!.copy(similarScenario = value)) to VerificationReason.SCENARIO_UNCONFIRMED,
                record.copy(change = record.change!!.copy(noOtherChanges = value)) to VerificationReason.OTHER_CHANGES_UNCONFIRMED,
                record.copy(baseline = record.baseline.copy(context = record.baseline.context.copy(noCharging = value))) to VerificationReason.NO_CHARGING_UNCONFIRMED,
                record.copy(retest = record.retest!!.copy(context = record.retest!!.context.copy(noCharging = value))) to VerificationReason.NO_CHARGING_UNCONFIRMED
            )
            cases.forEach { (input, reason) -> blocked(metric(input), reason) }
        }
    }

    @Test fun rootRequiresLocalCollectionOwnershipOnBothSides() {
        fun root(summary: DiagnosticVerificationSummary, ownership: VerificationOwnership) = summary.copy(
            inputSource = DiagnosticInputSource.ROOT, context = summary.context.copy(device = summary.context.device.copy(ownership = ownership)))
        val input = record.copy(baseline = root(record.baseline, VerificationOwnership.LOCAL_COLLECTION), retest = root(record.retest!!, VerificationOwnership.LOCAL_COLLECTION))
        assertNotNull(metric(input).delta)
        blocked(metric(input.copy(baseline = root(record.baseline, VerificationOwnership.USER_CONFIRMED))), VerificationReason.OWNERSHIP_UNCONFIRMED)
    }

    @Test fun missingAndMalformedWindowsNeverBorrowGeneratedOrReportedTime() {
        for (before in listOf(true, false)) {
            val cases: List<Pair<(MetricWindow) -> MetricWindow, VerificationReason>> = listOf(
                { w: MetricWindow -> w.copy(startedAt = null) } to VerificationReason.WINDOW_MISSING,
                { w: MetricWindow -> w.copy(endedAt = null) } to VerificationReason.WINDOW_MISSING,
                { w: MetricWindow -> w.copy(endedAt = w.startedAt) } to VerificationReason.WINDOW_INVALID,
                { w: MetricWindow -> w.copy(endedAt = w.startedAt!!.minusSeconds(1)) } to VerificationReason.WINDOW_INVALID,
                { w: MetricWindow -> w.copy(startedAt = Instant.MIN, endedAt = Instant.MAX) } to VerificationReason.WINDOW_INVALID,
                { w: MetricWindow -> w.copy(batteryDurationMillis = null) } to VerificationReason.BATTERY_DURATION_MISSING,
                { w: MetricWindow -> w.copy(batteryDurationMillis = -1) } to VerificationReason.BATTERY_DURATION_INVALID,
                { w: MetricWindow -> w.copy(batteryDurationMillis = Long.MAX_VALUE) } to VerificationReason.BATTERY_DURATION_INVALID,
                { w: MetricWindow -> w.copy(batteryDurationMillis = 3_600_001) } to VerificationReason.BATTERY_DURATION_INVALID
            )
            cases.forEach { (change, reason) -> blocked(metric(replace(before) { it.copy(window = change(it.window)) }), reason) }
        }
    }

    @Test fun timingRejectsOverlapWrongOrderAndSameCumulativeCycle() {
        blocked(metric(replace { it.copy(window = it.window.copy(startedAt = VerificationFixtures.start)) }), VerificationReason.WINDOW_OVERLAP)
        blocked(metric(record.copy(change = record.change!!.copy(confirmedAt = VerificationFixtures.end.minusSeconds(1)))), VerificationReason.CHANGE_OUTSIDE_WINDOWS)
        blocked(metric(record.copy(change = record.change!!.copy(confirmedAt = record.retest!!.metrics.first().window.startedAt!!.plusSeconds(1)))), VerificationReason.CHANGE_OUTSIDE_WINDOWS)
        fun cumulative(summary: DiagnosticVerificationSummary) = summary.copy(metrics = summary.metrics.map { it.copy(window = it.window.copy(scope = VerificationMetricScope.SINCE_LAST_CHARGE, resetId = "cycle-1")) })
        blocked(metric(record.copy(baseline = cumulative(record.baseline), retest = cumulative(record.retest!!))), VerificationReason.SAME_CUMULATIVE_CYCLE)
        val boundary = record.copy(change = record.change!!.copy(confirmedAt = VerificationFixtures.end), retest = record.retest!!.copy(metrics = record.retest!!.metrics.map { it.copy(window = VerificationFixtures.window(startedAt = VerificationFixtures.end)) }))
        assertNotNull(metric(boundary).delta)
    }

    @Test fun thirtyMinuteThresholdIsInclusiveForWindowAndBatterySeparately() {
        for (before in listOf(true, false)) {
            blocked(metric(replace(before) { it.copy(window = it.window.copy(endedAt = it.window.startedAt!!.plusMillis(1_799_999), batteryDurationMillis = 1_799_999)) }), VerificationReason.SAMPLE_TOO_SHORT)
            blocked(metric(replace(before) { it.copy(window = it.window.copy(batteryDurationMillis = 1_799_999)) }), VerificationReason.SAMPLE_TOO_SHORT)
        }
        fun short(summary: DiagnosticVerificationSummary) = summary.copy(metrics = summary.metrics.map { it.copy(window = it.window.copy(endedAt = it.window.startedAt!!.plusMillis(1_800_000), batteryDurationMillis = 1_800_000)) })
        val result = metric(record.copy(baseline = short(record.baseline), retest = short(record.retest!!)))
        assertEquals(200.0, result.beforeRate!!, 0.0)
        assertEquals(160.0, result.afterRate!!, 0.0)
    }

    @Test fun batteryDurationRatioIncludesBothLimitsAndRejectsAdjacentValues() {
        for ((duration, allowed) in listOf(2_880_000L to true, 4_500_000L to true, 2_879_999L to false, 4_500_001L to false)) {
            val result = metric(replace { it.copy(window = it.window.copy(endedAt = it.window.startedAt!!.plusSeconds(5400), batteryDurationMillis = duration)) })
            if (allowed) assertNotNull("duration=$duration", result.delta) else blocked(result, VerificationReason.BATTERY_DURATION_RATIO)
        }
    }

    @Test fun numericAndFieldFailuresPreserveOriginalValues() {
        for (before in listOf(true, false)) {
            for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                val result = metric(replace(before) { it.copy(value = value) })
                blocked(result, VerificationReason.VALUE_INVALID)
                assertEquals(value, (if (before) result.before else result.after)!!.value)
            }
            blocked(metric(replace(before) { it.copy(value = null) }), VerificationReason.FIELD_MISSING)
            for (status in EvidenceFieldStatus.entries.filter { it != EvidenceFieldStatus.PARSED }) {
                blocked(metric(replace(before) { it.copy(status = status) }), if (status == EvidenceFieldStatus.TRUNCATED) VerificationReason.FIELD_TRUNCATED else VerificationReason.FIELD_UNAVAILABLE)
            }
            for (status in DiagnosticSourceStatus.entries.filter { it != DiagnosticSourceStatus.AVAILABLE }) {
                val summary = if (before) record.baseline else record.retest!!
                val changed = summary.copy(sources = mapOf("batterystats" to status))
                blocked(metric(if (before) record.copy(baseline = changed) else record.copy(retest = changed)), VerificationReason.SOURCE_UNAVAILABLE)
            }
        }
    }

    @Test fun semanticAndSourceFailuresAreIndependent() {
        val cases: List<Pair<(VerificationMetric) -> VerificationMetric, VerificationReason>> = listOf(
            { m: VerificationMetric -> m.copy(unit = VerificationUnit.COUNT) } to VerificationReason.UNIT_MISMATCH,
            { m: VerificationMetric -> m.copy(estimate = VerificationEstimate.ACTUAL) } to VerificationReason.ESTIMATE_MISMATCH,
            { m: VerificationMetric -> m.copy(estimate = VerificationEstimate.UNKNOWN) } to VerificationReason.ESTIMATE_UNKNOWN,
            { m: VerificationMetric -> m.copy(window = m.window.copy(scope = VerificationMetricScope.UNKNOWN)) } to VerificationReason.SCOPE_UNKNOWN,
            { m: VerificationMetric -> m.copy(window = m.window.copy(scope = VerificationMetricScope.SINCE_LAST_CHARGE)) } to VerificationReason.SCOPE_MISMATCH,
            { m: VerificationMetric -> m.copy(window = m.window.copy(source = "alarm")) } to VerificationReason.METRIC_SOURCE_MISMATCH,
            { m: VerificationMetric -> m.copy(window = m.window.copy(charging = VerificationConfirmation.YES)) } to VerificationReason.WINDOW_CHARGING,
            { m: VerificationMetric -> m.copy(window = m.window.copy(charging = VerificationConfirmation.UNKNOWN)) } to VerificationReason.WINDOW_CHARGING
        )
        cases.forEach { (transform, reason) -> blocked(metric(replace(transform = transform)), reason) }
    }

    @Test fun aMissingMetricDoesNotTurnIntoZeroOrBlockAnotherMetric() {
        val extra = record.baseline.metrics.first().copy(key = VerificationMetricKey(VerificationMetricKind.ALARM_COUNT, listOf("com.example.app")), value = 12.0, unit = VerificationUnit.COUNT, estimate = VerificationEstimate.ACTUAL, window = VerificationFixtures.window("alarm"))
        val input = record.copy(baseline = record.baseline.copy(metrics = record.baseline.metrics + extra))
        val results = VerificationComparisonEngine.compare(input).metrics
        assertEquals(-20.0, results.first { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }.delta!!, 0.0)
        val missing = results.first { it.key.kind == VerificationMetricKind.ALARM_COUNT }
        blocked(missing, VerificationReason.FIELD_MISSING)
        assertEquals(VerificationComparisonStatus.METRIC_UNAVAILABLE, missing.status)
        assertNull(missing.after)
        val bad = extra.copy(value = null)
        val after = bad.copy(window = VerificationFixtures.window("alarm", record.retest!!.metrics.first().window.startedAt!!))
        val independent = input.copy(retest = record.retest!!.copy(sources = record.retest!!.sources + ("alarm" to DiagnosticSourceStatus.AVAILABLE), metrics = record.retest!!.metrics + after), baseline = input.baseline.copy(sources = input.baseline.sources + ("alarm" to DiagnosticSourceStatus.AVAILABLE)))
        val independentResult = VerificationComparisonEngine.compare(independent)
        assertEquals(-20.0, independentResult.metrics.first().delta!!, 0.0)
        blocked(independentResult.metrics.first { it.key.kind == VerificationMetricKind.ALARM_COUNT }, VerificationReason.FIELD_MISSING)
        val absentBaseline = record.copy(retest = record.retest!!.copy(metrics = record.retest!!.metrics + after.copy(value = 24.0)))
        val absentResult = VerificationComparisonEngine.compare(absentBaseline).metrics.first { it.key.kind == VerificationMetricKind.ALARM_COUNT }
        assertNull(absentResult.before); assertEquals(24.0, absentResult.after!!.value!!, 0.0)
        blocked(absentResult, VerificationReason.FIELD_MISSING)
        val badWindow = independent.copy(retest = independent.retest!!.copy(metrics = independent.retest!!.metrics.map {
            if (it.key.kind == VerificationMetricKind.ALARM_COUNT) it.copy(value = 24.0, window = it.window.copy(startedAt = null)) else it
        }))
        val windowResult = VerificationComparisonEngine.compare(badWindow)
        assertEquals(-20.0, windowResult.metrics.first().delta!!, 0.0)
        blocked(windowResult.metrics.first { it.key.kind == VerificationMetricKind.ALARM_COUNT }, VerificationReason.WINDOW_MISSING)
        val commonFailure = VerificationComparisonEngine.compare(badWindow.copy(change = badWindow.change!!.copy(similarScenario = VerificationConfirmation.UNKNOWN)))
        commonFailure.metrics.forEach { blocked(it, VerificationReason.SCENARIO_UNCONFIRMED) }
    }

    @Test fun durationAndCountUseTheirOwnBatteryWindows() {
        for ((kind, unit, rawBefore, rawAfter, expectedBefore, expectedAfter, rateUnit, source) in listOf(
            RateCase(VerificationMetricKind.WAKELOCK_DURATION, VerificationUnit.MILLIS, 120_000.0, 180_000.0, 2.0, 3.0, "minutes/battery-hour", "batterystats"),
            RateCase(VerificationMetricKind.ALARM_COUNT, VerificationUnit.COUNT, 12.0, 24.0, 12.0, 24.0, "count/battery-hour", "alarm"))) {
            fun convert(summary: DiagnosticVerificationSummary, value: Double) = summary.copy(sources = mapOf(source to DiagnosticSourceStatus.AVAILABLE), metrics = summary.metrics.map { it.copy(key = it.key.copy(kind = kind), value = value, unit = unit, estimate = VerificationEstimate.ACTUAL, window = it.window.copy(source = source)) })
            val input = record.copy(baseline = convert(record.baseline, rawBefore), retest = convert(record.retest!!, rawAfter))
            val result = metric(input)
            assertEquals(expectedBefore, result.beforeRate!!, 0.0); assertEquals(expectedAfter, result.afterRate!!, 0.0)
            assertEquals(rateUnit, result.rateUnit)
            blocked(metric(input.copy(retest = input.retest!!.copy(metrics = input.retest!!.metrics.map { it.copy(window = it.window.copy(startedAt = null)) }))), VerificationReason.WINDOW_MISSING)
            if (source == "alarm") blocked(metric(input.copy(baseline = input.baseline.copy(metrics = input.baseline.metrics.map { it.copy(window = it.window.copy(source = "batterystats")) }), retest = input.retest!!.copy(metrics = input.retest!!.metrics.map { it.copy(window = it.window.copy(source = "batterystats")) }))), VerificationReason.METRIC_SOURCE_MISMATCH)
        }
    }
    private data class RateCase(val kind: VerificationMetricKind, val unit: VerificationUnit, val rawBefore: Double, val rawAfter: Double, val before: Double, val after: Double, val rateUnit: String, val source: String)

    @Test fun zeroBaselineHasNoRelativeChangeAndExactEqualityAloneMeansNoChange() {
        val zero = metric(record.copy(baseline = record.baseline.copy(metrics = record.baseline.metrics.map { it.copy(value = 0.0) })))
        assertEquals(80.0, zero.delta!!, 0.0); assertNull(zero.relativeChange)
        val equal = metric(replace { it.copy(value = 100.0) })
        assertEquals(VerificationComparisonStatus.NO_NUMERIC_CHANGE, equal.status)
        val tiny = metric(replace { it.copy(value = 100.0000000001) })
        assertEquals(VerificationComparisonStatus.CHANGE_OBSERVED, tiny.status)
        val overflow = metric(record.copy(baseline = record.baseline.copy(metrics = record.baseline.metrics.map { it.copy(value = Double.MIN_VALUE) }), retest = record.retest!!.copy(metrics = record.retest!!.metrics.map { it.copy(value = Double.MAX_VALUE) })))
        assertNotNull(overflow.delta); assertNull(overflow.relativeChange)
        blocked(metric(record.copy(baseline = record.baseline.copy(metrics = record.baseline.metrics.map { it.copy(value = Double.MAX_VALUE, window = it.window.copy(batteryDurationMillis = 1_800_000)) }), retest = record.retest!!.copy(metrics = record.retest!!.metrics.map { it.copy(window = it.window.copy(batteryDurationMillis = 1_800_000)) }))), VerificationReason.VALUE_INVALID)
    }

    @Test fun packageSetChangesRefuseComparisonAndRetainBothRawGroups() {
        val result = VerificationComparisonEngine.compare(replace { it.copy(key = it.key.copy(packageNames = listOf("com.example.app", "com.example.shared"))) })
        assertEquals(2, result.metrics.size)
        result.metrics.forEach { blocked(it, VerificationReason.GROUP_CHANGED) }
        assertTrue(result.metrics.any { it.after?.key?.packageNames?.size == 2 })
    }

    @Test fun duplicateMetricKeysCannotSilentlyPickOneValue() {
        val input = record.copy(baseline = record.baseline.copy(metrics = record.baseline.metrics + record.baseline.metrics.first().copy(value = 1000.0)))
        blocked(metric(input), VerificationReason.METRIC_AMBIGUOUS)
    }

    @Test fun changingUidDoesNotChangePackageIdentity() {
        val before = VerificationFixtures.snapshot()
        val startedAt = record.retest!!.metrics.first().window.startedAt!!
        val after = before.copy(sampledAtInstant = startedAt.plusSeconds(3660), apps = before.apps.map { it.copy(uid = 20999, estimatedPowerMah = 80.0) })
        val metadata = VerificationFixtures.metadata().copy(windowsBySource = VerificationFixtures.metadata().windowsBySource.mapValues { VerificationFixtures.window(it.key, startedAt) })
        val projectedBefore = VerificationSummaryProjector.project(before, VerificationFixtures.metadata(), VerificationFixtures.context(), record.target)!!
        val projectedAfter = VerificationSummaryProjector.project(after, metadata, VerificationFixtures.context(), record.target)!!
        val result = VerificationComparisonEngine.compare(record.copy(baseline = projectedBefore, retest = projectedAfter)).metrics.first { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }
        assertEquals(-20.0, result.delta!!, 0.0)
    }
}
