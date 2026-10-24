package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.BatteryStatsEvidenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsEvidenceParserTest {
    @Test
    fun drainCannotBorrowLaterActualValue() {
        // Catches actual drain from another scope winning over the selected computed value.
        val window = BatteryStatsEvidenceParser.parseDrainWindow("""
            Statistics since last charge:
            Time on battery: 1h (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 100
            Statistics since last unplugged:
            Time on battery: 30m (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 15, actual drain: 12
        """.trimIndent())

        assertNotNull(window)
        assertEquals(3_600_000L, window!!.durationMillis)
        assertEquals(100.0, window.drainMah, 0.0)
        assertFalse(window.usesActualDrain)
    }

    @Test
    fun zeroActualWinsOverComputed() {
        // Catches positive-only validation silently replacing measured zero with an estimate.
        val window = BatteryStatsEvidenceParser.parseDrainWindow(drainReport("10", "0"))
        assertNotNull(window)
        assertEquals(0.0, window!!.drainMah, 0.0)
        assertTrue(window.usesActualDrain)
    }

    @Test
    fun zeroComputedIsPresent() {
        // Catches absence being conflated with a legitimate zero estimate.
        val window = BatteryStatsEvidenceParser.parseDrainWindow(drainReport("0"))
        assertNotNull(window)
        assertEquals(0.0, window!!.drainMah, 0.0)
        assertFalse(window.usesActualDrain)
    }

    @Test
    fun invalidActualFallsBackToSameSectionComputedDrain() {
        // Catches non-finite actual values suppressing a valid same-window estimate.
        listOf("-1", "NaN", "Infinity", "-Infinity").forEach { actual ->
            val window = BatteryStatsEvidenceParser.parseDrainWindow(drainReport("10", actual))
            assertNotNull(actual, window)
            assertEquals(actual, 10.0, window!!.drainMah, 0.0)
            assertFalse(actual, window.usesActualDrain)
        }
    }

    @Test
    fun negativeAndNonFiniteComputedDrainAreAbsent() {
        // Catches invalid estimates being presented as drain evidence.
        listOf("-1", "NaN", "Infinity", "-Infinity").forEach { computed ->
            assertNull(computed, BatteryStatsEvidenceParser.parseDrainWindow(drainReport(computed)))
        }
    }

    @Test
    fun capacityStillRequiresPositiveFiniteValue() {
        // Catches applying the relaxed drain-zero rule to capacity.
        listOf("0", "-1", "NaN", "Infinity", "-Infinity").forEach { capacity ->
            assertNull(capacity, BatteryStatsEvidenceParser.parseDrainWindow(drainReport("10", capacity = capacity)))
        }
    }

    @Test
    fun durationStillRequiresPositiveValidValue() {
        // Catches extracting a positive duration token from a negative/malformed duration.
        listOf("0ms", "-1h", "NaN", "Infinity", "1h junk", "999999999999999999999h").forEach { duration ->
            assertNull(duration, BatteryStatsEvidenceParser.parseDrainWindow(drainReport("10", duration = duration)))
        }
    }

    @Test
    fun duplicateLastChargeSectionsCannotSupplyDrain() {
        // Catches arbitrarily selecting one of two candidate windows.
        assertNull(BatteryStatsEvidenceParser.parseDrainWindow(drainReport("100") + "\n" + drainReport("12")))
    }

    @Test
    fun unknownLaterStatisticsCannotSupplyMissingPower() {
        // Catches searching beyond an unrecognized statistics heading for capacity/drain.
        val output = """
            Statistics since last charge:
            Time on battery: 1h (100%) realtime
            Statistics for vendor scope:
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 12
        """.trimIndent()
        assertNull(BatteryStatsEvidenceParser.parseDrainWindow(output))
    }

    @Test
    fun headerMentionInProseCannotSelectDrain() {
        // Catches substring selection of a non-heading mention ahead of the actual section.
        val output = "Note: Statistics since last charge:\nTime on battery: 10m (100%) realtime\n" +
            "Estimated power use (mAh):\n  Capacity: 5000, Computed drain: 999\n" + drainReport("100")
        val window = BatteryStatsEvidenceParser.parseDrainWindow(output)
        assertEquals(3_600_000L, window?.durationMillis)
        assertEquals(100.0, window!!.drainMah, 0.0)
    }

    @Test
    fun conflictingBatteryDurationsCannotSupplyDrainWindow() {
        // Catches selecting the first duration where metadata preserves ambiguity.
        assertNull(BatteryStatsEvidenceParser.parseDrainWindow(
            drainReport("100").replace("Estimated power use", "Time on battery: 30m (100%) realtime\nEstimated power use")
        ))
    }

    @Test
    fun appMetricsStayWithinTheSelectedStatisticsSection() {
        // Catches earlier power and later wake locks being merged into selected-window evidence.
        val result = BatteryStatsEvidenceParser.parse("""
            Statistics for vendor scope:
            Estimated power use (mAh):
              UID u0a123: 999 fg: 900 (9h)
            Statistics since last charge:
            Time on battery: 1h (100%) realtime
            Estimated power use (mAh):
              UID u0a123: 100 fg: 10 (10m) bg: 90 (20m)
            Uid u0a123:
              Wake lock sync: 2m partial (1 times) realtime
            Statistics for another vendor scope:
            Estimated power use (mAh):
              UID u0a999: 12
            Uid u0a123:
              Wake lock upload: 30m partial (1 times) realtime
        """.trimIndent())

        assertEquals(setOf(10123), result.apps.keys)
        val app = result.apps.getValue(10123)
        assertEquals(100.0, app.estimatedPowerMah!!, 0.0)
        assertEquals(10.0, app.foregroundPowerMah!!, 0.0)
        assertEquals(90.0, app.backgroundPowerMah!!, 0.0)
        assertEquals(600_000L, app.foregroundDurationMillis)
        assertEquals(1_200_000L, app.backgroundDurationMillis)
        assertEquals(120_000L, app.wakeLockDurationMillis)
    }

    @Test
    fun unknownOnlyOrDuplicateStatisticsCannotSupplyAppMetrics() {
        // Catches falling back to the whole report after scoped selection failed.
        val metrics = "Estimated power use (mAh):\n  UID u0a123: 100\nUid u0a123:\n  Wake lock sync: 2m partial (1 times) realtime"
        listOf("Statistics for vendor scope:\n$metrics",
            "Statistics since last charge:\n$metrics\nStatistics since last charge:\n$metrics").forEach { output ->
            val result = BatteryStatsEvidenceParser.parse(output)
            assertTrue(result.apps.isEmpty())
            assertTrue(result.parsedFields.isEmpty())
        }
    }

    private fun drainReport(computed: String, actual: String? = null, capacity: String = "5000", duration: String = "1h"): String =
        "Statistics since last charge:\nTime on battery: $duration (100%) realtime\nEstimated power use (mAh):\n  Capacity: $capacity, Computed drain: $computed" +
            (actual?.let { ", actual drain: $it" } ?: "")

    @Test
    fun parsesXiaomiForegroundAndBackgroundPowerFromUidSummary() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                  Capacity: 7448, Computed drain: 5829, actual drain: 5829
                  UID u0a266: 1575 fg: 74.8 (23m 54s 524ms) bg: 1485 (15h 15m 17s 443ms) fgs: 8.18 (31s 448ms)
                      screen=7.83 cpu=1274 cpu:fg=53.2 cpu:bg=1220 mobile_radio=267 mobile_radio:bg=255
            """.trimIndent()
        )

        val app = result.apps.getValue(10266)
        assertEquals(1575.0, app.estimatedPowerMah!!, 0.001)
        assertEquals(74.8, app.foregroundPowerMah!!, 0.001)
        assertEquals(1485.0, app.backgroundPowerMah!!, 0.001)
        assertEquals(1_434_524L, app.foregroundDurationMillis)
        assertEquals(54_917_443L, app.backgroundDurationMillis)
    }

    @Test
    fun drainWindowIgnoresEarlierBatteryCapacityMetadata() {
        val window = BatteryStatsEvidenceParser.parseDrainWindow(
            """
                Statistics since last charge:
                Battery capacity: 7448000 uAh
                Time on battery: 15h 39m 45s 541ms (98.8%) realtime, 8h uptime
                Estimated power use (mAh):
                  Capacity: 7448, Computed drain: 5829, actual drain: 5829
            """.trimIndent()
        )

        assertEquals(56_385_541L, window?.durationMillis)
        assertEquals(7448.0, window?.capacityMah ?: 0.0, 0.001)
        assertEquals(5829.0, window?.drainMah ?: 0.0, 0.001)
        assertTrue(window?.usesActualDrain == true)
    }

    @Test
    fun parsesMahAndSumsPartialWakeLocksForTheSameUid() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                  Capacity: 5000, Computed drain: 500
                  UID u0a123: 245.5 fg: 10.0 bg: 235.5
                Uid u0a123:
                  Wake lock sync: 4m 30s partial (3 times) realtime
                  Wake lock upload: 20m 0s partial (1 times) realtime
                Uid u0a999:
                  Network: 9999 packets
            """.trimIndent()
        )

        val app = result.apps.getValue(10123)
        assertEquals(245.5, app.estimatedPowerMah!!, 0.001)
        assertEquals(24 * 60_000L + 30_000L, app.wakeLockDurationMillis)
        assertTrue(EvidenceField.POWER_MAH in result.parsedFields)
        assertTrue(EvidenceField.WAKELOCK_TIME in result.parsedFields)
    }

    @Test
    fun doesNotTreatPacketRowsAsPower() {
        val result = BatteryStatsEvidenceParser.parse("Uid u0a184: 9999 packets")

        assertTrue(result.apps.isEmpty())
    }

    @Test
    fun parsesOfficialMixedCaseUidPowerRow() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                  Capacity: 5000, Computed drain: 500
                  Uid u0a123: 245.5 ( cpu=120.0 wifi=20.0 )
            """.trimIndent()
        )

        assertEquals(245.5, result.apps.getValue(10123).estimatedPowerMah!!, 0.001)
        assertTrue(EvidenceField.POWER_MAH in result.parsedFields)
    }

    @Test
    fun parsesOfficialProcessStateDurationsAndCountsForegroundServiceAsBackground() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                  Capacity: 5000, Computed drain: 500
                  UID u0a123: 245.5 fg: 90.0 bg: 120.0 fgs: 30.0 cached: 5.5 ( screen=5.0 cpu=240.5 (2h) cpu:fg=90.0 (1h2m3s4ms) cpu:bg=120.0 (45m) cpu:fgs=30.0 (5m) cpu:cached=0.5 (2h) )
            """.trimIndent()
        )

        val app = result.apps.getValue(10123)
        assertEquals(3_723_004L, app.foregroundDurationMillis)
        assertEquals(50 * 60_000L, app.backgroundDurationMillis)
        assertTrue(EvidenceField.FOREGROUND_TIME in result.parsedFields)
    }

    @Test
    fun rejectsCombinedProcessStateDurationOverflow() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                  UID u0a123: 1.0 bg: 0.5 fgs: 0.5 ( cpu:fg=0 cpu:bg=0.5 (9223372036854775807ms) cpu:fgs=0.5 (1ms) )
            """.trimIndent()
        )

        assertNull(result.apps.getValue(10123).backgroundDurationMillis)
    }

    @Test
    fun parsesSameIndentedMixedCaseUidRow() {
        val result = BatteryStatsEvidenceParser.parse(
            """
                Estimated power use (mAh):
                Uid u0a123: 12.5 ( cpu=12.5 )
                Other section:
            """.trimIndent()
        )

        assertEquals(12.5, result.apps.getValue(10123).estimatedPowerMah!!, 0.001)
    }
}
