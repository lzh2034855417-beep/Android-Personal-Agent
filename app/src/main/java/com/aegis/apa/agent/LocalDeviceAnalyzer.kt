package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage

object LocalDeviceAnalyzer {
    fun analyze(context: DeviceContext, language: AppLanguage = AppLanguage.ZH_CN): AgentReport {
        if (language == AppLanguage.EN) return analyzeEnglish(context)
        val findings = mutableListOf<String>()
        val ramRatio = context.availableRamBytes.toDouble() / context.totalRamBytes
        val storageRatio = context.availableStorageBytes.toDouble() / context.totalStorageBytes

        if (context.batteryLevel == null) {
            findings += "电量未获取到，暂不判断电池状态。"
        } else if (context.batteryLevel <= 20) {
            findings += "电量低于 20%，建议及时充电。"
        } else {
            findings += "当前电量处于可用范围。"
        }

        if (context.totalRamBytes <= 0 || context.availableRamBytes !in 0..context.totalRamBytes) {
            findings += "RAM 未获取到有效读数，暂不判断。"
        } else if (ramRatio < 0.2) {
            findings += "可用 RAM 较低，后台应用可能影响流畅度。"
        } else {
            findings += "RAM 可用空间正常。"
        }

        if (context.totalStorageBytes <= 0 || context.availableStorageBytes !in 0..context.totalStorageBytes) {
            findings += "存储未获取到有效读数，暂不判断。"
        } else if (storageRatio < 0.1) {
            findings += "存储空间不足 10%，建议清理大文件或不常用应用。"
        } else {
            findings += "存储空间充足。"
        }

        findings += "已识别 ${context.launchableAppCount} 个可启动应用。"

        return AgentReport(
            summary = "${context.deviceModel} 的基础设备健康检查已完成。",
            findings = findings,
            source = "LOCAL BASELINE · 本地规则"
        )
    }

    private fun analyzeEnglish(context: DeviceContext): AgentReport {
        val findings = mutableListOf<String>()
        val ramRatio = context.availableRamBytes.toDouble() / context.totalRamBytes
        val storageRatio = context.availableStorageBytes.toDouble() / context.totalStorageBytes

        findings += when {
            context.batteryLevel == null -> "Battery level is unavailable, so battery status is not assessed."
            context.batteryLevel <= 20 -> "Battery level is below 20%; charge the phone soon."
            else -> "The current battery level is within a usable range."
        }
        findings += when {
            context.totalRamBytes <= 0 || context.availableRamBytes !in 0..context.totalRamBytes ->
                "RAM readings are invalid, so available memory is not assessed."
            ramRatio < 0.2 -> "Available RAM is low; background apps may affect responsiveness."
            else -> "Available RAM is within a normal range."
        }
        findings += when {
            context.totalStorageBytes <= 0 || context.availableStorageBytes !in 0..context.totalStorageBytes ->
                "Storage readings are invalid, so available storage is not assessed."
            storageRatio < 0.1 -> "Less than 10% of storage is available; remove large files or unused apps."
            else -> "Available storage is sufficient."
        }
        findings += "Detected ${context.launchableAppCount} launchable apps."

        return AgentReport(
            summary = "The basic device health check for ${context.deviceModel} is complete.",
            findings = findings,
            source = "LOCAL BASELINE"
        )
    }
}
