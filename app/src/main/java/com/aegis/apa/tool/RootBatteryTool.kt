package com.aegis.apa.tool

import java.util.concurrent.TimeUnit

data class RootBatteryInfo(
    val designCapacityMah: Long?,
    val fullChargeCapacityMah: Long?,
    val cycleCount: String?,
    val currentMilliAmp: Long?,
    val voltageMilliVolt: Long?,
    val temperatureCelsius: Double?,
    val error: String? = null,
    val sampledAt: String = java.time.ZonedDateTime.now().format(
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
    )
)

object RootBatteryTool {
    fun read(): RootBatteryInfo {
        val command = """
            for key in charge_full_design charge_full cycle_count current_now voltage_now temp; do
              for path in /sys/class/power_supply/battery/${'$'}key /sys/class/power_supply/Battery/${'$'}key; do
                if [ -r "${'$'}path" ]; then
                  echo "${'$'}key=${'$'}(cat "${'$'}path")"
                  break
                fi
              done
            done
        """.trimIndent()

        val process = runCatching {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        }.getOrElse {
            return emptyInfo("无法启动 Root 命令")
        }

        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return emptyInfo("Root 授权超时")
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.exitValue() != 0) {
            return emptyInfo("Root 授权被拒绝或执行失败")
        }

        val values = output.lineSequence()
            .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0] to it[1].trim() }

        return RootBatteryInfo(
            designCapacityMah = values["charge_full_design"]?.toLongOrNull()?.div(1_000),
            fullChargeCapacityMah = values["charge_full"]?.toLongOrNull()?.div(1_000),
            cycleCount = values["cycle_count"],
            currentMilliAmp = values["current_now"]?.toLongOrNull()?.div(1_000),
            voltageMilliVolt = values["voltage_now"]?.toLongOrNull()?.div(1_000),
            temperatureCelsius = values["temp"]?.toDoubleOrNull()?.div(10),
            error = if (values.isEmpty()) "设备未提供电池底层数据" else null
        )
    }

    private fun emptyInfo(error: String) = RootBatteryInfo(
        designCapacityMah = null,
        fullChargeCapacityMah = null,
        cycleCount = null,
        currentMilliAmp = null,
        voltageMilliVolt = null,
        temperatureCelsius = null,
        error = error
    )
}
