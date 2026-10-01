package com.aegis.apa

import com.aegis.apa.agent.AdvancedLevelReportBuilder
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.RootStatus
import com.aegis.apa.model.ShizukuAccessState
import com.aegis.apa.tool.CpuPolicyProfile
import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource
import com.aegis.apa.tool.RootBatteryInfo
import com.aegis.apa.tool.ThermalSensorProfile
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedLevelReportBuilderTest {
    private val rootStatus = RootStatus(
        hasSuBinary = true,
        isShizukuInstalled = true,
        kernelSuManagerPackage = "me.weishu.kernelsu",
        isMagiskManagerInstalled = false
    )

    @Test
    fun englishLevel1ReportContainsNoChineseTemplateCopy() {
        val report = AdvancedLevelReportBuilder.buildLevel1(
            sampledAt = "2026-10-01 12:00:00 +08:00",
            rootStatus = rootStatus,
            shizukuAccessState = ShizukuAccessState.AUTHORIZED,
            language = AppLanguage.EN
        )

        assertTrue(report.contains("Sample time: 2026-10-01 12:00:00 +08:00"))
        assertTrue(report.contains("Shizuku app: Installed"))
        assertTrue(report.contains("Shizuku service: Connected"))
        assertTrue(report.contains("Shizuku authorization: Granted"))
        assertFalse(report.contains("Not read by this report"))
        listOf("采样时间", "已安装", "当前版本尚未读取", "说明").forEach {
            assertFalse("unexpected Chinese template: $it", report.contains(it))
        }
    }

    @Test
    fun level1ReportDistinguishesRunningServiceFromMissingPermission() {
        val report = AdvancedLevelReportBuilder.buildLevel1(
            sampledAt = "sample",
            rootStatus = rootStatus,
            shizukuAccessState = ShizukuAccessState.PERMISSION_REQUIRED,
            language = AppLanguage.EN
        )

        assertTrue(report.contains("Shizuku service: Connected"))
        assertTrue(report.contains("Shizuku authorization: Permission required"))
    }

    @Test
    fun englishLevel2ReportLocalizesRootAndProfileNarrativeButPreservesRawEvidence() {
        val profile = DeviceProfileSnapshot(
            model = "2509FPN0BC",
            manufacturer = "Xiaomi",
            device = "walt",
            soc = "SM8850",
            androidVersion = "17",
            buildVersion = "OS4.0.1",
            kernelVersion = "6.6.99-android16",
            cpuPresent = "0-7",
            cpuOnline = "0-7",
            cpuPolicies = listOf(CpuPolicyProfile("policy7", listOf(7), 800_000, 4_000_000, "walt")),
            thermalSensors = listOf(ThermalSensorProfile("battery", 36.5)),
            access = DeviceProfileAccess.ROOT,
            hardwareSupplyInfo = HardwareSupplyInfo(
                ramVendor = "Samsung",
                ramType = "LPDDR5X",
                storageVendor = "SK hynix",
                storageModel = "HN8T271EJKX152",
                storageSpec = "UFS 4.1",
                source = HardwareSupplySource.ROOT_SYSFS
            ),
            sampledAtInstant = Instant.parse("2026-10-01T04:00:00Z")
        )
        val battery = RootBatteryInfo(
            designCapacityMah = 7500,
            fullChargeCapacityMah = 7448,
            cycleCount = 21,
            currentMilliAmp = -500,
            voltageMilliVolt = 4000,
            temperatureCelsius = 36.5,
            sampledAtInstant = Instant.parse("2026-10-01T04:00:00Z")
        )

        val report = AdvancedLevelReportBuilder.buildLevel2(
            sampledAt = "2026-10-01 12:00:00 +08:00",
            rootStatus = rootStatus,
            rootBatteryInfo = battery,
            deviceProfile = profile,
            language = AppLanguage.EN
        )

        assertTrue(report.contains("Design capacity: 7500 mAh"))
        assertTrue(report.contains("Cycle count: 21"))
        assertTrue(report.contains("Scheduling profile: Read-only"))
        assertTrue(report.contains("SM8850"))
        assertTrue(report.contains("SK hynix"))
        assertTrue(report.contains("HN8T271EJKX152"))
        assertTrue(report.contains("policy7 · CPU 7 · 800 MHz–4000 MHz · walt"))
        listOf("采样时间", "设计容量", "循环次数", "调度档案", "设备未提供", "采集提示").forEach {
            assertFalse("unexpected Chinese template: $it", report.contains(it))
        }
    }

    @Test
    fun chineseAdvancedReportsRemainTheDefault() {
        assertTrue(
            AdvancedLevelReportBuilder.buildLevel1("sample", rootStatus).contains("采样时间：sample")
        )
    }
}
