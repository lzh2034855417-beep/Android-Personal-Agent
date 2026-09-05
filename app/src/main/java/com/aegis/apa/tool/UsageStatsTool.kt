package com.aegis.apa.tool

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class UsageApp(
    val packageName: String,
    val label: String,
    val foregroundTimeMillis: Long
)

data class UsageSummary(
    val accessGranted: Boolean,
    val foregroundTimeMillis: Long?,
    val topApps: List<UsageApp>
)

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
            foregroundTimeMillis = apps.sumOf { it.foregroundTimeMillis },
            topApps = apps.take(limit)
        )
    }
}

object UsageStatsTool {
    fun read(context: Context): UsageSummary {
        if (!hasUsageAccess(context)) return UsageSummary(false, null, emptyList())

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return UsageSummary(true, 0L, emptyList())
        val now = System.currentTimeMillis()
        val startOfToday = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfToday, now)
        val durations = stats.mapValues { it.value.totalTimeInForeground }
        val labels = durations.keys.associateWith { packageName ->
            resolveLabel(context.packageManager, packageName)
        }
        return UsageSummaryBuilder.from(true, durations, labels)
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
