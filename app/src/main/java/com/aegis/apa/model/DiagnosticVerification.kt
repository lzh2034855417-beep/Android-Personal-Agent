package com.aegis.apa.model

import java.time.Instant

enum class VerificationState(val wireId: String) {
    BASELINE_SAVED("baseline_saved"), CHANGE_RECORDED("change_recorded"), RETEST_SAVED("retest_saved"), CLOSED("closed")
}

enum class VerificationCloseReason(val wireId: String) {
    COMPLETED("completed"), ROLLED_BACK("rolled_back"), ABANDONED("abandoned")
}

enum class VerificationConfirmation(val wireId: String) { YES("yes"), NO("no"), UNKNOWN("unknown") }
enum class VerificationScenario(val wireId: String) { IDLE("idle"), LIGHT("light"), GAME("game"), VIDEO("video"), OTHER("other") }
enum class VerificationOwnership(val wireId: String) {
    LOCAL_COLLECTION("local_collection"), USER_CONFIRMED("user_confirmed"), UNKNOWN("unknown")
}

enum class VerificationMetricScope(val wireId: String) {
    SINCE_LAST_CHARGE("since_last_charge"), SOURCE_CUMULATIVE("source_cumulative"), EXPLICIT_WINDOW("explicit_window"), UNKNOWN("unknown")
}

enum class VerificationMetricKind(val wireId: String) {
    OVERALL_POWER("overall_power"), APP_TOTAL_POWER("app_total_power"), APP_FOREGROUND_POWER("app_foreground_power"),
    APP_BACKGROUND_POWER("app_background_power"), FOREGROUND_DURATION("foreground_duration"), BACKGROUND_DURATION("background_duration"),
    WAKELOCK_DURATION("wakelock_duration"), WAKEUP_COUNT("wakeup_count"), ALARM_COUNT("alarm_count"), JOB_COUNT("job_count")
}

enum class VerificationUnit(val wireId: String) { MAH("mah"), MILLIS("millis"), COUNT("count") }
enum class VerificationEstimate(val wireId: String) { ACTUAL("actual"), ESTIMATED("estimated"), UNKNOWN("unknown") }

sealed interface VerificationTarget {
    data object Overall : VerificationTarget

    class AppGroup(packageNames: List<String>) : VerificationTarget {
        val packageNames: List<String> = canonicalPackages(packageNames)
        fun copy(packageNames: List<String> = this.packageNames) = AppGroup(packageNames)
        override fun equals(other: Any?) = other is AppGroup && packageNames == other.packageNames
        override fun hashCode() = packageNames.hashCode()
    }
}

data class VerificationDeviceContext(
    val manufacturer: String?, val modelCode: String?, val apiLevel: Int?, val buildVersion: String?, val ownership: VerificationOwnership
)

class VerificationSampleContext(
    val device: VerificationDeviceContext,
    val scenario: VerificationScenario,
    scenarioNote: String,
    val noCharging: VerificationConfirmation
) {
    val scenarioNote: String = boundedUnicodeText(scenarioNote, 500)
    fun copy(device: VerificationDeviceContext = this.device, scenario: VerificationScenario = this.scenario,
        scenarioNote: String = this.scenarioNote, noCharging: VerificationConfirmation = this.noCharging) =
        VerificationSampleContext(device, scenario, scenarioNote, noCharging)
    private fun values() = listOf(device, scenario, scenarioNote, noCharging)
    override fun equals(other: Any?) = other is VerificationSampleContext && values() == other.values()
    override fun hashCode() = values().hashCode()
}

