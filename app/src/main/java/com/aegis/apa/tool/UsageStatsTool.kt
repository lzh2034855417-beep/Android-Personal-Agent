package com.aegis.apa.tool

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
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

object UsageSummaryBuilder {
    fun from(
        accessGranted: Boolean,
        appDurationsMillis: Map<String, Long>,
        labels: Map<String, String>,
        limit: Int = 5
    ): UsageSummary {
        if (!accessGranted) return UsageSummary(false, null, emptyList())

        val apps = appDurationsMillis
            .filterValues { it > 0L }
            .map { (packageName, foregroundTimeMillis) ->
                UsageApp(
                    packageName = packageName,
                    label = labels[packageName] ?: packageName,
                    foregroundTimeMillis = foregroundTimeMillis
                )
            }
            .sortedWith(compareByDescending<UsageApp> { it.foregroundTimeMillis }.thenBy { it.label })

        return UsageSummary(
            accessGranted = true,
            foregroundTimeMillis = if (appDurationsMillis.isEmpty()) null else apps.sumOf { it.foregroundTimeMillis },
            topApps = apps.take(limit)
        )
    }
}

object UsageStatsTool {
    fun read(context: Context): UsageSummary {
        if (!hasUsageAccess(context)) return UsageSummary(false, null, emptyList())

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return UsageSummary(true, null, emptyList())
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        // Read yesterday as well to recover observed sessions that cross midnight.
        // Missing history is never replaced by the aggregate API's wider time buckets.
        val lookback = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return try {
            val events = usageStatsManager.queryEvents(lookback, now)
                ?: return UsageSummary(true, null, emptyList(), startOfToday, now)
            val records = mutableListOf<UsageEventRecord>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(event)) break
                val kind = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUME
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.PAUSE
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.CLOSE_ALL
                    UsageEvents.Event.DEVICE_STARTUP -> UsageEventKind.RESET
                    else -> null
                } ?: continue
                records += UsageEventRecord(event.packageName.orEmpty(), event.className.orEmpty(), event.timeStamp, kind)
            }
            val result = UsageEventDurations.calculate(records, startOfToday, now)
            val labels = result.durations.keys.associateWith { packageName ->
                resolveLabel(context.packageManager, packageName)
            }
            if (!hasUsageAccess(context)) return UsageSummary(false, null, emptyList())
            UsageSummaryBuilder.from(true, result.durations, labels).copy(
                startTimeMillis = startOfToday, endTimeMillis = now, isPartial = result.isPartial
            )
        } catch (_: SecurityException) {
            UsageSummary(hasUsageAccess(context), null, emptyList(), startOfToday, now)
        } catch (_: RuntimeException) {
            UsageSummary(true, null, emptyList(), startOfToday, now)
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
            ?: return false
        return appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun resolveLabel(packageManager: PackageManager, packageName: String): String {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(packageManager)
                .toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
