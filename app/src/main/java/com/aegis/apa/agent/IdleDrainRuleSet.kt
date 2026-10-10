package com.aegis.apa.agent

import com.aegis.apa.model.*

object IdleDrainRuleSet {
    private val activityKinds = setOf(GuidedEvidenceKind.APP_BACKGROUND_POWER, GuidedEvidenceKind.BACKGROUND_DURATION,
        GuidedEvidenceKind.WAKELOCK_DURATION, GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT, GuidedEvidenceKind.JOB_COUNT)
    private val idleKinds = GuidedEvidenceKind.entries.toSet() - setOf(GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.THERMAL_STATUS)

    fun evaluate(answers: GuidedAnswers, samples: List<GuidedSample>): GuidedAssessment {
        val orderedSamples = samples.sortedBy { it.id }
        val all = orderedSamples.flatMap { it.evidence }.filter { it.kind in idleKinds }
        val duplicateIds = all.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        // Ambiguous IDs cannot support a graph reference or select an adjustment target.
        val facts = all.filter { it.id !in duplicateIds }.sortedBy { it.id }
        val factIds = facts.map { it.id }.toSet()
        val gaps = linkedMapOf<String, GuidedGap>()
        fun gap(id: String, evidenceIds: List<String> = emptyList()) {
            gaps[id] = GuidedGap(id, ((gaps[id]?.evidenceIds ?: emptyList()) + evidenceIds).distinct().sorted())
        }
        orderedSamples.flatMap { it.gaps }.filter { it.id != "thermal_status_unknown" }.forEach {
            gap(it.id, it.evidenceIds.filter { id -> id in factIds })
        }
        if (duplicateIds.isNotEmpty()) gap("idle_evidence_conflict")
        val reversed = answers.startedAt != null && answers.endedAt != null && answers.endedAt <= answers.startedAt
        if (reversed) gap("answer_time_range_reversed")
        if (!answers.reviewed) gap("idle_answers_unreviewed")
        val conditionsConfirmed = answers.charging == VerificationConfirmation.NO && answers.rebooted == VerificationConfirmation.NO &&
            answers.mostlyIdle == VerificationConfirmation.YES
        if (!conditionsConfirmed) gap("idle_conditions_unconfirmed")

        val diagnosticFacts = facts.filter { it.source != "battery" && it.source != "observation" }
        val chargingConflict = if (answers.charging == VerificationConfirmation.NO) diagnosticFacts.filter {
            it.sourceStatus == DiagnosticSourceStatus.AVAILABLE && intervalMatches(it, answers) && it.window?.charging == VerificationConfirmation.YES
        } else emptyList()
        if (chargingConflict.isNotEmpty()) gap("idle_conditions_conflict", chargingConflict.map { it.id })
        diagnosticFacts.filter { !windowMatches(it, answers) }.takeIf { it.isNotEmpty() }?.let {
            gap("idle_window_unknown", it.map { item -> item.id })
        }
        val rates = facts.filter { it.kind == GuidedEvidenceKind.OBSERVATION_RATE && usableNumber(it) != null && validWindow(it.window) }
        if (rates.isNotEmpty()) gap("idle_observation_rough", rates.map { it.id })

        val candidates = mutableListOf<GuidedCandidate>()
        val verificationCandidates = mutableSetOf<String>()
        for (sample in orderedSamples) {
            val groups = sample.evidence.filter { it.id in factIds && it.packages.isNotEmpty() }.groupBy { it.packages }
                .toSortedMap(compareBy { it.joinToString("\u0000") })
            for ((packages, groupFacts) in groups) {
                val support = groupFacts.filter { it.kind in activityKinds && (usableNumber(it) ?: 0.0) > 0.0 }.sortedBy { it.id }
                if (support.isEmpty()) continue
                val candidateGaps = mutableListOf<String>()
                if (support.any { !windowMatches(it, answers) }) candidateGaps += "idle_window_unknown"
                if (!conditionsConfirmed) candidateGaps += "idle_conditions_unconfirmed"
                if (sample.diagnosticDevice?.ownership !in setOf(VerificationOwnership.LOCAL_COLLECTION, VerificationOwnership.USER_CONFIRMED)) {
                    gap("report_device_unknown"); candidateGaps += "report_device_unknown"
                }
                if (packages.size > 1 || support.any { it.fieldStatus != EvidenceFieldStatus.PARSED || it.sourceStatus != DiagnosticSourceStatus.AVAILABLE } ||
                    sample.sources["packages"] != DiagnosticSourceStatus.AVAILABLE) {
                    gap("idle_app_evidence_incomplete", groupFacts.map { it.id }); candidateGaps += "idle_app_evidence_incomplete"
                }
                if (sample.answersAtConfirmation != answers) {
                    gap("idle_answers_changed"); candidateGaps += "idle_answers_changed"
                }
                val candidate = GuidedCandidate("idle.background_activity_correlation:${support.first().id}", support.map { it.id }, candidateGaps.distinct().sorted())
                candidates += candidate
                val required = listOf(GuidedEvidenceKind.APP_TOTAL_POWER, GuidedEvidenceKind.APP_BACKGROUND_POWER, GuidedEvidenceKind.WAKELOCK_DURATION)
                    .map { kind -> groupFacts.singleOrNull { it.kind == kind } }
                val safeRequired = required.all { it != null && completeNumber(it) != null && windowMatches(it, answers) }
                val sameWindow = required.mapNotNull { it?.window }.distinct().size == 1
                if (answers.reviewed && conditionsConfirmed && !reversed && duplicateIds.isEmpty() && candidateGaps.isEmpty() &&
                    packages in sample.verificationEligibleAppGroups && safeRequired && sameWindow) {
                    verificationCandidates += candidate.ruleId
                }
            }
        }
        // App correlations take precedence over whole-device rate context within the explanation cap.
        rates.forEach { candidates += GuidedCandidate("idle.rough_observation_rate", listOf(it.id), listOf("idle_observation_rough")) }
        val retainedCandidates = candidates.distinctBy { it.ruleId }.take(3)
        val actions = mutableListOf<GuidedAction>()
        fun action(id: String, kind: GuidedActionKind, vararg reasons: String) { actions += GuidedAction(id, kind, reasons.toList()) }
        action("idle.end", GuidedActionKind.END)
        if (!answers.reviewed) action("idle.answer", GuidedActionKind.ANSWER, "idle_answers_unreviewed")
        if (reversed) action("idle.answer_conflict", GuidedActionKind.ANSWER, "answer_time_range_reversed")
        if (duplicateIds.isNotEmpty()) action("idle.answer_conflict", GuidedActionKind.ANSWER, "idle_evidence_conflict")
        if (chargingConflict.isNotEmpty()) action("idle.answer_conflict", GuidedActionKind.ANSWER, "idle_conditions_conflict")
        val quantified = rates.isNotEmpty() || facts.any {
            it.kind == GuidedEvidenceKind.OVERALL_POWER && usableNumber(it) != null && (it.window?.batteryDurationMillis ?: 0) > 0
        }
        val hasReport = orderedSamples.any { it.diagnosticInputSource != null || "batterystats" in it.sources }
        // Explicit unsupported observation capability is terminal; repeating evaluation cannot promise another source.
        val observationUnsupported = orderedSamples.any { it.sources["battery"] == DiagnosticSourceStatus.UNSUPPORTED } &&
            orderedSamples.none { it.sources["battery"] == DiagnosticSourceStatus.AVAILABLE || it.sources["observation"] == DiagnosticSourceStatus.AVAILABLE }
        if (!quantified && !observationUnsupported) {
            gap("idle_quantification_missing")
            action("idle.open_observation", GuidedActionKind.OPEN_OBSERVATION, "idle_quantification_missing")
        } else if (quantified && !hasReport) {
            gap("idle_report_missing")
            action("idle.import_report", GuidedActionKind.IMPORT_REPORT, "idle_report_missing")
        } else if (quantified) {
            val selected = retainedCandidates.firstOrNull { it.ruleId in verificationCandidates }
            if (selected != null) action("idle.verify_background", GuidedActionKind.OPEN_VERIFICATION, selected.ruleId)
            else retainedCandidates.firstOrNull { it.ruleId.startsWith("idle.background_activity_correlation:") }?.let {
                action("idle.observe_condition", GuidedActionKind.OBSERVE_CONDITION, it.ruleId)
            }
        }
        return GuidedAssessment(facts, retainedCandidates, gaps.values.sortedBy { it.id }, GuidedActionPolicy.select(actions))
    }

