package com.aegis.apa

import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareSupplierFeedbackTest {
    @Test
    fun englishFeedbackTranslatesLabelsAndPreservesRawHardwareEvidence() {
        val feedback = HardwareSupplierFeedback.format(
            deviceName = "Xiaomi 17 Pro Max",
            androidVersion = "17",
            hardware = HardwareSupplyInfo(
                ramVendor = "NewChip Labs",
                ramType = "LPDDR5X",
                storageVendor = "SK hynix",
                storageModel = "HN8T271EJKX152",
                storageSpec = "UFS 4.1",
                source = HardwareSupplySource.COMBINED
            ),
            appVersion = "0.2.0-preview",
            language = AppLanguage.EN
        )

        assertEquals(
            "APA hardware recognition feedback\n" +
                "Device: Xiaomi 17 Pro Max\n" +
                "Android: 17\n" +
                "RAM vendor: NewChip Labs\n" +
                "RAM specification: LPDDR5X\n" +
                "ROM vendor: SK hynix\n" +
                "ROM model: HN8T271EJKX152\n" +
                "ROM specification: UFS 4.1\n" +
                "Source: Android bugreport + system nodes\n" +
                "APA: 0.2.0-preview\n" +
                "Privacy: no serial number, account, app list, original system report, or API key is included.",
            feedback
        )
    }

    @Test
    fun unknownSupplierProducesSanitizedCopyText() {
        val feedback = HardwareSupplierFeedback.format(
            deviceName = "Xiaomi 17 Pro Max",
            androidVersion = "17",
            hardware = HardwareSupplyInfo(
                ramVendor = "NewChip Labs",
                ramType = "LPDDR5X",
                storageVendor = "Samsung",
                storageModel = "KLUEG8UHDB-C2D1",
                storageSpec = "UFS 4.1",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            ),
            appVersion = "0.2.0-preview"
        )

        assertEquals(
            "APA 硬件识别反馈\n" +
                "设备：Xiaomi 17 Pro Max\n" +
                "Android：17\n" +
                "RAM 厂商：NewChip Labs\n" +
                "RAM 规格：LPDDR5X\n" +
                "ROM 厂商：Samsung\n" +
                "ROM 型号：KLUEG8UHDB-C2D1\n" +
                "ROM 规格：UFS 4.1\n" +
                "来源：Android bugreport\n" +
                "APA：0.2.0-preview\n" +
                "说明：不含序列号、账号、应用列表、原始系统报告或密钥。",
            feedback
        )
    }

    @Test
    fun missingOrKnownSuppliersDoNotOfferFeedback() {
        val missing = HardwareSupplierFeedback.format("Phone", "17", null, "0.2.0-preview")
        val known = HardwareSupplierFeedback.format(
            deviceName = "Phone",
            androidVersion = "17",
            hardware = HardwareSupplyInfo(
                ramVendor = "Samsung",
                storageVendor = "SK hynix",
                source = HardwareSupplySource.ROOT_SYSFS
            ),
            appVersion = "0.2.0-preview"
        )

        assertNull(missing)
        assertNull(known)
    }

    @Test
    fun presentHardwareWithMissingVendorsDoesNotOfferFeedback() {
        val bothMissing = HardwareSupplierFeedback.format(
            deviceName = "Phone",
            androidVersion = "17",
            hardware = HardwareSupplyInfo(
                ramType = "LPDDR5X",
                storageSpec = "UFS 4.1",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            ),
            appVersion = "0.2.0-preview"
        )
        val oneKnownOneMissing = HardwareSupplierFeedback.format(
            deviceName = "Phone",
            androidVersion = "17",
            hardware = HardwareSupplyInfo(
                ramVendor = "Samsung",
                storageSpec = "UFS 4.1",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            ),
            appVersion = "0.2.0-preview"
        )

        assertNull(bothMissing)
        assertNull(oneKnownOneMissing)
    }
}
