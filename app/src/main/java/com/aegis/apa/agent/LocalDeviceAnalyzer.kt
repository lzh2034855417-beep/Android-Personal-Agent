package com.aegis.apa.agent

object LocalDeviceAnalyzer {
    fun analyze(context: DeviceContext): AgentReport {
        val findings = mutableListOf<String>()
        val ramRatio = context.availableRamBytes.toDouble() / context.totalRamBytes
        val storageRatio = context.availableStorageBytes.toDouble() / context.totalStorageBytes

        if (context.batteryLevel <= 20) {
            findings += "电量低于 20%，建议及时充电。"
        } else {
            findings += "当前电量处于可用范围。"
        }

        if (ramRatio < 0.2) {
            findings += "可用 RAM 较低，后台应用可能影响流畅度。"
        } else {
            findings += "RAM 可用空间正常。"
        }

        if (storageRatio < 0.1) {
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
}
