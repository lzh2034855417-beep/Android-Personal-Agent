package com.aegis.apa.agent

import com.aegis.apa.model.*

object UsageHeatRuleSet {
    fun evaluate(answers: GuidedAnswers, samples: List<GuidedSample>): GuidedAssessment {
        val orderedSamples = samples.sortedBy { it.id }
        val all = orderedSamples.flatMap { it.evidence }
        val duplicateIds = all.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val gaps = linkedMapOf<String, GuidedGap>()
        fun gap(id: String, evidenceIds: List<String> = emptyList()) {
            gaps[id] = GuidedGap(id, ((gaps[id]?.evidenceIds ?: emptyList()) + evidenceIds).distinct().sorted())
        }
        if (duplicateIds.isNotEmpty()) gap("heat_evidence_conflict")
        val facts = orderedSamples.flatMap { sample ->
            sample.evidence.filter { it.id !in duplicateIds }.map { item ->
                val sourceConflict = sample.sources[item.source]?.let { it != item.sourceStatus } == true
                if (sourceConflict) gap("heat_evidence_conflict", listOf(item.id))
                val sourceMatches = item.source == expectedSource(item.kind) &&
                    (item.window == null || item.window.source == item.source)
                val sensor = item.kind in setOf(GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.THERMAL_STATUS, GuidedEvidenceKind.CHARGING)
                val available = item.sourceStatus in setOf(DiagnosticSourceStatus.AVAILABLE, DiagnosticSourceStatus.TRUNCATED) &&
                    item.fieldStatus in setOf(EvidenceFieldStatus.PARSED, EvidenceFieldStatus.TRUNCATED)
                val rateWindow = item.kind != GuidedEvidenceKind.OBSERVATION_RATE || validObservationWindow(item.window)
                val provenance = !sourceConflict && sourceMatches && available &&
                    rateWindow && item.estimate != VerificationEstimate.UNKNOWN &&
                    (!sensor || item.estimate == VerificationEstimate.ACTUAL && item.packages.isEmpty())
                val validValue = when (val value = item.value) {
                    is GuidedValue.Number -> {
                        if (value.unit != expectedUnit(item.kind)) gap("heat_unit_unknown", listOf(item.id))
                        value.unit == expectedUnit(item.kind) && value.value.isFinite() &&
                            (item.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE || value.value >= 0)
                    }
                    is GuidedValue.Code -> when (item.kind) {
                        GuidedEvidenceKind.THERMAL_STATUS -> GuidedThermalStatus.fromCode(value.value) != null
                        GuidedEvidenceKind.CHARGING -> value.value in setOf("yes", "no")
                        else -> false
                    }
                    null -> false
                }
                val usable = provenance && validValue
                if (!usable && item.value != null) gap("heat_reading_invalid", listOf(item.id))
                if (!usable && item.kind == GuidedEvidenceKind.THERMAL_STATUS) gap("thermal_status_unknown", listOf(item.id))
                if (item.sourceStatus != DiagnosticSourceStatus.AVAILABLE || item.fieldStatus != EvidenceFieldStatus.PARSED) {
                    gap("heat_evidence_incomplete", listOf(item.id))
                }
                if (usable) item else item.copy(value = null, estimate = VerificationEstimate.UNKNOWN)
            }
        }.sortedBy { it.id }
        val factIds = facts.map { it.id }.toSet()
        orderedSamples.flatMap { it.gaps }.forEach { gap(it.id, it.evidenceIds.filter { id -> id in factIds }) }
        val reversed = answers.startedAt != null && answers.endedAt != null && answers.endedAt <= answers.startedAt
        if (reversed) gap("answer_time_range_reversed")
        if (!answers.reviewed) gap("heat_answers_unreviewed")
        if (answers.stillHot == VerificationConfirmation.UNKNOWN || answers.charging == VerificationConfirmation.UNKNOWN ||
            answers.activity == GuidedActivity.UNKNOWN) gap("heat_conditions_unconfirmed")
        if (orderedSamples.any { it.answersAtConfirmation != answers }) gap("heat_answers_changed")
        val chargingContradiction = answers.activity == GuidedActivity.CHARGING_USE && answers.charging == VerificationConfirmation.NO
        if (chargingContradiction) gap("heat_conditions_conflict")

        val temperatures = facts.filter { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE && it.value != null }
        val thermalStates = facts.filter { it.kind == GuidedEvidenceKind.THERMAL_STATUS && it.value != null }
        val severe = thermalStates.filter { GuidedThermalStatus.fromCode((it.value as GuidedValue.Code).value)?.requiresSystemGuidance == true }
        if (severe.isNotEmpty()) gap("heat_recognized_severe_state", severe.map { it.id })
        if (temperatures.isNotEmpty()) gap("heat_sensor_scope", temperatures.map { it.id })
        val sensorFacts = temperatures + thermalStates
        if (sensorFacts.isEmpty()) gap("heat_sensor_missing")

        // A confirmation/import timestamp never joins a battery reading to a report event.
        // Even close/equal times do not establish the same event, sensor, task or device.
        val reportFacts = facts.filter { it.source !in setOf("battery", "observation") }
        val separateObservations = (sensorFacts + reportFacts).distinctBy { it.id }.sortedBy { it.id }
        val multipleSensorSamples = orderedSamples.count { sample -> sample.evidence.any { it.id in sensorFacts.map { sensor -> sensor.id } } } > 1
        val unlinked = multipleSensorSamples || temperatures.isNotEmpty() && reportFacts.isNotEmpty()
        if (unlinked) gap("heat_event_unlinked", separateObservations.map { it.id })

        val rates = facts.filter { it.kind == GuidedEvidenceKind.OBSERVATION_RATE && it.value != null }
        if (rates.isNotEmpty()) gap("heat_observation_rough", rates.map { it.id })
        val chargingUse = answers.charging == VerificationConfirmation.YES && answers.activity != GuidedActivity.UNKNOWN
        if (chargingUse) {
            gap("heat_conditions_user_reported")
            gap("heat_factors_not_separated")
        }
        val candidates = mutableListOf<GuidedCandidate>()
        if (unlinked) candidates += GuidedCandidate("heat.separate_observations:${separateObservations.first().id}",
            separateObservations.map { it.id }, listOf("heat_event_unlinked"))
        if (chargingUse) candidates += GuidedCandidate("heat.charging_use", emptyList(),
            listOf("heat_conditions_user_reported", "heat_factors_not_separated"))
        if (temperatures.isNotEmpty()) candidates += GuidedCandidate("heat.battery_sensor_only:${temperatures.first().id}",
            temperatures.map { it.id }, listOf("heat_sensor_scope"))
        rates.forEach { candidates += GuidedCandidate("heat.rough_observation_rate:${it.id}", listOf(it.id), listOf("heat_observation_rough")) }
        val retained = candidates.take(3)

        val actions = mutableListOf(GuidedAction("heat.end", GuidedActionKind.END, emptyList()))
        fun action(id: String, kind: GuidedActionKind, vararg reasons: String) { actions += GuidedAction(id, kind, reasons.toList()) }
        if (severe.isNotEmpty()) action("heat.view_system_warning", GuidedActionKind.VIEW_SYSTEM_WARNING, "heat_recognized_severe_state")
        if (!answers.reviewed) action("heat.answer", GuidedActionKind.ANSWER, "heat_answers_unreviewed")
        if (reversed) action("heat.answer_conflict", GuidedActionKind.ANSWER, "answer_time_range_reversed")
        if (chargingContradiction) action("heat.answer_conflict", GuidedActionKind.ANSWER, "heat_conditions_conflict")
        if ("heat_evidence_conflict" in gaps) action("heat.answer_conflict", GuidedActionKind.ANSWER, "heat_evidence_conflict")
        // COLLECT is a user-initiated basic snapshot refresh only. No stress/automatic sampling is executed here.
        val batteryUnavailable = orderedSamples.any { it.sources["battery"] in setOf(DiagnosticSourceStatus.UNSUPPORTED,
            DiagnosticSourceStatus.PERMISSION_DENIED) } && temperatures.isEmpty() &&
            orderedSamples.none { it.sources["battery"] in setOf(DiagnosticSourceStatus.AVAILABLE, DiagnosticSourceStatus.TRUNCATED) }
        if (answers.stillHot == VerificationConfirmation.YES) {
            if (sensorFacts.isEmpty() && !batteryUnavailable) action("heat.collect_current", GuidedActionKind.COLLECT, "heat_sensor_missing")
            else if (unlinked && !multipleSensorSamples && !batteryUnavailable) action("heat.collect_same_session", GuidedActionKind.COLLECT, "heat_event_unlinked")
            else if (sensorFacts.isNotEmpty() && (answers.activity != GuidedActivity.UNKNOWN || answers.charging == VerificationConfirmation.YES)) {
                action("heat.observe_condition", GuidedActionKind.OBSERVE_CONDITION,
                    if (chargingUse) "heat.charging_use" else if (temperatures.isNotEmpty()) "heat_sensor_scope" else thermalStates.first().id)
            }
        }
        return GuidedAssessment(facts, retained, gaps.values.sortedBy { it.id }, GuidedActionPolicy.select(actions))
    }