class VerificationChange(
    description: String,
    rollback: String,
    val confirmedAt: Instant,
    val similarScenario: VerificationConfirmation,
    val noOtherChanges: VerificationConfirmation,
    sideEffects: String
) {
    val description: String = boundedUnicodeText(description, 500)
    val rollback: String = boundedUnicodeText(rollback, 500)
    val sideEffects: String = boundedUnicodeText(sideEffects, 500)
    fun copy(description: String = this.description, rollback: String = this.rollback, confirmedAt: Instant = this.confirmedAt,
        similarScenario: VerificationConfirmation = this.similarScenario, noOtherChanges: VerificationConfirmation = this.noOtherChanges,
        sideEffects: String = this.sideEffects) = VerificationChange(description, rollback, confirmedAt, similarScenario, noOtherChanges, sideEffects)
    private fun values() = listOf(description, rollback, confirmedAt, similarScenario, noOtherChanges, sideEffects)
    override fun equals(other: Any?) = other is VerificationChange && values() == other.values()
    override fun hashCode() = values().hashCode()
}

data class MetricWindow(
    val source: String,
    val scope: VerificationMetricScope,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val batteryDurationMillis: Long?,
    val resetId: String?,
    val charging: VerificationConfirmation
)

class VerificationMetricKey(val kind: VerificationMetricKind, packageNames: List<String>) {
    val packageNames: List<String> = if (kind == VerificationMetricKind.OVERALL_POWER) emptyList() else canonicalPackages(packageNames)
    fun copy(kind: VerificationMetricKind = this.kind, packageNames: List<String> = this.packageNames) = VerificationMetricKey(kind, packageNames)
    override fun equals(other: Any?) = other is VerificationMetricKey && kind == other.kind && packageNames == other.packageNames
    override fun hashCode() = 31 * kind.hashCode() + packageNames.hashCode()
}

data class VerificationMetric(
    val key: VerificationMetricKey, val value: Double?, val unit: VerificationUnit, val estimate: VerificationEstimate,
    val window: MetricWindow, val status: EvidenceFieldStatus
)

data class DiagnosticVerificationSummary(
    val inputSource: DiagnosticInputSource,
    val generatedAt: Instant,
    val reportedAt: Instant?,
    val context: VerificationSampleContext,
    val sources: Map<String, DiagnosticSourceStatus>,
    val metrics: List<VerificationMetric>,
    val fingerprint: String
)

data class DiagnosticVerificationMetadata(
    val reportedAt: Instant?, val device: VerificationDeviceContext?, val windowsBySource: Map<String, MetricWindow>
)

class VerificationRecord(
    val id: String,
    val schemaVersion: Int,
    val ruleVersion: Int,
    val scopeId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
    title: String,
    val state: VerificationState,
    val target: VerificationTarget,
    val baseline: DiagnosticVerificationSummary,
    val change: VerificationChange?,
    val retest: DiagnosticVerificationSummary?,
    val closeReason: VerificationCloseReason?
) {
    val title: String = boundedUnicodeText(title, 80)
    fun copy(id: String = this.id, schemaVersion: Int = this.schemaVersion, ruleVersion: Int = this.ruleVersion,
        scopeId: String = this.scopeId, createdAt: Instant = this.createdAt, updatedAt: Instant = this.updatedAt,
        revision: Long = this.revision, title: String = this.title, state: VerificationState = this.state,
        target: VerificationTarget = this.target, baseline: DiagnosticVerificationSummary = this.baseline,
        change: VerificationChange? = this.change, retest: DiagnosticVerificationSummary? = this.retest,
        closeReason: VerificationCloseReason? = this.closeReason) =
        VerificationRecord(id, schemaVersion, ruleVersion, scopeId, createdAt, updatedAt, revision, title, state, target, baseline, change, retest, closeReason)
    private fun values() = listOf(id, schemaVersion, ruleVersion, scopeId, createdAt, updatedAt, revision, title, state, target, baseline, change, retest, closeReason)
    override fun equals(other: Any?) = other is VerificationRecord && values() == other.values()
    override fun hashCode() = values().hashCode()
}

class VerificationTransitionException(val state: VerificationState, val operation: String) :
    IllegalStateException("Cannot $operation from ${state.wireId}")

private fun canonicalPackages(packageNames: List<String>): List<String> = packageNames.distinct().sorted()
