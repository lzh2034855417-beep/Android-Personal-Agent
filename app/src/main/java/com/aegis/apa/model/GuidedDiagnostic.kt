package com.aegis.apa.model

import java.time.Instant
import java.util.Collections

enum class GuidedScenario { IDLE_DRAIN, USAGE_HEAT }
enum class GuidedStage { ANSWERING, NEEDS_EVIDENCE, READY, AWAITING_USER_ACTION, ENDED }
enum class GuidedActivity { UNKNOWN, GAME, VIDEO, NAVIGATION, CHARGING_USE, OTHER }

class GuidedAnswers(
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val charging: VerificationConfirmation = VerificationConfirmation.UNKNOWN,
    val mostlyIdle: VerificationConfirmation = VerificationConfirmation.UNKNOWN,
    val rebooted: VerificationConfirmation = VerificationConfirmation.UNKNOWN,
    val stillHot: VerificationConfirmation = VerificationConfirmation.UNKNOWN,
    val activity: GuidedActivity = GuidedActivity.UNKNOWN,
    note: String = "",
    val reviewed: Boolean = false
) {
    val note: String = boundedUnicodeText(note, 500)
    fun copy(startedAt: Instant? = this.startedAt, endedAt: Instant? = this.endedAt,
        charging: VerificationConfirmation = this.charging, mostlyIdle: VerificationConfirmation = this.mostlyIdle,
        rebooted: VerificationConfirmation = this.rebooted, stillHot: VerificationConfirmation = this.stillHot,
        activity: GuidedActivity = this.activity, note: String = this.note, reviewed: Boolean = this.reviewed) =
        GuidedAnswers(startedAt, endedAt, charging, mostlyIdle, rebooted, stillHot, activity, note, reviewed)
    private fun values() = listOf(startedAt, endedAt, charging, mostlyIdle, rebooted, stillHot, activity, note, reviewed)
    override fun equals(other: Any?) = other is GuidedAnswers && values() == other.values()
    override fun hashCode() = values().hashCode()
}

data class GuidedInput(val diagnostic: PowerDiagnosticSnapshot?, val device: DeviceSnapshot?, val observation: BatteryObservationResult?)
enum class GuidedEvidenceKind {
    OVERALL_POWER, APP_TOTAL_POWER, APP_FOREGROUND_POWER, APP_BACKGROUND_POWER,
    FOREGROUND_DURATION, BACKGROUND_DURATION, WAKELOCK_DURATION, WAKEUP_COUNT, ALARM_COUNT, JOB_COUNT,
    BATTERY_TEMPERATURE, THERMAL_STATUS, CHARGING, OBSERVATION_RATE
}
sealed interface GuidedValue {
    data class Number(val value: Double, val unit: String) : GuidedValue
    data class Code(val value: String) : GuidedValue
}

class GuidedEvidence(
    val id: String, val kind: GuidedEvidenceKind, val value: GuidedValue?, val source: String,
    val sourceStatus: DiagnosticSourceStatus, val fieldStatus: EvidenceFieldStatus,
    val estimate: VerificationEstimate, packages: List<String>, val collectedAt: Instant,
    val reportedAt: Instant?, val window: MetricWindow?
) {
    val packages: List<String> = guidedList(packages.distinct().sorted())
    fun copy(id: String = this.id, kind: GuidedEvidenceKind = this.kind, value: GuidedValue? = this.value,
        source: String = this.source, sourceStatus: DiagnosticSourceStatus = this.sourceStatus,
        fieldStatus: EvidenceFieldStatus = this.fieldStatus, estimate: VerificationEstimate = this.estimate,
        packages: List<String> = this.packages, collectedAt: Instant = this.collectedAt,
        reportedAt: Instant? = this.reportedAt, window: MetricWindow? = this.window) =
        GuidedEvidence(id, kind, value, source, sourceStatus, fieldStatus, estimate, packages, collectedAt, reportedAt, window)
    private fun values() = listOf(id, kind, value, source, sourceStatus, fieldStatus, estimate, packages, collectedAt, reportedAt, window)
    override fun equals(other: Any?) = other is GuidedEvidence && values() == other.values()
    override fun hashCode() = values().hashCode()
}

