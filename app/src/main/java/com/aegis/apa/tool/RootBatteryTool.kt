package com.aegis.apa.tool

import java.util.concurrent.TimeUnit

data class RootBatteryInfo(
    val designCapacityMah: Long?,
    val fullChargeCapacityMah: Long?,
    val cycleCount: Long?,
    val currentMilliAmp: Long?,
    val voltageMilliVolt: Long?,
    val temperatureCelsius: Double?,
    val error: String? = null,
    val sampledAtInstant: java.time.Instant = java.time.Instant.now()
) {
    val sampledAt: String get() = com.aegis.apa.model.SampleTime.format(sampledAtInstant)
}

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

        return RootBatteryParser.parse(output)
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
