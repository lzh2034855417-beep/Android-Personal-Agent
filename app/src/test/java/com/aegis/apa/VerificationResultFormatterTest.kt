package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class VerificationResultFormatterTest {
    // Catch lost evidence, language-dependent arithmetic, causal claims and hidden side effects.
    @Test fun bothLanguagesShowTheSameValuesWindowsSourcesAndSideEffects() {
        val record = VerificationFixtures.record().let { it.copy(change = it.change!!.copy(sideEffects = "Notifications delayed", rollback = "Restore sync")) }
        val result = VerificationComparisonEngine.compare(record)
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val zh = VerificationResultFormatter.format(record, result, AppLanguage.ZH_CN)
            val en = VerificationResultFormatter.format(record, result, AppLanguage.EN)
            for (text in listOf(zh, en)) {
                for (value in listOf("100", "80", "-20", "-20%", "batterystats", "com.example.app", "2026-10-01T00:00:00Z", "3600000", "Notifications delayed", "Restore sync")) assertTrue(value, text.contains(value))
                assertFalse(text.contains("已节电")); assertFalse(text.contains("显著")); assertFalse(text.contains("元凶"))
                assertFalse(text.lowercase().contains("significant")); assertFalse(text.lowercase().contains("saved power"))
            }
            assertTrue(zh.contains("本次观察值减少")); assertTrue(en.contains("Observed value decreased"))
            assertTrue(zh.contains("估算")); assertTrue(en.contains("estimated"))
            assertTrue(zh.contains("用户确认")); assertTrue(en.contains("user confirmed"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun unavailableResultsKeepRawValuesReasonsAndNeverPublishPercentage() {
        val record = VerificationFixtures.record().let { it.copy(change = it.change!!.copy(similarScenario = VerificationConfirmation.UNKNOWN)) }
        val result = VerificationComparisonEngine.compare(record)
        for (language in AppLanguage.entries) {
            val text = VerificationResultFormatter.format(record, result, language)
            assertTrue(text.contains("scenario_unconfirmed")); assertTrue(text.contains("100")); assertTrue(text.contains("80"))
            assertFalse(text.contains("-20%")); assertFalse(text.contains("Observed value decreased")); assertFalse(text.contains("本次观察值减少"))
        }
    }

    @Test fun missingRetestIsExplicitAndZeroBaselineDoesNotInventPercent() {
        val initial = VerificationFixtures.record()
        for (language in AppLanguage.entries) {
            val missing = initial.copy(retest = null)
            assertTrue(VerificationResultFormatter.format(missing, VerificationComparisonEngine.compare(missing), language).contains("missing_retest"))
            val zero = initial.copy(baseline = initial.baseline.copy(metrics = initial.baseline.metrics.map { it.copy(value = 0.0) }))
            val text = VerificationResultFormatter.format(zero, VerificationComparisonEngine.compare(zero), language)
            assertFalse(text.contains("%")); assertFalse(text.contains("Infinity")); assertFalse(text.contains("NaN"))
        }
    }

    @Test fun opposingMetricsAndActualLabelsRemainSeparateFromSideEffects() {
        val record = VerificationFixtures.record()
        fun extra(summary: DiagnosticVerificationSummary, value: Double) = summary.copy(metrics = summary.metrics + summary.metrics.first().copy(key = VerificationMetricKey(VerificationMetricKind.WAKELOCK_DURATION, listOf("com.example.app")), value = value, unit = VerificationUnit.MILLIS, estimate = VerificationEstimate.ACTUAL))
        val input = record.copy(baseline = extra(record.baseline, 60_000.0), retest = extra(record.retest!!, 120_000.0))
        val text = VerificationResultFormatter.format(input, VerificationComparisonEngine.compare(input), AppLanguage.EN)
        assertTrue(text.contains("Observed value decreased")); assertTrue(text.contains("Observed value increased"))
        assertTrue(text.contains("actual")); assertTrue(text.contains("None noticed")); assertTrue(text.contains("minutes/battery-hour"))
    }
}
