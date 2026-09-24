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
    val displayNames: List<String> = emptyList(),
    val facts: List<String>,
    val confidence: DiagnosticConfidence,
    val maxAdviceLevel: AdviceLevel,
    val reason: String,
    val sceneAction: String? = null,
    val risk: String? = null,
    val rollback: String? = null,
    val retest: String? = null
)

fun RankedPowerCandidate.userFacingName(): String {
    if (packageNames.size > 1) {
        val groupLabel = if (
            (uid ?: Int.MAX_VALUE) < 10_000 ||
            packageNames.any { it == "android" || it.startsWith("com.android.") || it.startsWith("com.miui.") }
        ) {
            "系统组件组"
        } else {
            "共享 UID 应用组"
        }
        val names = displayNames.ifEmpty { packageNames }
        return "$groupLabel（${names.joinToString("、")}）"
    }
    val packageName = packageNames.firstOrNull()
    val displayName = displayNames.firstOrNull()?.takeIf { it.isNotBlank() && it != packageName }
    return when {
        packageName != null && displayName != null -> "$displayName（$packageName）"
        packageName != null -> packageName
        else -> uid?.let { "UID $it" } ?: "未知应用"
    }
}

fun RankedPowerCandidate.packageSummary(): String? = packageNames
    .takeIf(List<String>::isNotEmpty)
    ?.joinToString(prefix = "包名：")

fun RankedPowerCandidate.packageCopyText(): String? = packageNames
    .takeIf(List<String>::isNotEmpty)?.joinToString("\n")

data class LocalPowerVerdict(
    val type: PowerVerdictType,
    val totalConsumption: List<RankedPowerCandidate>,
    val backgroundSuspects: List<RankedPowerCandidate>,
    val nextStep: String?,
    val limits: List<String>,
    val backgroundConsumption: List<RankedPowerCandidate> = emptyList(),
    val schedulingObservations: List<RankedPowerCandidate> = emptyList()
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
    val foregroundPowerMah: Double? = null,
    val backgroundPowerMah: Double? = null,
    val wakeLockDurationMillis: Long? = null,
    val wakeupCount: Long? = null,
    val alarmCount: Long? = null,
    val jobCount: Long? = null,
    val foregroundDurationMillis: Long? = null,
    val backgroundDurationMillis: Long? = null,
    val sharedUid: Boolean = false
)

data class BatteryDrainWindowEvidence(
    val durationMillis: Long,
    val capacityMah: Double,
    val drainMah: Double,
    val usesActualDrain: Boolean
)

data class SystemPowerEvidence(
    val interactive: Boolean? = null,
    val wakefulness: String? = null,
    val deviceIdleMode: Boolean? = null,
    val thermalStatus: Int? = null,
    val topWakeupSources: List<String> = emptyList(),
    val batteryDrainWindow: BatteryDrainWindowEvidence? = null
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
