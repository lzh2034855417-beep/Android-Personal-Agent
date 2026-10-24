package com.aegis.apa

import com.aegis.apa.tool.HardwareSupplyCache
import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HardwareSupplyCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private fun cache() = HardwareSupplyCache(java.io.File(folder.root, "hardware"), "Xiaomi", "2509FPN0BC")
    private val local = HardwareSupplyInfo(ramVendor = "三星 (Samsung)", ramType = "LPDDR5",
        storageVendor = "SK hynix", storageModel = "HN8T271EJKX152", source = HardwareSupplySource.ROOT_SYSFS)

    @Test fun restartRestoresKnownSuppliersAndPartialReadsDoNotEraseThem() {
        assertTrue(cache().saveLocal(local))
        assertTrue(cache().saveLocal(HardwareSupplyInfo(storageVendor = "SK hynix", source = HardwareSupplySource.ROOT_SYSFS)))
        val restored = cache().load()
        assertEquals("三星 (Samsung)", restored?.ramVendor)
        assertEquals("LPDDR5", restored?.ramType)
        assertEquals("HN8T271EJKX152", restored?.storageModel)
        assertFalse(cache().saveLocal(null))
        assertEquals("三星 (Samsung)", cache().load()?.ramVendor)
    }

    @Test fun reportsNeedOwnershipConfirmationAndMatchingNonemptyDeviceHeaders() {
        val report = HardwareSupplyInfo(ramVendor = "美光 (Micron)", source = HardwareSupplySource.ANDROID_BUGREPORT)
        val headers = mapOf("ro.product.manufacturer" to "Xiaomi", "ro.product.model" to "2509FPN0BC")
        assertFalse(cache().saveReport(report, headers, confirmedCurrentDevice = false))
        assertFalse(cache().saveReport(report, headers + ("ro.product.model" to "another-phone"), true))
        assertFalse(cache().saveReport(report, emptyMap(), true))
        assertNull(cache().load())
        assertTrue(cache().saveReport(report, headers, true))
        assertEquals("美光 (Micron)", cache().load()?.ramVendor)
    }

    @Test fun foreignDeviceCacheAndOversizedOrCorruptFilesAreNotRestored() {
        assertTrue(cache().saveLocal(local))
        assertNull(HardwareSupplyCache(java.io.File(folder.root, "hardware"), "Xiaomi", "OTHER").load())
        java.io.File(folder.root, "hardware").writeText("x".repeat(5000))
        assertNull(cache().load())
        java.io.File(folder.root, "hardware").writeText("source=UNKNOWN\nmodel=2509FPN0BC")
        assertNull(cache().load())
    }

    @Test fun failedWriteDoesNotDestroyThePreviousCache() {
        assertTrue(cache().saveLocal(local))
        java.io.File(folder.root, "hardware.new").mkdir()
        assertFalse(cache().saveLocal(local.copy(ramVendor = "美光 (Micron)")))
        assertEquals("三星 (Samsung)", cache().load()?.ramVendor)
    }
}
