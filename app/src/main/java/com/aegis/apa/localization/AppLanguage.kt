package com.aegis.apa.localization

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val languageTag: String) {
    ZH_CN("zh-CN"),
    EN("en");

    companion object {
        fun fromStoredValue(value: String?): AppLanguage = when (value) {
            EN.languageTag -> EN
            ZH_CN.languageTag -> ZH_CN
            else -> ZH_CN
        }
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH_CN }
