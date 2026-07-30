package com.aegis.apa.tool

import android.os.Build

data class DeviceInfo(
    val model: String,
    val androidVersion: String
)

object DeviceInfoTool {
    fun read(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL

        return DeviceInfo(
            model = "$manufacturer $model",
            androidVersion = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"
        )
    }
}
