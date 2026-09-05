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
    fun resolvesKnownSamsungModelToItsRetailNameFromOfflineDatabase() {
        assertEquals(
            "Samsung Galaxy S20",
            publicDeviceName(manufacturer = "Samsung", modelCode = "SM-G980F")
        )
    }

    @Test
    fun keepsSystemModelWhenOfflineDatabaseDoesNotKnowTheDevice() {
        assertEquals(
            "Xiaomi · 系统型号 ABC123XYZ",
            publicDeviceName(manufacturer = "Xiaomi", modelCode = "ABC123XYZ")
        )
    }
}
