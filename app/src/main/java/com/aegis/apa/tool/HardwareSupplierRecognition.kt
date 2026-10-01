package com.aegis.apa.tool

import java.util.Locale

/** Tracks whether a detected supplier name is already covered by APA's recognition aliases. */
object HardwareSupplierRecognition {
    fun needsReview(hardware: HardwareSupplyInfo?): Boolean {
        val ramVendor = hardware?.ramVendor?.trim()?.takeIf(String::isNotEmpty)
        val storageVendor = hardware?.storageVendor?.trim()?.takeIf(String::isNotEmpty)
        return ramVendor == null || storageVendor == null ||
            !isKnownRamSupplier(ramVendor) || !isKnownStorageSupplier(storageVendor)
    }

    fun hasUnrecognizedSupplier(hardware: HardwareSupplyInfo?): Boolean {
        val ramVendor = hardware?.ramVendor?.trim()?.takeIf(String::isNotEmpty)
        val storageVendor = hardware?.storageVendor?.trim()?.takeIf(String::isNotEmpty)
        return (ramVendor != null && !isKnownRamSupplier(ramVendor)) ||
            (storageVendor != null && !isKnownStorageSupplier(storageVendor))
    }

    private fun isKnownRamSupplier(vendor: String): Boolean =
        normalizeVendor(vendor) in KNOWN_RAM_SUPPLIERS

    private fun isKnownStorageSupplier(vendor: String): Boolean =
        normalizeVendor(vendor) in KNOWN_STORAGE_SUPPLIERS

    private fun normalizeVendor(value: String): String =
        value.lowercase(Locale.ROOT).replace(NON_NAME_CHARACTERS, "")

    private val NON_NAME_CHARACTERS = Regex("[^\\p{L}\\p{N}]")
    private val COMMON_SUPPLIERS = setOf(
        "samsung", "samsungelectronics", "三星", "三星电子", "三星samsung",
        "hynix", "skhynix", "skhynixinc", "海力士", "海力士skhynix",
        "micron", "microntechnology", "美光", "美光micron"
    )
    private val KNOWN_RAM_SUPPLIERS = COMMON_SUPPLIERS + setOf(
        "cxmt", "长鑫", "长鑫存储", "长鑫cxmt", "长鑫存储cxmt"
    )
    private val KNOWN_STORAGE_SUPPLIERS = COMMON_SUPPLIERS + setOf(
        "kioxia", "铠侠", "铠侠kioxia", "toshiba", "东芝", "东芝toshiba",
        "ymtc", "长江存储", "长江存储ymtc",
        "xbstor", "飞存闪拓", "飞存闪拓xbstor"
    )
}
