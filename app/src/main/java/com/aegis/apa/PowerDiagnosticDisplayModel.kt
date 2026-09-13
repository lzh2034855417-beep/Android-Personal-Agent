package com.aegis.apa

import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.RankedPowerCandidate

data class PowerDiagnosticDisplayModel(
    val sourceLabel: String,
    val totalConsumption: List<RankedPowerCandidate>,
    val backgroundSuspects: List<RankedPowerCandidate>,
    val nextStep: String?,
    val limits: List<String>
) {
    companion object {
        fun from(snapshot: PowerDiagnosticSnapshot): PowerDiagnosticDisplayModel {
            val verdict = snapshot.localVerdict
            return PowerDiagnosticDisplayModel(
                sourceLabel = when (snapshot.inputSource) {
                    DiagnosticInputSource.BUGREPORT -> "系统 Bug Report"
                    DiagnosticInputSource.ROOT -> "Root 只读采集"
                    null -> "未知来源"
                },
                totalConsumption = verdict?.totalConsumption.orEmpty(),
                backgroundSuspects = verdict?.backgroundSuspects.orEmpty(),
                nextStep = verdict?.nextStep,
                limits = verdict?.limits.orEmpty()
            )
        }
    }
}
