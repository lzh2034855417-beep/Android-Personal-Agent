package com.aegis.apa.model

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
    val rangeText: String? get() {
        val start = startTimeMillis ?: return null
        val end = endTimeMillis ?: return null
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(ZoneId.systemDefault())
        return "${formatter.format(Instant.ofEpochMilli(start))} 至 ${formatter.format(Instant.ofEpochMilli(end))}"
    }
}

