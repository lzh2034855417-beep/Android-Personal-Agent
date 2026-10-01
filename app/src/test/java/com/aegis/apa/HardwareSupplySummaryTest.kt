package com.aegis.apa

import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSupplySummaryTest {
    private val hardware = HardwareSupplyInfo(
        ramVendor = "三星 (Samsung)",
        ramType = "LPDDR5X",
        storageVendor = "SK hynix",
        storageModel = "HN8T271EJKX152",
        storageSpec = "UFS 4.1",
        source = HardwareSupplySource.ANDROID_BUGREPORT
    )

    @Test
    fun formatsConsumerCapacitiesForShareCard() {
        val summary = HardwareSupplySummary.format(
            hardware = hardware,
            totalRamBytes = 15_600_000_000L,
            totalStorageBytes = 510_000_000_000L
        )

        assertEquals(
            "RAM：16GB · 三星 (Samsung) · LPDDR5X\n" +
                "ROM：512GB · SK hynix · UFS 4.1 · HN8T271EJKX152",
            summary
        )
    }

    @Test
    fun emptyHardwareShowsPendingSupplierBadge() {
        val summary = HardwareSupplySummary.format(
            hardware = null,
            totalRamBytes = 16L * 1024 * 1024 * 1024,
            totalStorageBytes = 512L * 1024 * 1024 * 1024
        )

        assertEquals(
            "RAM：16GB · 待读取\n" +
                "ROM：512GB · 待读取",
            summary
        )
    }

    @Test
    fun englishSummaryUsesEnglishPunctuationAndPendingCopy() {
        val summary = HardwareSupplySummary.format(
            hardware = null,
            totalRamBytes = 16L * 1024 * 1024 * 1024,
            totalStorageBytes = 512L * 1024 * 1024 * 1024,
            language = AppLanguage.EN
        )

        assertEquals("RAM: 16GB · Pending\nROM: 512GB · Pending", summary)
    }

    @Test
    fun partialSupplierReadKeepsKnownRamAndPendingRom() {
        val imported = HardwareSupplyInfo(
            ramVendor = "三星 (Samsung)",
            source = HardwareSupplySource.ANDROID_BUGREPORT
        )
        val systemNodes = HardwareSupplyInfo(
            storageVendor = "SK hynix",
            storageModel = "HN8T271EJKX152",
            source = HardwareSupplySource.ROOT_SYSFS
        )

        val summary = HardwareSupplySummary.format(
            hardware = imported.mergeMissingFrom(systemNodes),
            totalRamBytes = 15_600_000_000L,
            totalStorageBytes = 510_000_000_000L
        )!!

        assertTrue(summary.contains("RAM：16GB · 三星 (Samsung)"))
        assertTrue(summary.contains("ROM：512GB · SK hynix · HN8T271EJKX152"))
    }

    @Test
    fun reportCardTextPlacesSupplierResultNearModelForEveryTopic() {
        val text = buildReportCardText(
            model = "Xiaomi 17 Pro Max",
            sampledAt = "2026-09-24 12:00:00 +08:00",
            topic = "电池",
            report = "当前电量：65%",
            hardwareSummary = "RAM：三星 (Samsung) · 16GB\nROM：SK hynix · 512GB"
        )

        assertTrue(text.contains("Xiaomi 17 Pro Max\n\nRAM：三星 (Samsung) · 16GB"))
        assertTrue(text.contains("ROM：SK hynix · 512GB\n\n电池 · 本地报告"))
        assertFalse(text.contains("null"))
        assertTrue(text.endsWith("APA · Android Personal Agent"))
        assertFalse(text.contains("github.com"))
        assertFalse(text.contains("项目："))
    }
}