    private fun validObservationWindow(window: MetricWindow?): Boolean = window != null && window.source == "observation" &&
        window.scope == VerificationMetricScope.EXPLICIT_WINDOW && window.startedAt != null && window.endedAt != null &&
        window.startedAt < window.endedAt && (window.batteryDurationMillis ?: 0) > 0 && window.charging != VerificationConfirmation.YES

    private fun expectedSource(kind: GuidedEvidenceKind): String = when (kind) {
        GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.CHARGING -> "battery"
        GuidedEvidenceKind.THERMAL_STATUS -> "thermalservice"
        GuidedEvidenceKind.OBSERVATION_RATE -> "observation"
        GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT -> "alarm"
        GuidedEvidenceKind.JOB_COUNT -> "jobscheduler"
        else -> "batterystats"
    }

    private fun expectedUnit(kind: GuidedEvidenceKind): String? = when (kind) {
        GuidedEvidenceKind.BATTERY_TEMPERATURE -> "celsius"
        GuidedEvidenceKind.OBSERVATION_RATE -> "percent_per_hour"
        GuidedEvidenceKind.OVERALL_POWER, GuidedEvidenceKind.APP_TOTAL_POWER,
        GuidedEvidenceKind.APP_FOREGROUND_POWER, GuidedEvidenceKind.APP_BACKGROUND_POWER -> "mah"
        GuidedEvidenceKind.FOREGROUND_DURATION, GuidedEvidenceKind.BACKGROUND_DURATION,
        GuidedEvidenceKind.WAKELOCK_DURATION -> "millis"
        GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT, GuidedEvidenceKind.JOB_COUNT -> "count"
        else -> null
    }
}
