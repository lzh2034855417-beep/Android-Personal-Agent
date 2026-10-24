package com.aegis.apa.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLanguageStore {
    private const val PREFERENCES_NAME = "apa_language"
    private const val LANGUAGE_KEY = "language_tag"

    fun load(context: Context): AppLanguage = AppLanguage.fromStoredValue(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
    )

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language.languageTag)
            .apply()
    }
}

fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
