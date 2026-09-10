package com.aegis.apa

import com.aegis.apa.model.BatteryInfo
import com.aegis.apa.agent.QuickReport
import org.junit.Assert.*
import org.junit.Test

class QuickReportTest {
    @Test fun missingTemperatureDoesNotProduceHeatDiagnosis() {
        val text = QuickReport.explain("发热", BatteryInfo(null, "未知"))
        assertTrue(text.contains("未获取"))
        assertFalse(text.contains("温度正常"))
    }
    @Test fun snapshotCannotRecommendBatteryReplacement() {
        val text = QuickReport.explain("电池", BatteryInfo(82, "放电中", temperatureCelsius = 35.0))
        assertTrue(text.contains("82%"))
        assertTrue(text.contains("不足以判断"))
    }
}
