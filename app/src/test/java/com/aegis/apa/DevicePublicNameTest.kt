package com.aegis.apa

import com.aegis.apa.tool.publicDeviceName
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePublicNameTest {
    @Test
    fun resolvesCurrentXiaomiModelCodeToPublicRetailName() {
        assertEquals(
            "Xiaomi 17 Pro Max",
            publicDeviceName(manufacturer = "Xiaomi", modelCode = "2509FPN0BC")
        )
    }

    @Test
    fun hidesUnknownOpaqueModelCodeInsteadOfDisplayingIt() {
        assertEquals(
            "Xiaomi 未识别机型",
            publicDeviceName(manufacturer = "Xiaomi", modelCode = "ABC123XYZ")
        )
    }
}
