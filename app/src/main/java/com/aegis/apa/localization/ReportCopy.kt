package com.aegis.apa.localization

object ReportCopy {
    fun pending(language: AppLanguage) = if (language == AppLanguage.EN) "Pending" else "待读取"
}
