package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.BatteryStatsEvidenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsEvidenceParserTest {
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
