package com.aegis.apa.agent

import com.aegis.apa.model.BatteryInfo
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.DeviceUiCopy

object QuickReport {
    fun explain(topic: String, battery: BatteryInfo, language: AppLanguage = AppLanguage.ZH_CN): String = if (language == AppLanguage.EN) buildString {
        appendLine("Current charge: ${battery.levelText} · ${DeviceUiCopy.batteryStatus(battery.status, language)}")
        appendLine("Battery temperature: ${battery.temperatureCelsius?.let { "$it°C" } ?: "Unavailable"}")
        if (topic == "发热") {
            appendLine("This is one battery-sensor reading; it does not represent CPU or case-surface temperature.")
            append("Compare standby, active-use, and charging temperatures in the same environment. One reading cannot identify the cause of heat.")
        } else {
            append("Battery health requires cycle count plus design and full-charge capacity. A single snapshot is only a reference.")
        }
    }.trim() else buildString {
        appendLine("当前电量：${battery.levelText} · ${battery.status}")
        appendLine("电池温度：${battery.temperatureCelsius?.let { "$it°C" } ?: "未获取到"}")
        if (topic == "发热") {
            appendLine("这是电池传感器的一次读数，不能代表 CPU 或机身表面温度。")
            append("可在相同环境下分别记录待机、使用和充电时的温度，结合当时的任务比较。单次读数不能确定发热原因。")
        } else {
            append("电池健康需结合循环次数与设计/满充容量判断，单次快照仅供参考。")
        }
    }.trim()
}
