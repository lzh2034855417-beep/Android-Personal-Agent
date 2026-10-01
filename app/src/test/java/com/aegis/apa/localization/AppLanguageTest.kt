package com.aegis.apa.localization

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun storedEnglishLoadsEnglish() {
        assertEquals(AppLanguage.EN, AppLanguage.fromStoredValue("en"))
    }

    @Test
    fun storedChineseLoadsChinese() {
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromStoredValue("zh-CN"))
    }

    @Test
    fun invalidStoredLanguageFallsBackToChinese() {
        listOf(null, "garbage", "").forEach { storedValue ->
            assertEquals(
                "Unexpected language for stored value $storedValue",
                AppLanguage.ZH_CN,
                AppLanguage.fromStoredValue(storedValue)
            )
        }
    }
}
