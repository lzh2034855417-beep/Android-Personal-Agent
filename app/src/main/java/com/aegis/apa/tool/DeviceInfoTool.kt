package com.aegis.apa.tool

import android.os.Build
import de.boehrsi.devicemarketingnames.DeviceMarketingNames

data class DeviceInfo(
    val model: String,
    val androidVersion: String
)

private val publicNamesByModelCode = mapOf(
    "2509FPN0BC" to "Xiaomi 17 Pro Max"
)

/** Resolves a user-facing retail name locally; falls back to the system model when unknown. */
fun publicDeviceName(manufacturer: String?, modelCode: String?): String {
    val brand = manufacturer
        ?.trim()
        ?.replaceFirstChar { it.uppercase() }
        ?.ifBlank { null }
        ?: "Android"
    val hasManufacturer = manufacturer?.isNotBlank() == true
    val normalizedCode = modelCode?.trim()?.uppercase().orEmpty()

    val marketingName = publicNamesByModelCode[normalizedCode]
        ?: DeviceMarketingNames.getSingleNameFromModel(normalizedCode)
        .trim()
        .takeUnless { it.isEmpty() || it.equals(normalizedCode, ignoreCase = true) }

    if (marketingName != null) {
        return if (marketingName.startsWith(brand, ignoreCase = true)) {
            marketingName
        } else if (hasManufacturer) {
            "$brand $marketingName"
        } else {
            marketingName
        }
    }

    return if (normalizedCode.isBlank()) "$brand 未识别机型" else "$brand · 系统型号 $normalizedCode"
}

object DeviceInfoTool {
    fun read(): DeviceInfo {
        val model = publicDeviceName(Build.MANUFACTURER, Build.MODEL)

        return DeviceInfo(
            model = model,
            androidVersion = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"
        )
    }
}
