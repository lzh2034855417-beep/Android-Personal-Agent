package com.aegis.apa

import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileParser
import com.aegis.apa.tool.toChipSchedulingDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileParserTest {
    @Test
    fun parsesSm8850CpuPoliciesAndThermalSensors() {
        val raw = """
            MANUFACTURER=Xiaomi
            MODEL=2509FPN0BC
            DEVICE=popsicle
            SOC=SM8850
            ANDROID=17
            BUILD=OS4.0.0.23.XPBCNXM
            KERNEL=6.12.69-android16-6
            CPU_PRESENT=0-7
            CPU_ONLINE=0-7
            POLICY=policy0|0 1 2 3 4 5|384000|3628800|schedutil
            POLICY=policy6|6 7|768000|4608000|walt
            DDR_TYPE_HEX=00000008
            STORAGE=SKhynix|HN8T271EJKX152
            THERMAL=cpu-0-0-usr|42123
            THERMAL=battery|32000
            SERIAL=must-not-be-retained
        """.trimIndent()

        val profile = DeviceProfileParser.parse(raw, DeviceProfileAccess.ROOT)

        assertEquals("2509FPN0BC", profile.model)
        assertEquals("popsicle", profile.device)
        assertEquals("SM8850", profile.soc)
        assertEquals("17", profile.androidVersion)
        assertEquals("OS4.0.0.23.XPBCNXM", profile.buildVersion)
        assertEquals("6.12.69-android16-6", profile.kernelVersion)
        assertEquals("0-7", profile.cpuPresent)
        assertEquals("0-7", profile.cpuOnline)
        assertEquals(DeviceProfileAccess.ROOT, profile.access)
        assertEquals(2, profile.cpuPolicies.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), profile.cpuPolicies[0].cpus)
        assertEquals(384000L, profile.cpuPolicies[0].minFrequencyKhz)
        assertEquals(3628800L, profile.cpuPolicies[0].maxFrequencyKhz)
        assertEquals("schedutil", profile.cpuPolicies[0].governor)
        assertEquals(listOf(6, 7), profile.cpuPolicies[1].cpus)
        assertEquals(4608000L, profile.cpuPolicies[1].maxFrequencyKhz)
        assertEquals(42.123, profile.thermalSensors[0].temperatureCelsius, 0.001)
        assertEquals(32.0, profile.thermalSensors[1].temperatureCelsius, 0.001)
        assertFalse(profile.toReportText().contains("must-not-be-retained"))
        assertFalse(profile.toReportText().contains("SERIAL"))
        assertTrue(profile.toReportText().contains("设备名称：Xiaomi 17 Pro Max"))
        assertFalse(profile.toReportText().contains("2509FPN0BC"))
        assertFalse(profile.toReportText().contains("popsicle"))
        assertTrue(profile.toReportText().contains("内存规格：LPDDR5"))
        assertTrue(profile.toReportText().contains("存储厂商：SK hynix"))
        assertTrue(profile.toReportText().contains("存储型号：HN8T271EJKX152"))
    }

    @Test
    fun parsesMemoryAndStorageSuppliersFromAndroidBootLog() {
        val profile = DeviceProfileParser.parse(
            raw = """
                DDR Device Type = 0x9
                DDR Manufacturer ID = 0x01CE, DDR Rank0 = 1
                Total DDR Size = 16384 MB
                UFS INQUIRY ID = 0x06
                UFS Spec Version = 4.1
            """.trimIndent(),
            access = DeviceProfileAccess.STANDARD
        )

        val hardware = profile.hardwareSupplyInfo!!
        assertEquals("三星 (Samsung)", hardware.ramVendor)
        assertEquals("LPDDR5X", hardware.ramType)
        assertEquals("SK hynix", hardware.storageVendor)
        assertEquals("UFS 4.1", hardware.storageSpec)
    }

    @Test
    fun preservesUnknownSupplierIdsForSafeFeedback() {
        val profile = DeviceProfileParser.parse(
            raw = """
                MANUFACTURER=Xiaomi
                MODEL=2509FPN0BC
                ANDROID=17
                DDR Manufacturer ID = 0xBEEF, serial=must-not-be-retained
                UFS INQUIRY ID = 0x7A
            """.trimIndent(),
            access = DeviceProfileAccess.STANDARD
        )

        val hardware = profile.hardwareSupplyInfo!!
        assertEquals("JEDEC ID 0xBEEF", hardware.ramVendor)
        assertEquals("UFS inquiry ID 0x007A", hardware.storageVendor)
        val feedback = HardwareSupplierFeedback.format(
            deviceName = "Xiaomi 17 Pro Max",
            androidVersion = "17",
            hardware = hardware,
            appVersion = "0.2.0-preview"
        )!!
        assertTrue(feedback.contains("RAM 厂商：JEDEC ID 0xBEEF"))
        assertTrue(feedback.contains("ROM 厂商：UFS inquiry ID 0x007A"))
        assertFalse(feedback.contains("must-not-be-retained"))
        assertFalse(feedback.contains("serial="))
    }

    @Test
    fun zeroSupplierIdsRemainMissingInsteadOfBecomingFeedback() {
        val profile = DeviceProfileParser.parse(
            raw = "DDR Manufacturer ID = 0x0000\nUFS INQUIRY ID = 0x00",
            access = DeviceProfileAccess.STANDARD
        )

        assertNull(profile.hardwareSupplyInfo)
    }

    @Test
    fun malformedAndMissingNodesDegradeWithoutCrashing() {
        val raw = """
            MODEL=Unknown phone
            SOC=
            CPU_PRESENT=0-7
            POLICY=policy0|0 one 2||not-a-number|
            POLICY=broken
            THERMAL=cpu|not-a-number
            THERMAL=skin|45500
        """.trimIndent()

        val profile = DeviceProfileParser.parse(raw, DeviceProfileAccess.STANDARD)

        assertEquals("Unknown phone", profile.model)
        assertNull(profile.soc)
        assertEquals(1, profile.cpuPolicies.size)
        assertEquals(listOf(0, 2), profile.cpuPolicies.single().cpus)
        assertNull(profile.cpuPolicies.single().minFrequencyKhz)
        assertNull(profile.cpuPolicies.single().maxFrequencyKhz)
        assertEquals(1, profile.thermalSensors.size)
        assertEquals("skin", profile.thermalSensors.single().type)
        assertEquals(45.5, profile.thermalSensors.single().temperatureCelsius, 0.001)
        assertEquals(DeviceProfileAccess.STANDARD, profile.access)
    }

    @Test
    fun reportExplainsReadOnlyStateAndUnavailableFields() {
        val profile = DeviceProfileParser.parse(
            raw = "MODEL=2509FPN0BC\nDEVICE=popsicle\nSOC=SM8850",
            access = DeviceProfileAccess.STANDARD
        )

        val report = profile.toReportText()

        assertTrue(report.contains("只读"))
        assertTrue(report.contains("APA 未修改系统调度"))
        assertTrue(!report.contains("原厂调度"))
        assertEquals("未获取到", profile.toChipSchedulingDetails().scheduler)
        assertTrue(report.contains("标准权限"))
        assertTrue(report.contains("CPU 策略：设备未提供"))
        assertTrue(report.contains("温度节点：设备未提供"))
    }

    @Test
    fun createsCompactChipSchedulingDetailsForExpandableDeviceCard() {
        val profile = DeviceProfileParser.parse(
            raw = """
                SOC=SM8850
                KERNEL=6.12.69-android16-6
                CPU_PRESENT=0-7
                POLICY=policy0|0 1 2 3 4 5|384000|3628800|schedutil
                POLICY=policy6|6 7|768000|4608000|walt
                THERMAL=cpu-0-0-usr|42123
                THERMAL=battery|32000
            """.trimIndent(),
            access = DeviceProfileAccess.ROOT
        )

        val details = profile.toChipSchedulingDetails()

        assertEquals("SM8850", details.chipset)
        assertEquals("schedutil / walt", details.scheduler)
        assertEquals("Root 只读", details.access)
        assertEquals("CPU 0-7", details.cpuTopology)
        assertEquals(
            listOf(
                "policy0 · CPU 0,1,2,3,4,5 · 最高 3628 MHz",
                "policy6 · CPU 6,7 · 最高 4608 MHz"
            ),
            details.policySummaries
        )
        assertEquals("相关温度节点：2 个", details.thermalSummary)
        assertEquals("内核：6.12.69-android16-6", details.kernelSummary)
    }
}
