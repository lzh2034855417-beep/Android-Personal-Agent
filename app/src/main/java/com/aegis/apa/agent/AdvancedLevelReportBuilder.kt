package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.RootStatus
import com.aegis.apa.model.ShizukuAccessState
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.RootBatteryInfo

object AdvancedLevelReportBuilder {
    fun buildLevel1(
        sampledAt: String,
        rootStatus: RootStatus,
        shizukuAccessState: ShizukuAccessState = ShizukuAccessState.NOT_INSTALLED,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String = if (language == AppLanguage.EN) buildString {
        appendLine("Sample time: $sampledAt")
        appendLine("Shizuku app: ${if (rootStatus.isShizukuInstalled) "Installed" else "Not detected"}")
        appendLine("Shizuku service: ${shizukuServiceEnglish(shizukuAccessState)}")
        appendLine("Shizuku authorization: ${shizukuAuthorizationEnglish(shizukuAccessState)}")
    } else buildString {
        appendLine("采样时间：$sampledAt")
        appendLine("Shizuku 应用：${if (rootStatus.isShizukuInstalled) "已安装" else "未检测到"}")
        appendLine("Shizuku 服务：${shizukuServiceChinese(shizukuAccessState)}")
        appendLine("Shizuku 授权：${shizukuAuthorizationChinese(shizukuAccessState)}")
    }

    private fun shizukuServiceEnglish(state: ShizukuAccessState): String = when (state) {
        ShizukuAccessState.AUTHORIZED, ShizukuAccessState.PERMISSION_REQUIRED -> "Connected"
        ShizukuAccessState.SERVICE_UNAVAILABLE -> "Not connected"
        ShizukuAccessState.NOT_INSTALLED -> "Unavailable"
    }

    private fun shizukuAuthorizationEnglish(state: ShizukuAccessState): String = when (state) {
        ShizukuAccessState.AUTHORIZED -> "Granted"
        ShizukuAccessState.PERMISSION_REQUIRED -> "Permission required"
        ShizukuAccessState.SERVICE_UNAVAILABLE -> "Unavailable while the service is stopped"
        ShizukuAccessState.NOT_INSTALLED -> "Unavailable"
    }

    private fun shizukuServiceChinese(state: ShizukuAccessState): String = when (state) {
        ShizukuAccessState.AUTHORIZED, ShizukuAccessState.PERMISSION_REQUIRED -> "已连接"
        ShizukuAccessState.SERVICE_UNAVAILABLE -> "未连接"
        ShizukuAccessState.NOT_INSTALLED -> "不可用"
    }

    private fun shizukuAuthorizationChinese(state: ShizukuAccessState): String = when (state) {
        ShizukuAccessState.AUTHORIZED -> "已授权"
        ShizukuAccessState.PERMISSION_REQUIRED -> "需要授权"
        ShizukuAccessState.SERVICE_UNAVAILABLE -> "服务未启动，无法确认"
        ShizukuAccessState.NOT_INSTALLED -> "不可用"
    }

    fun buildLevel2(
        sampledAt: String,
        rootStatus: RootStatus,
        rootBatteryInfo: RootBatteryInfo?,
        deviceProfile: DeviceProfileSnapshot?,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String = if (language == AppLanguage.EN) buildEnglishLevel2(
        sampledAt, rootStatus, rootBatteryInfo, deviceProfile
    ) else buildChineseLevel2(sampledAt, rootStatus, rootBatteryInfo, deviceProfile)

    private fun buildEnglishLevel2(
        sampledAt: String,
        rootStatus: RootStatus,
        rootBatteryInfo: RootBatteryInfo?,
        deviceProfile: DeviceProfileSnapshot?
    ): String = buildString {
        appendLine("Sample time: $sampledAt")
        appendLine("su interface: ${if (rootStatus.hasSuBinary) "Detected" else "Not detected"}")
        appendLine("KernelSU manager: ${if (rootStatus.isKernelSuManagerInstalled) "Installed" else "Not detected"}")
        appendLine("Magisk manager: ${if (rootStatus.isMagiskManagerInstalled) "Installed" else "Not detected"}")
        when {
            rootBatteryInfo == null -> appendLine("Advanced Root battery data: Not read for this request")
            rootBatteryInfo.error != null -> appendLine("Advanced Root battery data: ${rootBatteryErrorEnglish(rootBatteryInfo.error)}")
            else -> {
                appendLine("Advanced battery sample: ${rootBatteryInfo.sampledAt} (refresh manually on the Capabilities page)")
                appendLine("Design capacity: ${rootBatteryInfo.designCapacityMah?.let { "$it mAh" } ?: "Unavailable"}")
                appendLine("Full-charge capacity: ${rootBatteryInfo.fullChargeCapacityMah?.let { "$it mAh" } ?: "Unavailable"}")
                appendLine("Cycle count: ${rootBatteryInfo.cycleCount ?: "Unavailable"}")
                appendLine("Instant current: ${rootBatteryInfo.currentMilliAmp?.let { "$it mA" } ?: "Unavailable"}")
                appendLine("Voltage: ${rootBatteryInfo.voltageMilliVolt?.let { "$it mV" } ?: "Unavailable"}")
                appendLine("Temperature: ${rootBatteryInfo.temperatureCelsius?.let { "$it°C" } ?: "Unavailable"}")
            }
        }
        if (deviceProfile == null) {
            appendLine("Scheduling profile: Not read for this request; APA did not modify system scheduling")
        } else {
            appendLine()
            append(deviceProfile.toReportText(AppLanguage.EN))
        }
    }

    private fun buildChineseLevel2(
        sampledAt: String,
        rootStatus: RootStatus,
        rootBatteryInfo: RootBatteryInfo?,
        deviceProfile: DeviceProfileSnapshot?
    ): String = buildString {
        appendLine("采样时间：$sampledAt")
        appendLine("su 接口：${if (rootStatus.hasSuBinary) "检测到" else "未检测到"}")
        appendLine("KernelSU 管理器：${if (rootStatus.isKernelSuManagerInstalled) "已安装" else "未检测到"}")
        appendLine("Magisk 管理器：${if (rootStatus.isMagiskManagerInstalled) "已安装" else "未检测到"}")
        when {
            rootBatteryInfo == null -> appendLine("Root 高级电池信息：本次尚未读取")
            rootBatteryInfo.error != null -> appendLine("Root 高级电池信息：${rootBatteryInfo.error}")
            else -> {
                appendLine("高级电池采样时间：${rootBatteryInfo.sampledAt}（需在能力页手动重读）")
                appendLine("设计容量：${rootBatteryInfo.designCapacityMah?.let { "$it mAh" } ?: "设备未提供"}")
                appendLine("满充容量：${rootBatteryInfo.fullChargeCapacityMah?.let { "$it mAh" } ?: "设备未提供"}")
                appendLine("循环次数：${rootBatteryInfo.cycleCount ?: "设备未提供"}")
                appendLine("瞬时电流：${rootBatteryInfo.currentMilliAmp?.let { "$it mA" } ?: "设备未提供"}")
                appendLine("电压：${rootBatteryInfo.voltageMilliVolt?.let { "$it mV" } ?: "设备未提供"}")
                appendLine("温度：${rootBatteryInfo.temperatureCelsius?.let { "$it°C" } ?: "设备未提供"}")
            }
        }
        if (deviceProfile == null) {
            appendLine("调度档案：本次尚未读取；APA 未修改系统调度")
        } else {
            appendLine()
            append(deviceProfile.toReportText(AppLanguage.ZH_CN))
        }
    }

    private fun rootBatteryErrorEnglish(error: String): String = when {
        error.contains("超时") -> "Root authorization timed out."
        error.contains("拒绝") || error.contains("失败") -> "Root authorization was denied or the command failed."
        else -> "Root battery data could not be read."
    }
}
