package com.aegis.apa

import com.aegis.apa.tool.RootBatteryParser
import org.junit.Assert.*
import org.junit.Test

class RootBatteryParserTest {
    @Test fun normalizesSysfsUnitsAndKeepsSignedCurrent() {
        val value = RootBatteryParser.parse("charge_full_design=5000000\ncharge_full=4600000\ncycle_count=321\ncurrent_now=-450000\nvoltage_now=4100000\ntemp=325")
        assertEquals(5000L, value.designCapacityMah)
        assertEquals(4600L, value.fullChargeCapacityMah)
        assertEquals(321L, value.cycleCount)
        assertEquals(-450L, value.currentMilliAmp)
        assertEquals(4100L, value.voltageMilliVolt)
        assertEquals(32.5, value.temperatureCelsius!!, 0.001)
        assertNull(value.error)
    }
    @Test fun invalidOrUnrecognizedValuesAreUnavailable() {
        val value = RootBatteryParser.parse("charge_full=-1\ncycle_count=unknown\nvoltage_now=-5\ntemp=NaN\nsecret=irrelevant")
        assertNull(value.fullChargeCapacityMah)
        assertNull(value.cycleCount)
        assertNull(value.voltageMilliVolt)
        assertNull(value.temperatureCelsius)
        assertNotNull(value.error)
    }
    @Test fun zeroCyclesRemainAValidReading() {
        val value = RootBatteryParser.parse("cycle_count=0")
        assertEquals(0L, value.cycleCount)
        assertNull(value.error)
    }
}
