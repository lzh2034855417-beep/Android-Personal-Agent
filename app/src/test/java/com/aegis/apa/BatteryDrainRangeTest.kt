package com.aegis.apa

import com.aegis.apa.agent.AppPowerRanking
import com.aegis.apa.agent.PowerDiagnosticReportBuilder
import com.aegis.apa.agent.VerificationSummaryProjector
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import com.aegis.apa.tool.BatteryStatsEvidenceParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BatteryDrainRangeTest {
    private fun report(actual: String = "962-1006") = """
        Statistics since last charge:
        Time on battery: 21h 18m 35s 511ms (100.0%) realtime, 1h uptime
        Estimated power use (mAh):
          Capacity: 4374, Computed drain: 1.00, actual drain: $actual
    """.trimIndent()

    private fun sample(actual: String = "962-1006", power: Double = 158.0): PowerDiagnosticSnapshot =
        PowerDiagnosticSnapshot(Instant.EPOCH, 0, emptyMap(),
            listOf(AppPowerEvidence(10123, listOf("example.app"), listOf("Example"), estimatedPowerMah = power)),
            SystemPowerEvidence(batteryDrainWindow = BatteryStatsEvidenceParser.parseDrainWindow(report(actual))), emptyList())

    @Test fun actualRangeWinsOverMisleadingComputedDrain() {
        val window = BatteryStatsEvidenceParser.parseDrainWindow(report())!!
        assertEquals(962.0, window.drainMah, 0.0)
        assertTrue(window.usesActualDrain)
    }

    @Test fun negativeScientificExponentRemainsAValidScalar() {
        val window = BatteryStatsEvidenceParser.parseDrainWindow(report("1e-3"))
        assertNotNull(window)
        assertEquals(0.001, window!!.drainMah, 0.0)
        assertTrue(window.usesActualDrain)
    }

    @Test fun bothLanguagesPublishRangeRatesAndNeverOneMah() {
        val zh = PowerDiagnosticReportBuilder.build(sample())
        val en = PowerDiagnosticReportBuilder.build(sample(), language = AppLanguage.EN)
        assertTrue(zh, zh.contains("周期耗电：962.0–1006.0 mAh"))
        assertTrue(zh, zh.contains("45.1–47.2 mAh/小时"))
        assertTrue(en, en.contains("Window drain: 962.0–1006.0 mAh"))
        assertTrue(en, en.contains("45.1–47.2 mAh/hour"))
        assertFalse(zh.contains("周期耗电：1.0 mAh"))
    }

    @Test fun rangeDenominatorPreservesRankingAndDisplaysShareBounds() {
        val zh = AppPowerRanking.build(sample(), AppLanguage.ZH_CN)
        assertTrue(zh, zh.contains("1. Example"))
        assertTrue(zh, zh.contains("15.7%–16.4%"))
        assertFalse(zh.contains("统计口径待核对"))
        // Between the total bounds is uncertain, not a proven basis conflict.
        val overlap = AppPowerRanking.build(sample(power = 980.0), AppLanguage.EN)
        assertTrue(overlap, overlap.contains("1. Example"))
        assertTrue(overlap, overlap.contains("share unavailable"))
        val conflict = AppPowerRanking.build(sample(power = 1010.0), AppLanguage.EN)
        assertTrue(conflict.contains("excluded from this window's ranking"))
        assertFalse(conflict.contains("1. Example"))
        assertTrue(conflict, conflict.contains("1006.0 mAh"))
    }

    @Test fun invalidRangesDoNotSilentlyFallBackToComputedValue() {
        listOf("1006-962", "962-", "962-NaN", "962-1006-extra").forEach {
            assertNull(it, BatteryStatsEvidenceParser.parseDrainWindow(report(it)))
        }
    }

    @Test fun zeroLowerBoundDoesNotCreateInfiniteRuntimeOrShare() {
        val sample = sample("0-10", 5.0)
        val report = PowerDiagnosticReportBuilder.build(sample, language = AppLanguage.EN)
        assertTrue(report, report.contains("0.0–10.0 mAh"))
        assertFalse(report.contains("Infinity"))
        assertFalse(report.contains("Projected runtime"))
        assertTrue(AppPowerRanking.build(sample, AppLanguage.EN).contains("share unavailable"))
    }

    @Test fun scalarVerificationNeverTreatsRangeEndpointAsExactTotal() {
        val base = VerificationFixtures.snapshot()
        val summary = VerificationSummaryProjector.project(
            base.copy(system = base.system.copy(batteryDrainWindow = sample().system.batteryDrainWindow)),
            VerificationFixtures.metadata(), VerificationFixtures.context(), VerificationTarget.Overall)!!
        val total = summary.metrics.single { it.key.kind == VerificationMetricKind.OVERALL_POWER }
        assertNull(total.value)
        assertEquals(VerificationEstimate.UNKNOWN, total.estimate)
    }
}
