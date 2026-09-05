package com.aegis.apa

import com.aegis.apa.agent.Level0ReportBuilder
import com.aegis.apa.tool.BatteryInfo
import com.aegis.apa.tool.DeviceInfo
import com.aegis.apa.tool.DisplayInfo
import com.aegis.apa.tool.HardwareExperienceGrade
import com.aegis.apa.tool.RamInfo
import com.aegis.apa.tool.StorageInfo
import com.aegis.apa.tool.UsageSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class Level0ReportBuilderTest {
    @Test
    fun separatesBaseAndOptionalDataInTheLevel0Report() {
        val report = Level0ReportBuilder.build(
            sampledAt = "12:00:00",
            deviceInfo = DeviceInfo("Example Phone", "Android 16（API 36）"),
            batteryInfo = BatteryInfo(level = 70, status = "未充电"),
            displayInfo = DisplayInfo(null, null, null, null, null),
            ramInfo = RamInfo(8_000_000_000L, 4_000_000_000L, false),
            storageInfo = StorageInfo(128_000_000_000L, 64_000_000_000L),
            usageSummary = UsageSummary(false, null, emptyList()),
            hardwareGrade = HardwareExperienceGrade("待读取", "读取芯片档案后给出等级", emptyList()),
            securityPatch = null,
            socName = null,
            supportedAbis = emptyList()
        )

        assertTrue(report.contains("【设备与系统】"))
        assertTrue(report.contains("【屏幕体验】"))
        assertTrue(report.contains("【电池即时状态】"))
        assertTrue(report.contains("【资源状态】"))
        assertTrue(report.contains("【可选使用习惯】"))
        assertTrue(report.contains("未授权，不影响基础报告"))
        assertTrue(report.contains("设备未提供"))
    }

    @Test
    fun keepsOneDecimalForResourceCapacityInsteadOfRoundingDown() {
        val report = Level0ReportBuilder.build(
            sampledAt = "12:00:00",
            deviceInfo = DeviceInfo("Example Phone", "Android 16（API 36）"),
            batteryInfo = BatteryInfo(level = 70, status = "未充电"),
            displayInfo = DisplayInfo(null, null, null, null, null),
            ramInfo = RamInfo(8_590_000_000L, 4_290_000_000L, false),
            storageInfo = StorageInfo(128_000_000_000L, 63_450_000_000L),
            usageSummary = UsageSummary(false, null, emptyList()),
            hardwareGrade = HardwareExperienceGrade("待读取", "读取芯片档案后给出等级", emptyList()),
            securityPatch = null,
            socName = null,
            supportedAbis = emptyList()
        )

        assertTrue(report.contains("4.0 GB 可用 / 8.0 GB 总量"))
        assertTrue(report.contains("59.1 GB 可用 / 119.2 GB 总量"))
    }
}