class GuidedSample(
    val id: String, evidence: List<GuidedEvidence>, val answersAtConfirmation: GuidedAnswers,
    val sourceAppGroupCount: Int = evidence.map { it.packages }.filter { it.isNotEmpty() }.distinct().size,
    val omittedAppGroupCount: Int = 0,
    gaps: List<GuidedGap> = emptyList(), val diagnosticInputSource: DiagnosticInputSource? = null,
    val diagnosticDevice: VerificationDeviceContext? = null, sources: Map<String, DiagnosticSourceStatus> = emptyMap(),
    verificationEligibleAppGroups: List<List<String>> = emptyList()
) {
    val evidence: List<GuidedEvidence> = guidedList(evidence)
    val gaps: List<GuidedGap> = guidedList(gaps)
    val sources: Map<String, DiagnosticSourceStatus> = Collections.unmodifiableMap(LinkedHashMap(sources))
    val verificationEligibleAppGroups: List<List<String>> = guidedList(verificationEligibleAppGroups.map { guidedList(it.distinct().sorted()) }.distinct()
        .sortedBy { it.joinToString("\u0000") })
    fun copy(id: String = this.id, evidence: List<GuidedEvidence> = this.evidence,
        answersAtConfirmation: GuidedAnswers = this.answersAtConfirmation,
        sourceAppGroupCount: Int = this.sourceAppGroupCount, omittedAppGroupCount: Int = this.omittedAppGroupCount,
        gaps: List<GuidedGap> = this.gaps, diagnosticInputSource: DiagnosticInputSource? = this.diagnosticInputSource,
        diagnosticDevice: VerificationDeviceContext? = this.diagnosticDevice,
        sources: Map<String, DiagnosticSourceStatus> = this.sources,
        verificationEligibleAppGroups: List<List<String>> = this.verificationEligibleAppGroups) = GuidedSample(id, evidence, answersAtConfirmation,
            sourceAppGroupCount, omittedAppGroupCount, gaps, diagnosticInputSource, diagnosticDevice, sources, verificationEligibleAppGroups)
    private fun values() = listOf(id, evidence, answersAtConfirmation, sourceAppGroupCount, omittedAppGroupCount,
        gaps, diagnosticInputSource, diagnosticDevice, sources, verificationEligibleAppGroups)
    override fun equals(other: Any?) = other is GuidedSample && values() == other.values()
    override fun hashCode() = values().hashCode()
}

sealed interface GuidedSampleResult {
    data class Ready(val sample: GuidedSample) : GuidedSampleResult
    data class Rejected(val reasonId: String) : GuidedSampleResult
}
class GuidedSummaryException(val reasonId: String) : IllegalArgumentException(reasonId)

class GuidedCandidate(val ruleId: String, evidenceIds: List<String>, gapIds: List<String>) {
    val evidenceIds = guidedList(evidenceIds)
    val gapIds = guidedList(gapIds)
    override fun equals(other: Any?) = other is GuidedCandidate && ruleId == other.ruleId && evidenceIds == other.evidenceIds && gapIds == other.gapIds
    override fun hashCode() = listOf(ruleId, evidenceIds, gapIds).hashCode()
}
class GuidedGap(val id: String, evidenceIds: List<String> = emptyList()) {
    val evidenceIds = guidedList(evidenceIds)
    override fun equals(other: Any?) = other is GuidedGap && id == other.id && evidenceIds == other.evidenceIds
    override fun hashCode() = listOf(id, evidenceIds).hashCode()
}
enum class GuidedActionKind { ANSWER, IMPORT_REPORT, COLLECT, OPEN_OBSERVATION, OBSERVE_CONDITION, OPEN_VERIFICATION, END, VIEW_SYSTEM_WARNING }
class GuidedAction(val id: String, val kind: GuidedActionKind, reasonIds: List<String>) {
    val reasonIds = guidedList(reasonIds)
    override fun equals(other: Any?) = other is GuidedAction && id == other.id && kind == other.kind && reasonIds == other.reasonIds
    override fun hashCode() = listOf(id, kind, reasonIds).hashCode()
}
class GuidedAssessment(facts: List<GuidedEvidence>, candidates: List<GuidedCandidate>, gaps: List<GuidedGap>, val primaryAction: GuidedAction) {
    val facts = guidedList(facts)
    val candidates = guidedList(candidates)
    val gaps = guidedList(gaps)
    override fun equals(other: Any?) = other is GuidedAssessment && facts == other.facts && candidates == other.candidates && gaps == other.gaps && primaryAction == other.primaryAction
    override fun hashCode() = listOf(facts, candidates, gaps, primaryAction).hashCode()
}

private fun <T> guidedList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

class GuidedDiagnosticSession(
    val id: String, val scenario: GuidedScenario, val revision: Long, val sampleRevision: Long,
    val answerRevision: Long, val answers: GuidedAnswers, samples: List<GuidedSample>, val stage: GuidedStage
) {
    val samples = guidedList(samples)
    val key get() = GuidedSessionKey(id, revision, sampleRevision, answerRevision)
    fun copy(revision: Long = this.revision, sampleRevision: Long = this.sampleRevision,
        answerRevision: Long = this.answerRevision, answers: GuidedAnswers = this.answers,
        samples: List<GuidedSample> = this.samples, stage: GuidedStage = this.stage) =
        GuidedDiagnosticSession(id, scenario, revision, sampleRevision, answerRevision, answers, samples, stage)
}

data class GuidedSessionKey(val id: String, val revision: Long, val sampleRevision: Long, val answerRevision: Long)
data class GuidedUiState(val session: GuidedDiagnosticSession? = null, val assessment: GuidedAssessment? = null,
    val availableSampleId: String? = null, val errorId: String? = null)

data class GuidedSamplePreview(val key: GuidedSessionKey, val candidateRevision: Long, val sample: GuidedSample)
data class GuidedWorkToken(val key: GuidedSessionKey, val requestId: Long)
