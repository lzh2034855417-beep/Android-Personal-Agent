package com.aegis.apa

import com.aegis.apa.model.BatteryObservationAnalyzer
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.model.BatteryObservationReportBuilder
import com.aegis.apa.model.BatteryObservationValidity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryObservationAnalyzerTest {
    private val start = Instant.parse("2026-09-14T00:00:00Z")

    @Test
    fun computesMeasuredDrainRateFromTwoDischargingSamples() {
        val result = BatteryObservationAnalyzer.finish(
            start = BatteryObservationPoint(start, 80, charging = false),
            end = BatteryObservationPoint(start.plusSeconds(4 * 60 * 60), 68, charging = false)
        )

        assertEquals(BatteryObservationValidity.VALID, result.validity)
        assertEquals(12, result.dropPercent)
        assertEquals(3.0, result.drainPercentPerHour!!, 0.001)
        assertTrue(result.isUsableEvidence)
    }

    @Test
    fun rejectsChargingShortRisingAndFlatMeasurements() {
        val charging = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 80, charging = true),
            BatteryObservationPoint(start.plusSeconds(3600), 75, charging = false)
        )
        val tooShort = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 80, charging = false),
            BatteryObservationPoint(start.plusSeconds(10 * 60), 78, charging = false)
        )
        val rising = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 70, charging = false),
            BatteryObservationPoint(start.plusSeconds(3600), 72, charging = false)
        )
        val flat = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 70, charging = false),
            BatteryObservationPoint(start.plusSeconds(3600), 70, charging = false)
        )

        assertEquals(BatteryObservationValidity.STARTED_WHILE_CHARGING, charging.validity)
        assertEquals(BatteryObservationValidity.TOO_SHORT, tooShort.validity)
        assertEquals(BatteryObservationValidity.BATTERY_INCREASED, rising.validity)
        assertEquals(BatteryObservationValidity.NO_MEASURABLE_DROP, flat.validity)
        assertNull(charging.drainPercentPerHour)
        assertFalse(tooShort.isUsableEvidence)
    }

    @Test
    fun reportStatesMeasurementAndItsAttributionLimit() {
        val result = BatteryObservationAnalyzer.finish(
            BatteryObservationPoint(start, 90, charging = false),
            BatteryObservationPoint(start.plusSeconds(2 * 60 * 60), 84, charging = false)
        )

        val report = BatteryObservationReportBuilder.build(result)

        assertTrue(report.contains("平均掉电速度：3.00%/小时"))
        assertTrue(report.contains("不能据此归因到具体应用"))
        assertFalse(report.contains("Scene 报告"))
    }
}
