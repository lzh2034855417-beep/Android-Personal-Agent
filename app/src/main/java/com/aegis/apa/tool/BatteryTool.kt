package com.aegis.apa.tool

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryInfo(
    val level: Int?,
    val status: String,
    val currentMilliAmp: Int? = null,
    val remainingMilliAmpHour: Int? = null,
    val remainingMilliWattHour: Long? = null,
    val temperatureCelsius: Double? = null,
    val voltageMilliVolt: Int? = null,
    val health: String? = null,
    val plugged: String? = null,
    val technology: String? = null,
    val isPresent: Boolean? = null
) {
    val levelText: String get() = level?.let { "$it%" } ?: "未获取到"
}

object BatteryReportText {
    fun format(info: BatteryInfo): String = buildString {
        appendLine("电池温度：${info.temperatureCelsius?.let { "$it°C" } ?: "设备未提供"}")
        appendLine("电池电压：${info.voltageMilliVolt?.let { "$it mV" } ?: "设备未提供"}")
        appendLine("电池健康：${info.health ?: "设备未提供"}")
        appendLine("充电方式：${info.plugged ?: "设备未提供"}")
        appendLine("电池技术：${info.technology ?: "设备未提供"}")
    }
}

object BatteryPlugText {
    fun fromFlags(flags: Int): String? {
        if (flags < 0) return null
        if (flags == 0) return "未外接电源"
        val sources = buildList {
            if (flags and BatteryManager.BATTERY_PLUGGED_AC != 0) add("交流电")
            if (flags and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
            if (flags and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("无线充电")
        }
        return sources.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }
}

object BatteryReading {
    fun percentage(level: Int, scale: Int): Int? =
        if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null

    fun status(value: Int): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "正在充电"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "正在放电"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> "未获取到"
    }
}

object BatteryTool {
    fun read(context: Context): BatteryInfo {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = BatteryReading.percentage(level, scale)
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val currentMicroAmp = runCatching { batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: Int.MIN_VALUE
        val chargeMicroAmpHour = runCatching { batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }.getOrNull() ?: Int.MIN_VALUE
        val energyNanoWattHour = runCatching { batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }.getOrNull() ?: Long.MIN_VALUE

        val currentMilliAmp = currentMicroAmp
            .takeIf { it != Int.MIN_VALUE }
            ?.div(1_000)
        val remainingMilliAmpHour = chargeMicroAmpHour
            .takeIf { it != Int.MIN_VALUE }
            ?.div(1_000)
        val remainingMilliWattHour = energyNanoWattHour
            .takeIf { it != Long.MIN_VALUE }
            ?.div(1_000_000)

        val status = BatteryReading.status(batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1)
        val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "电压异常"
            BatteryManager.BATTERY_HEALTH_DEAD -> "无响应"
            BatteryManager.BATTERY_HEALTH_COLD -> "温度过低"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "状态异常"
            else -> null
        }
        val plugged = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            ?.let(BatteryPlugText::fromFlags)
        val temperatureCelsius = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10.0)
        val voltageMilliVolt = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
        return BatteryInfo(
            level = percentage,
            status = status,
            currentMilliAmp = currentMilliAmp,
            remainingMilliAmpHour = remainingMilliAmpHour,
            remainingMilliWattHour = remainingMilliWattHour,
            temperatureCelsius = temperatureCelsius,
            voltageMilliVolt = voltageMilliVolt,
            health = health,
            plugged = plugged,
            technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { null },
            isPresent = batteryIntent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
        )
    }
}