    private fun usableNumber(item: GuidedEvidence): Double? = (item.value as? GuidedValue.Number)?.value?.takeIf {
        it.isFinite() && it >= 0 && item.sourceStatus in setOf(DiagnosticSourceStatus.AVAILABLE, DiagnosticSourceStatus.TRUNCATED) &&
            item.fieldStatus in setOf(EvidenceFieldStatus.PARSED, EvidenceFieldStatus.TRUNCATED)
    }
    private fun completeNumber(item: GuidedEvidence): Double? = usableNumber(item)?.takeIf {
        item.sourceStatus == DiagnosticSourceStatus.AVAILABLE && item.fieldStatus == EvidenceFieldStatus.PARSED
    }
    private fun validWindow(window: MetricWindow?): Boolean = window != null && window.startedAt != null && window.endedAt != null &&
        window.startedAt < window.endedAt && (window.batteryDurationMillis ?: 0) > 0

    private fun windowMatches(item: GuidedEvidence, answers: GuidedAnswers): Boolean {
        val window = item.window ?: return false
        return intervalMatches(item, answers) && window.charging == VerificationConfirmation.NO
    }

    private fun intervalMatches(item: GuidedEvidence, answers: GuidedAnswers): Boolean {
        val window = item.window ?: return false
        return validWindow(window) && window.source == item.source && window.scope == VerificationMetricScope.EXPLICIT_WINDOW &&
            answers.startedAt != null && answers.endedAt != null && window.startedAt == answers.startedAt && window.endedAt == answers.endedAt
    }
}
