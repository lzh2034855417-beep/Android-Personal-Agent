package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.util.Locale

object GuidedEvidenceAdapter {
    fun adapt(input: GuidedInput, sampleId: String, answers: GuidedAnswers): GuidedSampleResult {
        val evidence = mutableListOf<GuidedEvidence>()
        val gaps = mutableListOf<GuidedGap>()
        var groupCount = 0
        var omittedCount = 0
        var diagnosticDevice: VerificationDeviceContext? = null
        val verificationEligibleAppGroups = mutableListOf<List<String>>()
        val sources = linkedMapOf<String, DiagnosticSourceStatus>()
        if (answers.startedAt != null && answers.endedAt != null && answers.endedAt < answers.startedAt) {
            gaps += GuidedGap("answer_time_range_reversed")
        }
        input.diagnostic?.let { report ->
            sources += report.sources.filterKeys { it in GuidedSummaryCodec.diagnosticSources }.mapValues { it.value.status }
            val groups = report.apps.map { it.packageNames.distinct().sorted() }.filter { it.isNotEmpty() }
                .distinct().sortedBy { it.joinToString("\u0000") }
            groupCount = groups.size
            omittedCount = (groupCount - 20).coerceAtLeast(0)
            if (omittedCount > 0) gaps += GuidedGap("app_groups_omitted")
            val retained = groups.take(20).toSet()
            // Keep duplicate rows so the existing projector retains its ambiguous-identity gate.
            val boundedReport = report.copy(apps = report.apps.filter { it.packageNames.distinct().sorted() in retained })
            val metadata = report.verificationMetadata ?: DiagnosticVerificationMetadata(null, null, emptyMap())
            diagnosticDevice = when (report.inputSource) {
                DiagnosticInputSource.ROOT -> (metadata.device ?: unknownDevice()).copy(ownership = VerificationOwnership.LOCAL_COLLECTION)
                DiagnosticInputSource.BUGREPORT -> (metadata.device ?: unknownDevice()).copy(ownership = VerificationOwnership.UNKNOWN)
                null -> unknownDevice()
            }
            if (diagnosticDevice?.ownership == VerificationOwnership.UNKNOWN) gaps += GuidedGap("report_device_unknown")
            if (metadata.reportedAt == null) gaps += GuidedGap("report_time_unknown")
            val context = VerificationSampleContext(diagnosticDevice!!, VerificationScenario.OTHER, "", VerificationConfirmation.UNKNOWN)
            val projected = VerificationSummaryProjector.project(boundedReport, metadata, context, VerificationTarget.Overall)
            if (projected == null) gaps += GuidedGap("diagnostic_evidence_unavailable")
            projected?.metrics?.sortedWith(compareBy<VerificationMetric> { it.key.packageNames.joinToString("\u0000") }
                .thenBy { it.key.kind.ordinal })?.forEachIndexed { index, metric ->
                val value = metric.value?.takeIf {
                    metric.status == EvidenceFieldStatus.PARSED || metric.status == EvidenceFieldStatus.TRUNCATED
                }
                evidence += GuidedEvidence("$sampleId:diagnostic:$index", GuidedEvidenceKind.valueOf(metric.key.kind.name),
                    value?.let { GuidedValue.Number(it, metric.unit.wireId) }, metric.window.source,
                    sources[metric.window.source] ?: DiagnosticSourceStatus.UNSUPPORTED, metric.status,
                    if (value == null) VerificationEstimate.UNKNOWN else metric.estimate,
                    metric.key.packageNames, report.sampledAtInstant, metadata.reportedAt, metric.window)
            }
            // Reuse the real policy with the original UID only while raw input is transient.
            // Complete projected fields gate every numeric signal; raw unavailable values never grant allowance.
            if (sources["packages"] == DiagnosticSourceStatus.AVAILABLE &&
                report.evidenceCoverage.fields[EvidenceField.UID_PACKAGES] == EvidenceFieldStatus.PARSED) {
                boundedReport.apps.groupBy { it.packageNames.distinct().sorted() }.forEach { (packages, rows) ->
                    val app = rows.singleOrNull()
                    if (app != null && app.uid != null && !app.sharedUid && packages.size == 1) {
                        fun number(kind: GuidedEvidenceKind): Double? = evidence.singleOrNull {
                            it.packages == packages && it.kind == kind &&
                                it.sourceStatus == DiagnosticSourceStatus.AVAILABLE && it.fieldStatus == EvidenceFieldStatus.PARSED
                        }?.let { (it.value as? GuidedValue.Number)?.value }
                        val safeApp = app.copy(packageNames = packages,
                            estimatedPowerMah = number(GuidedEvidenceKind.APP_TOTAL_POWER),
                            backgroundPowerMah = number(GuidedEvidenceKind.APP_BACKGROUND_POWER),
                            wakeLockDurationMillis = number(GuidedEvidenceKind.WAKELOCK_DURATION)?.let { app.wakeLockDurationMillis })
                        val verdict = LocalPowerAttributionEngine.attribute(boundedReport.copy(apps = listOf(safeApp)))
                        if (verdict.backgroundSuspects.any { it.packageNames == packages && it.maxAdviceLevel == AdviceLevel.RESTRICT }) {
                            verificationEligibleAppGroups += packages
                        }
                    }
                }
            }
            val thermalSource = sources["thermalservice"]
            if (thermalSource != null || report.system.thermalStatus != null) {
                val raw = report.system.thermalStatus
                val available = thermalSource == DiagnosticSourceStatus.AVAILABLE || thermalSource == DiagnosticSourceStatus.TRUNCATED
                val value = raw?.takeIf { available && it in 0..6 }?.let { GuidedValue.Code(it.toString()) }
                val status = when {
                    !available -> EvidenceFieldStatus.SOURCE_UNAVAILABLE
                    raw == null -> EvidenceFieldStatus.NOT_PRESENT
                    raw !in 0..6 -> EvidenceFieldStatus.NOT_PARSED
                    thermalSource == DiagnosticSourceStatus.TRUNCATED -> EvidenceFieldStatus.TRUNCATED
                    else -> EvidenceFieldStatus.PARSED
                }
                evidence += GuidedEvidence("$sampleId:thermal", GuidedEvidenceKind.THERMAL_STATUS, value, "thermalservice",
                    thermalSource ?: DiagnosticSourceStatus.UNSUPPORTED, status,
                    if (value == null) VerificationEstimate.UNKNOWN else VerificationEstimate.ACTUAL,
                    emptyList(), report.sampledAtInstant, metadata.reportedAt, null)
                if (value == null) gaps += GuidedGap("thermal_status_unknown", listOf("$sampleId:thermal"))
            }
        }
        input.device?.let { device ->
            sources["battery"] = DiagnosticSourceStatus.AVAILABLE
            val raw = device.batteryInfo.temperatureCelsius
            val temperature = raw?.takeIf { it.isFinite() }
            evidence += GuidedEvidence("$sampleId:battery_temperature", GuidedEvidenceKind.BATTERY_TEMPERATURE,
                temperature?.let { GuidedValue.Number(it, "celsius") }, "battery", DiagnosticSourceStatus.AVAILABLE,
                if (raw == null) EvidenceFieldStatus.NOT_PRESENT else if (temperature == null) EvidenceFieldStatus.NOT_PARSED else EvidenceFieldStatus.PARSED,
                if (temperature == null) VerificationEstimate.UNKNOWN else VerificationEstimate.ACTUAL,
                emptyList(), device.sampledAtInstant, null, null)
            val point = BatteryObservationPoint.from(device)
            evidence += GuidedEvidence("$sampleId:charging", GuidedEvidenceKind.CHARGING,
                if (point.powerStateKnown) GuidedValue.Code(if (point.charging) "yes" else "no") else null,
                "battery", DiagnosticSourceStatus.AVAILABLE,
                if (point.powerStateKnown) EvidenceFieldStatus.PARSED else EvidenceFieldStatus.NOT_PRESENT,
                if (point.powerStateKnown) VerificationEstimate.ACTUAL else VerificationEstimate.UNKNOWN,
                emptyList(), device.sampledAtInstant, null, null)
        }
        input.observation?.let { observation ->
            sources["observation"] = DiagnosticSourceStatus.AVAILABLE
            if (!observation.isUsableEvidence) {
                gaps += GuidedGap("observation_${observation.validity.name.lowercase(Locale.ROOT)}")
            } else {
                val rate = observation.drainPercentPerHour?.takeIf { it.isFinite() && it >= 0 }
                val validWindow = observation.end.sampledAtInstant > observation.start.sampledAtInstant && observation.durationMillis > 0
                if (!validWindow || rate == null) gaps += GuidedGap("observation_invalid_rate")
                else evidence += GuidedEvidence("$sampleId:observation_rate", GuidedEvidenceKind.OBSERVATION_RATE,
                    GuidedValue.Number(rate, "percent_per_hour"), "observation", DiagnosticSourceStatus.AVAILABLE,
                    EvidenceFieldStatus.PARSED, VerificationEstimate.ESTIMATED, emptyList(), observation.end.sampledAtInstant,
                    null, MetricWindow("observation", VerificationMetricScope.EXPLICIT_WINDOW,
                        observation.start.sampledAtInstant, observation.end.sampledAtInstant, observation.durationMillis,
                        null, VerificationConfirmation.UNKNOWN))
            }
        }
        val sample = GuidedSample(sampleId, evidence, answers.copy(), groupCount, omittedCount, gaps,
            input.diagnostic?.inputSource, diagnosticDevice, sources, verificationEligibleAppGroups)
        return try {
            GuidedSummaryCodec.encode(sample)
            GuidedSampleResult.Ready(sample)
        } catch (failure: GuidedSummaryException) { GuidedSampleResult.Rejected(failure.reasonId) }
    }

    private fun unknownDevice() = VerificationDeviceContext(null, null, null, null, VerificationOwnership.UNKNOWN)
}
