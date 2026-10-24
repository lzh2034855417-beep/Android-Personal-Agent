package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.DetectedApp

object AppReportBuilder {
    fun build(
        launchableAppCount: Int,
        detectedApps: List<DetectedApp>,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String = buildString {
        if (language == AppLanguage.EN) {
            appendLine("Launchable apps: $launchableAppCount")
            detectedApps.forEach { app ->
                appendLine(
                    if (app.isInstalled) {
                        "${app.displayName}: Installed (${app.packageName ?: "package name unavailable"})"
                    } else {
                        "${app.displayName}: Not detected"
                    }
                )
            }
        } else {
            appendLine("可启动应用数量：$launchableAppCount")
            detectedApps.forEach { app ->
                appendLine(
                    if (app.isInstalled) {
                        "${app.displayName}：已安装（${app.packageName ?: "包名未知"}）"
                    } else {
                        "${app.displayName}：未检测到"
                    }
                )
            }
        }
    }
}
