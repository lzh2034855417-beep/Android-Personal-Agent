package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.BatteryStatsEvidenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsEvidenceParserTest {
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
