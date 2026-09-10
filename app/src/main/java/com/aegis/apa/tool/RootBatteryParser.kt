package com.aegis.apa.tool

object RootBatteryParser {
    fun parse(output: String): RootBatteryInfo {
        val values = output.lineSequence()
            .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0] to it[1].trim() }

        val result = RootBatteryInfo(
            designCapacityMah = values["charge_full_design"]?.toLongOrNull()?.takeIf { it >= 1_000 }?.div(1_000),
            fullChargeCapacityMah = values["charge_full"]?.toLongOrNull()?.takeIf { it >= 1_000 }?.div(1_000),
            cycleCount = values["cycle_count"]?.toLongOrNull()?.takeIf { it >= 0 },
            currentMilliAmp = values["current_now"]?.toLongOrNull()?.div(1_000),
            voltageMilliVolt = values["voltage_now"]?.toLongOrNull()?.takeIf { it >= 1_000 }?.div(1_000),
            temperatureCelsius = values["temp"]?.toDoubleOrNull()?.takeIf { it.isFinite() }?.div(10)
        )
        return if (listOf(result.designCapacityMah, result.fullChargeCapacityMah, result.cycleCount,
                result.currentMilliAmp, result.voltageMilliVolt, result.temperatureCelsius).all { it == null }) {
            result.copy(error = "设备未提供有效电池底层数据")
        } else result
    }

}
