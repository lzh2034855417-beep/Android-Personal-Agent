package com.aegis.apa.tool

import android.os.Build
import com.aegis.apa.model.DeviceIdentifiers
import com.aegis.apa.model.DeviceInfo

object DeviceInfoTool {
    fun read(): DeviceInfo = DeviceInfo(
        identity = DeviceNameResolver.resolve(DeviceIdentifiers(
            Build.MANUFACTURER, Build.MODEL, Build.BRAND, Build.DEVICE
        )),
        androidRelease = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT
    )
}
