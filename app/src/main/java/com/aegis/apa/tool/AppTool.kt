package com.aegis.apa.tool

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val name: String,
    val packageName: String
)

data class AppDetails(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystemApp: Boolean
)

data class DetectedApp(
    val displayName: String,
    val category: AppCategory,
    val packageName: String?,
    val isInstalled: Boolean
)

enum class AppCategory(val displayName: String) {
    ROOT_AND_FRAMEWORK("Root 与框架"),
    COMMON("常规应用")
}

private data class KnownApp(
    val displayName: String,
    val category: AppCategory,
    val packageNames: List<String>,
    val labelHints: List<String> = emptyList()
)

object AppTool {
    private val knownApps = listOf(
        KnownApp("KernelSU", AppCategory.ROOT_AND_FRAMEWORK, listOf("me.weishu.kernelsu", "me.weishu.kernelsu.next")),
        KnownApp("Magisk", AppCategory.ROOT_AND_FRAMEWORK, listOf("com.topjohnwu.magisk")),
        KnownApp("MT Manager", AppCategory.ROOT_AND_FRAMEWORK, listOf("bin.mt.plus", "bin.mt.plus.canary"), listOf("MT Manager", "MT\u7ba1\u7406\u5668")),
        KnownApp("LSPosed", AppCategory.ROOT_AND_FRAMEWORK, listOf("org.lsposed.manager", "org.lsposed.lspatch"), listOf("LSPosed", "LSPatch")),
        KnownApp("谷歌 Play 商店", AppCategory.COMMON, listOf("com.android.vending")),
        KnownApp("小米应用商店", AppCategory.COMMON, listOf("com.xiaomi.market")),
        KnownApp("小米社区", AppCategory.COMMON, listOf("com.xiaomi.vipaccount", "com.xiaomi.community"), listOf("Xiaomi Community", "Mi Community", "\u5c0f\u7c73\u793e\u533a"))
    )

    fun readLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .map { resolveInfo ->
                InstalledApp(
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }

    fun detectKnownApps(context: Context): List<DetectedApp> {
        val packageManager = context.packageManager
        val launchableApps = readLaunchableApps(context)
        return knownApps.map { app ->
            val installedPackage = app.packageNames.firstOrNull { packageName ->
                runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
            } ?: launchableApps.firstOrNull { launchableApp ->
                launchableApp.packageName in app.packageNames ||
                    app.labelHints.any { hint ->
                        launchableApp.name.equals(hint, ignoreCase = true)
                    }
            }?.packageName
            DetectedApp(
                displayName = app.displayName,
                category = app.category,
                packageName = installedPackage,
                isInstalled = installedPackage != null
            )
        }
    }

    fun readDetails(context: Context, packageName: String): AppDetails? = runCatching {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val applicationInfo = requireNotNull(packageInfo.applicationInfo) {
            "Application information is unavailable"
        }

        AppDetails(
            name = applicationInfo.loadLabel(packageManager).toString(),
            packageName = packageName,
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            },
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        )
    }.getOrNull()
}
