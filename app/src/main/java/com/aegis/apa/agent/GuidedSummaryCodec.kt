package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.util.Locale

object GuidedSummaryCodec {
    const val MAX_BYTES = 48 * 1024
    internal val diagnosticSources = setOf("packages", "batterystats", "alarm", "jobscheduler", "power", "deviceidle", "thermalservice", "wakeup_sources")
    private val sources = diagnosticSources + setOf("battery", "observation")
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
    private val gapIds = setOf("answer_time_range_reversed", "app_groups_omitted", "report_device_unknown",
        "report_time_unknown", "diagnostic_evidence_unavailable", "thermal_status_unknown", "observation_invalid_rate") +
        BatteryObservationValidity.entries.filter { it != BatteryObservationValidity.VALID }.map { "observation_${wire(it)}" }

    /** A deterministic whitelist for size checks and payload construction. No decoder or storage side effects. */
    fun encode(sample: GuidedSample): ByteArray {
        validate(sample)
        val evidence = sample.evidence.sortedBy { it.id }
        val packages = evidence.map { it.packages }.distinct().sortedBy { it.joinToString("\u0000") }
        val windows = evidence.mapNotNull { it.window }.distinct().sortedBy { json(windowValues(it)) }
        val answers = sample.answersAtConfirmation
        val text = obj(
            "schema_version" to "1", "id" to json(sample.id),
            "source_app_group_count" to json(sample.sourceAppGroupCount),
            "omitted_app_group_count" to json(sample.omittedAppGroupCount),
            "diagnostic_input_source" to json(sample.diagnosticInputSource?.let(::wire)),
            "diagnostic_device" to (sample.diagnosticDevice?.let { device -> obj(
                "manufacturer" to json(device.manufacturer), "model_code" to json(device.modelCode),
                "api_level" to json(device.apiLevel), "build_version" to json(device.buildVersion),
                "ownership" to json(device.ownership.wireId)) } ?: "null"),
            "sources" to obj(*sample.sources.toSortedMap().map { (source, status) -> source to json(wire(status)) }.toTypedArray()),
            "verification_eligible_app_groups" to json(sample.verificationEligibleAppGroups.map(packages::indexOf)),
            "answers" to obj("started_at" to json(answers.startedAt?.toString()), "ended_at" to json(answers.endedAt?.toString()),
                "charging" to json(answers.charging.wireId), "mostly_idle" to json(answers.mostlyIdle.wireId),
                "rebooted" to json(answers.rebooted.wireId), "still_hot" to json(answers.stillHot.wireId),
                "activity" to json(wire(answers.activity)), "note" to json(answers.note), "reviewed" to json(answers.reviewed)),
            "packages" to json(packages), "windows" to json(windows.map(::windowValues)),
            // Fixed v1 row order: id, kind, value, source, sourceStatus, fieldStatus, estimate,
            // package dictionary index, collectedAt, reportedAt, window dictionary index.
            "evidence" to json(evidence.map { item -> listOf(item.id, wire(item.kind), when (val value = item.value) {
                is GuidedValue.Number -> listOf("number", value.value, value.unit)
                is GuidedValue.Code -> listOf("code", value.value)
                null -> null
            }, item.source, wire(item.sourceStatus), wire(item.fieldStatus), item.estimate.wireId,
                packages.indexOf(item.packages), item.collectedAt.toString(), item.reportedAt?.toString(),
                item.window?.let(windows::indexOf)) }),
            "gaps" to json(sample.gaps.sortedBy { it.id }.map { listOf(it.id, it.evidenceIds.distinct().sorted()) })
        )
        return text.toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_BYTES) throw GuidedSummaryException("summary_too_large")
        }
    }

    private fun validate(sample: GuidedSample) {
        check(sample.id.isNotBlank())
        if (sample.evidence.map { it.id }.distinct().size != sample.evidence.size) throw GuidedSummaryException("duplicate_evidence_id")
        val groups = sample.evidence.map { it.packages }.filter { it.isNotEmpty() }.distinct()
        check(groups.size <= 20 && sample.sourceAppGroupCount >= groups.size && sample.sourceAppGroupCount >= 0)
        check(sample.omittedAppGroupCount == (sample.sourceAppGroupCount - 20).coerceAtLeast(0))
        check(sample.omittedAppGroupCount == 0 || sample.gaps.any { it.id == "app_groups_omitted" })
        check(sample.sources.keys.all { it in sources })
        check(sample.verificationEligibleAppGroups.all { it.size == 1 && it in groups })
        check(sample.gaps.map { it.id }.distinct().size == sample.gaps.size)
        sample.gaps.forEach { gap ->
            check(gap.id in gapIds && gap.evidenceIds.all { id -> sample.evidence.any { it.id == id } })
        }
        sample.evidence.forEach { item ->
            check(item.id.isNotBlank() && item.source in sources && item.source == sourceFor(item.kind))
            check(sample.sources[item.source]?.let { it == item.sourceStatus } != false)
            check(item.packages.all { packagePattern.matches(it) })
            val appKind = item.kind in setOf(GuidedEvidenceKind.APP_TOTAL_POWER, GuidedEvidenceKind.APP_FOREGROUND_POWER,
                GuidedEvidenceKind.APP_BACKGROUND_POWER, GuidedEvidenceKind.FOREGROUND_DURATION,
                GuidedEvidenceKind.BACKGROUND_DURATION, GuidedEvidenceKind.WAKELOCK_DURATION,
                GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT, GuidedEvidenceKind.JOB_COUNT)
            check(if (appKind) item.packages.isNotEmpty() else item.packages.isEmpty())
            item.window?.let { check(it.source == item.source && it.source in sources) }
            when (val value = item.value) {
                is GuidedValue.Number -> {
                    check(value.value.isFinite() && value.unit == unitFor(item.kind))
                    check(item.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE || value.value >= 0)
                }
                is GuidedValue.Code -> check(when (item.kind) {
                    GuidedEvidenceKind.CHARGING -> value.value == "yes" || value.value == "no"
                    GuidedEvidenceKind.THERMAL_STATUS -> value.value in setOf("0", "1", "2", "3", "4", "5", "6")
                    else -> false
                })
                null -> Unit
            }
            if (item.value != null) {
                check(item.sourceStatus == DiagnosticSourceStatus.AVAILABLE || item.sourceStatus == DiagnosticSourceStatus.TRUNCATED)
                check(item.fieldStatus == EvidenceFieldStatus.PARSED || item.fieldStatus == EvidenceFieldStatus.TRUNCATED)
            }
        }
    }

    private fun sourceFor(kind: GuidedEvidenceKind): String = when (kind) {
        GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT -> "alarm"
        GuidedEvidenceKind.JOB_COUNT -> "jobscheduler"
        GuidedEvidenceKind.THERMAL_STATUS -> "thermalservice"
        GuidedEvidenceKind.BATTERY_TEMPERATURE, GuidedEvidenceKind.CHARGING -> "battery"
        GuidedEvidenceKind.OBSERVATION_RATE -> "observation"
        else -> "batterystats"
    }

    private fun unitFor(kind: GuidedEvidenceKind): String? = when (kind) {
        GuidedEvidenceKind.OVERALL_POWER, GuidedEvidenceKind.APP_TOTAL_POWER,
        GuidedEvidenceKind.APP_FOREGROUND_POWER, GuidedEvidenceKind.APP_BACKGROUND_POWER -> "mah"
        GuidedEvidenceKind.FOREGROUND_DURATION, GuidedEvidenceKind.BACKGROUND_DURATION, GuidedEvidenceKind.WAKELOCK_DURATION -> "millis"
        GuidedEvidenceKind.WAKEUP_COUNT, GuidedEvidenceKind.ALARM_COUNT, GuidedEvidenceKind.JOB_COUNT -> "count"
        GuidedEvidenceKind.BATTERY_TEMPERATURE -> "celsius"
        GuidedEvidenceKind.OBSERVATION_RATE -> "percent_per_hour"
        else -> null
    }

    // Fixed v1 window order: source, scope, start, end, battery duration, reset token, charging.
    private fun windowValues(window: MetricWindow): List<Any?> = listOf(window.source, window.scope.wireId,
        window.startedAt?.toString(), window.endedAt?.toString(), window.batteryDurationMillis, window.resetId, window.charging.wireId)

    private fun wire(value: Enum<*>) = value.name.lowercase(Locale.ROOT)
    private fun check(condition: Boolean) { if (!condition) throw GuidedSummaryException("invalid_summary") }
    private fun obj(vararg fields: Pair<String, String>) = fields.joinToString(prefix = "{", postfix = "}", separator = ",") {
        "${quote(it.first)}:${it.second}"
    }
    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Int, is Long, is Boolean -> value.toString()
        is Double -> { check(value.isFinite()); if (value == 0.0) "0" else value.toString() }
        is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { json(it) }
        else -> throw GuidedSummaryException("invalid_summary")
    }
    private fun quote(value: String): String = buildString {
        append('"')
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char.code < 32 -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
                char.isHighSurrogate() -> {
                    check(index < value.length && value[index].isLowSurrogate())
                    append(char).append(value[index++])
                }
                char.isLowSurrogate() -> check(false)
                else -> append(char)
            }
        }
        append('"')
    }
}
