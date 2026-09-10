package com.aegis.apa

import android.os.BatteryManager
import com.aegis.apa.tool.BatteryReading
import com.aegis.apa.agent.DeviceContext
import com.aegis.apa.agent.LocalDeviceAnalyzer
import org.junit.Assert.*
import org.junit.Test

class TelemetryBoundaryTest {
    @Test fun invalidResourceReadingsCannotBeCalledHealthy() {
        val report = LocalDeviceAnalyzer.analyze(DeviceContext("Phone", "16", 80, 0, 0, -1, 128, 0))
        assertFalse(report.findings.any { it.contains("正常") || it.contains("充足") })
        assertTrue(report.findings.count { it.contains("未获取到有效") } == 2)
    }
    @Test fun invalidBatteryLevelsRemainUnknown() {
        assertNull(BatteryReading.percentage(-1, 100))
        assertNull(BatteryReading.percentage(80, 0))
        assertNull(BatteryReading.percentage(120, 100))
        assertEquals(0, BatteryReading.percentage(0, 100))
        assertEquals(50, BatteryReading.percentage(100, 200))
    }
    @Test fun absentChargingStatusIsExplicit() {
        assertEquals("未获取到", BatteryReading.status(-1))
        assertEquals("正在放电", BatteryReading.status(BatteryManager.BATTERY_STATUS_DISCHARGING))
    }
    @Test fun unknownBatteryCannotTriggerLowBatteryAdvice() {
        val context = DeviceContext("Phone", "16", null, 4, 8, 64, 128, 3)
        val findings = LocalDeviceAnalyzer.analyze(context).findings.joinToString()
        assertTrue(findings.contains("电量未获取到"))
        assertFalse(findings.contains("建议及时充电"))
    }
}
