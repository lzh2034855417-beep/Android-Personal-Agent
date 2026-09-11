package com.aegis.apa.model

import java.time.Instant

enum class DiagnosticSourceStatus {
    AVAILABLE,
    UNSUPPORTED,
    PERMISSION_DENIED,
    TIMED_OUT,
    TRUNCATED,
    PARSE_FAILED
}

data class DiagnosticSourceResult(
    val source: String,
    val status: DiagnosticSourceStatus,
    val detail: String? = null
)

enum class AdviceLevel { OBSERVE, RESTRICT, FREEZE_CANDIDATE }

enum class DiagnosticConfidence { LOW, MEDIUM, HIGH }

data class AppPowerEvidence(
    val uid: Int?,
    val packageNames: List<String>,
    val displayNames: List<String> = emptyList(),
    val wakeLockDurationMillis: Long? = null,
    val wakeupCount: Long? = null,
    val alarmCount: Long? = null,
    val jobCount: Long? = null
)

data class SystemPowerEvidence(
    val interactive: Boolean? = null,
    val wakefulness: String? = null,
    val deviceIdleMode: Boolean? = null,
    val thermalStatus: Int? = null,
    val topWakeupSources: List<String> = emptyList()
)

data class PowerFinding(
    val id: String,
    val title: String,
    val packageNames: List<String>,
    val evidence: List<String>,
    val explanation: String,
    val confidence: DiagnosticConfidence,
    val adviceLevel: AdviceLevel,
    val sceneAdvice: String,
    val caveat: String
)

data class PowerDiagnosticSnapshot(
    val sampledAtInstant: Instant,
    val collectionDurationMillis: Long,
    val sources: Map<String, DiagnosticSourceResult>,
    val apps: List<AppPowerEvidence>,
    val system: SystemPowerEvidence,
    val findings: List<PowerFinding>
)
