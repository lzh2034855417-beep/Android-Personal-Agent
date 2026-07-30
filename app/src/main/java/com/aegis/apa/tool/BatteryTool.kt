package com.aegis.apa.tool

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryInfo(
    val level: Int,
    val status: String,
    val currentMilliAmp: Int? = null,
    val remainingMilliAmpHour: Int? = null,
    val remainingMilliWattHour: Long? = null
)

object BatteryTool {
    fun read(context: Context): BatteryInfo {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val currentMicroAmp = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeMicroAmpHour = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energyNanoWattHour = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        val currentMilliAmp = currentMicroAmp
            .takeIf { it != Int.MIN_VALUE }
            ?.div(1_000)
        val remainingMilliAmpHour = chargeMicroAmpHour
            .takeIf { it != Int.MIN_VALUE }
            ?.div(1_000)
        val remainingMilliWattHour = energyNanoWattHour
            .takeIf { it != Long.MIN_VALUE }
            ?.div(1_000_000)

        val status = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "正在充电"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            else -> "未充电"
        }
        return BatteryInfo(
            level = percentage,
            status = status,
            currentMilliAmp = currentMilliAmp,
            remainingMilliAmpHour = remainingMilliAmpHour,
            remainingMilliWattHour = remainingMilliWattHour
        )
    }
}
