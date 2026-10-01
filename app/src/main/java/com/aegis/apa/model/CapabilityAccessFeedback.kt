package com.aegis.apa.model

import com.aegis.apa.localization.AppLanguage

enum class ShizukuAccessState {
    NOT_INSTALLED,
    SERVICE_UNAVAILABLE,
    PERMISSION_REQUIRED,
    AUTHORIZED;

    companion object {
        fun resolve(
            isInstalled: Boolean,
            binderAlive: Boolean,
            permissionGranted: Boolean
        ): ShizukuAccessState = when {
            !isInstalled -> NOT_INSTALLED
            !binderAlive -> SERVICE_UNAVAILABLE
            permissionGranted -> AUTHORIZED
            else -> PERMISSION_REQUIRED
        }
    }
}

data class CapabilityAccessFeedback(
    val usageAccessGranted: Boolean,
    val shizukuAccessState: ShizukuAccessState,
    val rootAuthorized: Boolean
) {
    val usageActionLabel: String
        get() = usageActionLabel(AppLanguage.ZH_CN)
    fun usageActionLabel(language: AppLanguage): String = if (language == AppLanguage.EN) {
        if (usageAccessGranted) "Usage access granted" else "Authorize usage access"
    } else if (usageAccessGranted) "已获得使用情况访问" else "授权使用情况访问"
    val usageActionEnabled: Boolean get() = !usageAccessGranted

    val shizukuActionLabel: String
        get() = shizukuActionLabel(AppLanguage.ZH_CN)
    fun shizukuActionLabel(language: AppLanguage): String = if (language == AppLanguage.EN) {
        when (shizukuAccessState) {
            ShizukuAccessState.NOT_INSTALLED -> "Download Shizuku"
            ShizukuAccessState.SERVICE_UNAVAILABLE -> "Open Shizuku"
            ShizukuAccessState.PERMISSION_REQUIRED -> "Authorize APA in Shizuku"
            ShizukuAccessState.AUTHORIZED -> "Shizuku authorized"
        }
    } else when (shizukuAccessState) {
            ShizukuAccessState.NOT_INSTALLED -> "浏览器下载 Shizuku"
            ShizukuAccessState.SERVICE_UNAVAILABLE -> "打开 Shizuku"
            ShizukuAccessState.PERMISSION_REQUIRED -> "授权 APA 使用 Shizuku"
            ShizukuAccessState.AUTHORIZED -> "Shizuku 已授权"
        }
    val shizukuActionEnabled: Boolean get() = shizukuAccessState != ShizukuAccessState.AUTHORIZED

    val rootActionLabel: String get() = rootActionLabel(AppLanguage.ZH_CN)
    fun rootActionLabel(language: AppLanguage): String = if (language == AppLanguage.EN) {
        if (rootAuthorized) "Root authorized" else "Open Root manager"
    } else if (rootAuthorized) "Root 已授权" else "打开 Root 管理器"
    val rootActionEnabled: Boolean get() = !rootAuthorized

    companion object {
        fun hasRootEvidence(
            rootBatteryAttempted: Boolean,
            rootBatteryError: String?,
            profileUsedRoot: Boolean,
            diagnosticHasSuccessfulCommand: Boolean
        ): Boolean =
            (rootBatteryAttempted && rootBatteryError == null) ||
                profileUsedRoot ||
                diagnosticHasSuccessfulCommand
    }
}
