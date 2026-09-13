package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.BatteryStatsEvidenceParser
import org.junit.Assert.assertEquals
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
}
