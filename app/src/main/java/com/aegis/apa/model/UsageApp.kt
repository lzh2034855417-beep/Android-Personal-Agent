package com.aegis.apa.model

import com.aegis.apa.localization.AppLanguage

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UsageApp(
    val packageName: String,
    val label: String,
    val foregroundTimeMillis: Long
)

data class UsageSummary(
    val accessGranted: Boolean,
    val foregroundTimeMillis: Long?,
    val topApps: List<UsageApp>,
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val isPartial: Boolean = false
) {
    val rangeText: String? get() = rangeText(AppLanguage.ZH_CN)

    fun rangeText(language: AppLanguage): String? {
        val start = startTimeMillis ?: return null
        val end = endTimeMillis ?: return null
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(ZoneId.systemDefault())
        val separator = if (language == AppLanguage.EN) " to " else " 至 "
        return "${formatter.format(Instant.ofEpochMilli(start))}$separator${formatter.format(Instant.ofEpochMilli(end))}"
    }
}

