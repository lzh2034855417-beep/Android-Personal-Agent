package com.aegis.apa.tool

import com.aegis.apa.model.DiagnosticSourceStatus

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
        val result = RootCommandRunner.runBatteryHealth()
        return when (result.status) {
            DiagnosticSourceStatus.AVAILABLE,
            DiagnosticSourceStatus.TRUNCATED -> RootBatteryParser.parse(result.output)
            DiagnosticSourceStatus.TIMED_OUT -> emptyInfo("Root 授权超时")
            DiagnosticSourceStatus.PERMISSION_DENIED -> emptyInfo(result.detail ?: "Root 授权被拒绝或执行失败")
            else -> emptyInfo("Root 授权被拒绝或执行失败")
        }
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
