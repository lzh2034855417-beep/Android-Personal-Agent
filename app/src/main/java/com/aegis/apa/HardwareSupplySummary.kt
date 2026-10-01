package com.aegis.apa

import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.ReportCopy
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
            hardware?.ramVendor ?: ReportCopy.pending(language),
            hardware?.ramType
        ).joinToString(" · ").let { if (language == AppLanguage.EN) "RAM: $it" else "RAM：$it" }
        lines += listOfNotNull(
            capacityLabel(totalStorageBytes, storageTiers),
            hardware?.storageVendor ?: ReportCopy.pending(language),
            hardware?.storageSpec,
            hardware?.storageModel
        ).joinToString(" · ").let { if (language == AppLanguage.EN) "ROM: $it" else "ROM：$it" }
        return lines.joinToString("\n")
    }

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
