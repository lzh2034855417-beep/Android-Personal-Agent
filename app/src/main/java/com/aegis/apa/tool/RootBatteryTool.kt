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
    fun read(cancellationRequested: () -> Boolean = { false }): RootBatteryInfo {
        checkCollectionCancellation(cancellationRequested)
        val result = RootCommandRunner.runBatteryHealth(cancellationRequested)
        checkCollectionCancellation(cancellationRequested)
        return when (result.status) {
            DiagnosticSourceStatus.AVAILABLE -> RootBatteryParser.parse(result.output)
            DiagnosticSourceStatus.TRUNCATED -> RootBatteryParser.parse(result.output)
                .copy(error = result.detail ?: "输出过长或不完整，已截断")
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
