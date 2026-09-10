package com.aegis.apa.model

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

