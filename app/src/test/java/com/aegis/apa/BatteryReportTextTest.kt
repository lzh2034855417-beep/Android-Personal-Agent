package com.aegis.apa

import com.aegis.apa.tool.BatteryInfo
import com.aegis.apa.tool.BatteryPlugText
import com.aegis.apa.tool.BatteryReportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryReportTextTest {
    @Test
    fun printsStandardBatteryFieldsAndKeepsMissingFieldsExplicit() {
        val text = BatteryReportText.format(
            BatteryInfo(
                level = 69,
                status = "正在充电",
                temperatureCelsius = 32.1,
                voltageMilliVolt = 4480,
                health = "良好",
                plugged = "USB",
                technology = null,
                isPresent = true
            )
        )

        assertTrue(text.contains("电池温度：32.1°C"))
        assertTrue(text.contains("电池电压：4480 mV"))
        assertTrue(text.contains("充电方式：USB"))
        assertTrue(text.contains("电池技术：设备未提供"))
    }

    @Test
    fun combinesMultipleChargingSourcesInsteadOfHidingThem() {
        assertEquals(
            "USB + 无线充电",
            BatteryPlugText.fromFlags(android.os.BatteryManager.BATTERY_PLUGGED_USB or android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS)
        )
    }
}
