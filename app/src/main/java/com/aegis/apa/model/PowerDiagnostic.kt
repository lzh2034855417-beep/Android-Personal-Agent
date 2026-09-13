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

enum class EvidenceField {
    UID_PACKAGES,
    POWER_MAH,
    FOREGROUND_TIME,
    WAKELOCK_TIME,
    WAKEUP_ALARMS,
    JOBS
}

enum class EvidenceFieldStatus { PARSED, NOT_PRESENT, NOT_PARSED, SOURCE_UNAVAILABLE, TRUNCATED }

data class EvidenceCoverage(
    val fields: Map<EvidenceField, EvidenceFieldStatus> = emptyMap()
)

enum class DiagnosticInputSource { BUGREPORT, ROOT }

enum class PowerVerdictType { SUFFICIENT, INSUFFICIENT }

data class RankedPowerCandidate(
    val uid: Int?,
    val packageNames: List<String>,
    val facts: List<String>,
    val confidence: DiagnosticConfidence,
    val maxAdviceLevel: AdviceLevel,
    val reason: String,
    val sceneAction: String? = null,
    val risk: String? = null,
    val rollback: String? = null,
    val retest: String? = null
)

data class LocalPowerVerdict(
    val type: PowerVerdictType,
    val totalConsumption: List<RankedPowerCandidate>,
    val backgroundSuspects: List<RankedPowerCandidate>,
    val nextStep: String?,
    val limits: List<String>
)

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
    val estimatedPowerMah: Double? = null,
    val wakeLockDurationMillis: Long? = null,
    val wakeupCount: Long? = null,
    val alarmCount: Long? = null,
    val jobCount: Long? = null,
    val foregroundDurationMillis: Long? = null,
    val backgroundDurationMillis: Long? = null,
    val sharedUid: Boolean = false
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
    val findings: List<PowerFinding>,
    val evidenceCoverage: EvidenceCoverage = EvidenceCoverage(),
    val inputSource: DiagnosticInputSource? = null,
    val localVerdict: LocalPowerVerdict? = null
)
