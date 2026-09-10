package com.aegis.apa.agent

import com.aegis.apa.model.BatteryInfo
import com.aegis.apa.tool.BatteryReportText
import com.aegis.apa.model.DeviceInfo
import com.aegis.apa.model.DisplayInfo
import com.aegis.apa.tool.DisplayReportText
import com.aegis.apa.model.RamInfo
import com.aegis.apa.model.StorageInfo
import com.aegis.apa.model.UsageSummary

object Level0ReportBuilder {
    fun build(
        sampledAt: String,
        deviceInfo: DeviceInfo,
        batteryInfo: BatteryInfo,
        displayInfo: DisplayInfo,
        ramInfo: RamInfo,
        storageInfo: StorageInfo,
        usageSummary: UsageSummary,
        securityPatch: String?,
        socName: String?,
        supportedAbis: List<String>,
        includeUsageReport: Boolean = false
    ): String = buildString {
        appendLine("【本次采样范围】")
        appendLine("采样时间：$sampledAt")
        appendLine("数据权限：普通 Android API（Level 0）")
        appendLine("说明：以下为本次即时读取；未提供字段不作推断。")
        appendLine()

        appendLine("【设备与系统】")
        appendLine("设备：${deviceInfo.model}")
        appendLine("Android：${deviceInfo.androidVersion}")
        appendLine("安全补丁：${securityPatch.orUnavailable()}")
        appendLine("芯片：${socName.orUnavailable()}")
        appendLine("支持 ABI：${supportedAbis.takeIf { it.isNotEmpty() }?.joinToString() ?: "设备未提供"}")
        appendLine()

        appendLine("【屏幕体验】")
        append(DisplayReportText.format(displayInfo))
        appendLine()

        appendLine("【电池即时状态】")
        appendLine("当前电量：${batteryInfo.levelText}")
        appendLine("充电状态：${batteryInfo.status}")
        appendLine("瞬时电流：${batteryInfo.currentMilliAmp?.let { "$it mA" } ?: "设备未提供"}")
        appendLine("剩余电量：${batteryInfo.remainingMilliAmpHour?.let { "$it mAh" } ?: "设备未提供"}")
        appendLine("剩余能量：${batteryInfo.remainingMilliWattHour?.let { "$it mWh" } ?: "设备未提供"}")
        append(BatteryReportText.format(batteryInfo))
        appendLine()

        appendLine("【资源状态】")
        appendLine("内存：${formatBytes(ramInfo.availableBytes)} 可用 / ${formatBytes(ramInfo.totalBytes)} 总量")
        appendLine("系统低内存标记：${if (ramInfo.isLowMemory) "是" else "否"}")
        appendLine("存储：${formatBytes(storageInfo.availableBytes)} 可用 / ${formatBytes(storageInfo.totalBytes)} 总量")
        appendLine()

        appendLine("【可选使用习惯】")
        if (!includeUsageReport) {
            appendLine("本次未选择，不发送使用习惯。")
            return@buildString
        }
        usageSummary.rangeText?.let { appendLine("统计范围：$it") }
        if (usageSummary.isPartial) appendLine("数据限制：部分前后台事件缺失，时长可能偏低。")
        if (!usageSummary.accessGranted) {
            appendLine("未授权，不影响基础报告。")
            appendLine("如需汇总当天应用前台使用时长，请在系统设置中为 APA 开启“使用情况访问权限”。")
        } else {
            appendLine("当天应用前台使用时长合计：${usageSummary.foregroundTimeMillis?.let(::formatDuration) ?: "设备未提供"}")
            appendLine("说明：这是按系统前后台事件估算的应用前台时长，记录可能缺失或延迟；多窗口应用的时长可能重叠，不等同于精确亮屏时长，也不能推断应用内容或后台行为。")
            if (usageSummary.topApps.isEmpty()) {
                appendLine("前台使用排行：设备未提供")
            } else {
                usageSummary.topApps.forEachIndexed { index, app ->
                    appendLine("${index + 1}. ${app.label}：${formatDuration(app.foregroundTimeMillis)}")
                }
            }
        }
    }

    private fun String?.orUnavailable(): String = this?.takeIf { it.isNotBlank() } ?: "设备未提供"

    private fun formatBytes(bytes: Long): String {
        val gib = 1024L * 1024L * 1024L
        val value = bytes.toDouble() / gib
        return String.format(java.util.Locale.US, "%.1f GB", value)
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalMinutes = milliseconds / 60_000
        return "${totalMinutes / 60} 小时 ${totalMinutes % 60} 分"
    }
}
