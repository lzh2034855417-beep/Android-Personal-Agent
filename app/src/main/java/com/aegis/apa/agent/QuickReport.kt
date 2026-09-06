package com.aegis.apa.agent

import com.aegis.apa.tool.BatteryInfo

object QuickReport {
    fun explain(topic: String, battery: BatteryInfo): String = buildString {
        appendLine("当前电量：${battery.levelText} · ${battery.status}")
        appendLine("电池温度：${battery.temperatureCelsius?.let { "$it°C" } ?: "未获取到"}")
        if (topic == "发热") {
            appendLine("这是电池传感器的一次读数，不能代表 CPU 或机身表面温度。")
            append("可在相同环境下分别记录待机、使用和充电时的温度，结合当时的任务比较。单次读数不能确定发热原因。")
        } else {
            appendLine("当前快照不足以判断电池老化程度或是否需要更换电池。")
            append("下一步：结合循环次数、设计与满充容量，以及多次相似使用条件下的续航记录。")
        }
    }.trim()
}
