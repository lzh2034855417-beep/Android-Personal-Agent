package com.aegis.apa

import com.aegis.apa.model.*
import com.aegis.apa.tool.DeviceNameResolver
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.time.Instant
import java.time.ZoneId

class DeviceIdentityTest {
    @Test fun curatedLookupNormalizesWithoutDestroyingRawIdentifiers() {
        val raw = DeviceIdentifiers(" xIaOmI ", "2509fpn0bc", "Xiaomi", "popsicle")
        val identity = DeviceNameResolver.resolve(raw)
        assertEquals("Xiaomi 17 Pro Max", identity.displayName)
        assertEquals(DeviceNameSource.CURATED, identity.nameSource)
        assertEquals(" xIaOmI ", identity.identifiers.manufacturer)
        assertEquals("2509fpn0bc", identity.identifiers.modelCode)
    }
    @Test fun lookupIsIndependentOfTurkishSystemLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val identity = DeviceNameResolver.resolve(DeviceIdentifiers("XIAOMI", "2509fpn0bc"))
            assertEquals("Xiaomi 17 Pro Max", identity.displayName)
        } finally { Locale.setDefault(before) }
    }
    @Test fun failingOfflineDatabasePreservesSystemModelAndSource() {
        val identity = DeviceNameResolver.resolve(DeviceIdentifiers("Acme", "Case-Sensitive")) { error("unavailable database") }
        assertEquals("Acme · 系统型号 Case-Sensitive", identity.displayName)
        assertEquals(DeviceNameSource.SYSTEM_MODEL, identity.nameSource)
    }
    @Test fun offlineNamesDoNotDuplicateBrand() {
        val identity = DeviceNameResolver.resolve(DeviceIdentifiers("Samsung", "SM-TEST")) { "Samsung Galaxy Test" }
        assertEquals("Samsung Galaxy Test", identity.displayName)
        assertEquals(DeviceNameSource.OFFLINE_DATABASE, identity.nameSource)
    }
    @Test fun missingModelDoesNotCallDatabaseOrGuessFromCodename() {
        val identity = DeviceNameResolver.resolve(DeviceIdentifiers(null, null, null, "popsicle")) { error("must not be consulted") }
        assertEquals("Android 未识别机型", identity.displayName)
        assertEquals(DeviceNameSource.UNAVAILABLE, identity.nameSource)
    }
    @Test fun timestampFormatsZoneWithoutChangingInstant() {
        val instant = Instant.parse("2026-09-09T00:00:00Z")
        assertEquals("2026-09-09 08:00:00 +08:00", SampleTime.format(instant, ZoneId.of("Asia/Shanghai")))
        assertEquals("2026-09-09 00:00:00 Z", SampleTime.format(instant, ZoneId.of("UTC")))
    }
}
