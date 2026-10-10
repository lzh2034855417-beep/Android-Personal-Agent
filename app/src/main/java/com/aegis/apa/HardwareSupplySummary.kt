package com.aegis.apa

import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.localization.AppLanguage
import java.util.Locale
import kotlin.math.roundToInt

object HardwareSupplySummary {
    private val ramTiers = listOf(2, 3, 4, 6, 8, 12, 16, 24, 32)
    private val storageTiers = listOf(32, 64, 128, 256, 512, 1024, 2048)

    fun format(
        hardware: HardwareSupplyInfo?,
        totalRamBytes: Long,
        totalStorageBytes: Long,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String? {
        val lines = mutableListOf<String>()
        lines += listOfNotNull(
            capacityLabel(totalRamBytes, ramTiers),
            if (hardware == null) unread(language)
            else if (hardware.ramVendor == null && hardware.ramType == null)
                if (language == AppLanguage.EN) "Vendor/type unknown (not provided by current data)"
                else "厂商／类型未知（当前读取结果未提供）"
            else hardware.ramVendor ?: if (language == AppLanguage.EN) "Vendor unknown" else "厂商未知",
            if (hardware == null || (hardware.ramVendor == null && hardware.ramType == null)) null
            else hardware.ramType ?: if (language == AppLanguage.EN) "Type unknown" else "类型未知"
        ).joinToString(" · ").let { if (language == AppLanguage.EN) "RAM: $it" else "RAM：$it" }
        lines += listOfNotNull(
            capacityLabel(totalStorageBytes, storageTiers),
            hardware?.storageVendor ?: if (hardware == null) unread(language)
            else if (language == AppLanguage.EN) "Vendor unknown (not provided by current data)"
            else "厂商未知（当前读取结果未提供）",
            hardware?.storageSpec,
            hardware?.storageModel
        ).joinToString(" · ").let { if (language == AppLanguage.EN) "ROM: $it" else "ROM：$it" }
        return lines.joinToString("\n")
    }

    private fun unread(language: AppLanguage) = if (language == AppLanguage.EN) "Not read" else "未读取"

    private fun capacityLabel(bytes: Long, tiers: List<Int>): String? {
        if (bytes <= 0) return null
        val gib = bytes.toDouble() / 1024 / 1024 / 1024
        val tier = tiers.firstOrNull { candidate ->
            gib <= candidate && gib >= candidate * 0.75
        }
        return if (tier != null) {
            "${tier}GB"
        } else {
            String.format(Locale.US, "%dGB", gib.roundToInt())
        }
    }
}
