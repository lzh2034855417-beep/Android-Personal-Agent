package com.aegis.apa.tool

import android.os.Build

data class DeviceInfo(
    val model: String,
    val androidVersion: String
)

private val publicNamesByModelCode = mapOf(
    "2509FPN0BC" to "Xiaomi 17 Pro Max"
)

/** Returns a user-facing retail name and never exposes an opaque internal model code. */
fun publicDeviceName(manufacturer: String?, modelCode: String?): String {
    val brand = manufacturer
        ?.trim()
        ?.replaceFirstChar { it.uppercase() }
        ?.ifBlank { null }
        ?: "Android"
    val normalizedCode = modelCode?.trim()?.uppercase().orEmpty()

    return publicNamesByModelCode[normalizedCode] ?: "$brand 未识别机型"
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
