package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.math.BigDecimal
import java.security.MessageDigest

object VerificationSummaryProjector {
    private val knownSources = setOf("packages", "batterystats", "alarm", "jobscheduler", "power", "deviceidle", "thermalservice", "wakeup_sources")

    fun project(snapshot: PowerDiagnosticSnapshot, metadata: DiagnosticVerificationMetadata,
        context: VerificationSampleContext, target: VerificationTarget): DiagnosticVerificationSummary? {
        val inputSource = snapshot.inputSource ?: return null
        val sources = snapshot.sources.filterKeys { it in knownSources }.mapValues { it.value.status }
        if (sources.isEmpty()) return null
        val metrics = mutableListOf<VerificationMetric>()
        val groups = snapshot.apps.filter { it.packageNames.isNotEmpty() }.groupBy { it.packageNames.distinct().sorted() }
        val ambiguousGroups = groups.filterValues { it.size > 1 }.keys
        val identityCoverage = snapshot.evidenceCoverage.fields[EvidenceField.UID_PACKAGES]
        val identityStatus = when {
            sources["packages"] != DiagnosticSourceStatus.AVAILABLE && sources["packages"] != DiagnosticSourceStatus.TRUNCATED ->
                EvidenceFieldStatus.SOURCE_UNAVAILABLE
            sources["packages"] == DiagnosticSourceStatus.TRUNCATED || identityCoverage == EvidenceFieldStatus.TRUNCATED ->
                EvidenceFieldStatus.TRUNCATED
            else -> identityCoverage ?: EvidenceFieldStatus.NOT_PARSED
        }

        fun metric(kind: VerificationMetricKind, packages: List<String>, rawValue: Double?, unit: VerificationUnit,
            estimate: VerificationEstimate, source: String, field: EvidenceField) {
            val sourceStatus = sources[source]
            val sourceAvailable = sourceStatus == DiagnosticSourceStatus.AVAILABLE || sourceStatus == DiagnosticSourceStatus.TRUNCATED
            val key = VerificationMetricKey(kind, packages)
            val ambiguous = key.packageNames in ambiguousGroups
            val appIdentityStatus = identityStatus.takeIf { key.packageNames.isNotEmpty() }
            val numericValue = rawValue?.takeIf { it.isFinite() && it >= 0.0 }
            // A partial mapping can omit shared-UID members, so it cannot own an app value.
            val value = numericValue?.takeIf { sourceAvailable && !ambiguous &&
                (appIdentityStatus == null || appIdentityStatus == EvidenceFieldStatus.PARSED) }
            val coverage = snapshot.evidenceCoverage.fields[field]
            val status = when {
                ambiguous -> EvidenceFieldStatus.NOT_PARSED
                !sourceAvailable -> EvidenceFieldStatus.SOURCE_UNAVAILABLE
                appIdentityStatus != null && appIdentityStatus != EvidenceFieldStatus.PARSED -> appIdentityStatus
                sourceStatus == DiagnosticSourceStatus.TRUNCATED || coverage == EvidenceFieldStatus.TRUNCATED -> EvidenceFieldStatus.TRUNCATED
                coverage != null && coverage != EvidenceFieldStatus.PARSED -> coverage
                rawValue != null && numericValue == null -> EvidenceFieldStatus.NOT_PARSED
                value == null -> EvidenceFieldStatus.NOT_PRESENT
                else -> EvidenceFieldStatus.PARSED
            }
            // A window belongs only to its own source; mismatched metadata is unknown.
            val window = metadata.windowsBySource[source]?.takeIf { it.source == source }
                ?: MetricWindow(source, VerificationMetricScope.UNKNOWN, null, null, null, null, VerificationConfirmation.UNKNOWN)
            metrics += VerificationMetric(key, value, unit,
                if (value == null) VerificationEstimate.UNKNOWN else estimate, window, status)
        }

        val drain = snapshot.system.batteryDrainWindow
        // The archive stores scalar metrics; do not turn an interval endpoint into an exact value.
        metric(VerificationMetricKind.OVERALL_POWER, emptyList(), drain?.exactDrainMah, VerificationUnit.MAH,
            if (drain?.usesActualDrain == true) VerificationEstimate.ACTUAL else VerificationEstimate.ESTIMATED,
            "batterystats", EvidenceField.OVERALL_POWER_MAH)

        // Package identity lacks user/profile scope. Multiple rows cannot be safely assigned or summed.
        val ranked = groups.map { (packages, rows) ->
            if (rows.size == 1) rows.single().copy(packageNames = packages)
            else AppPowerEvidence(uid = null, packageNames = packages, sharedUid = packages.size > 1)
        }.sortedWith(
            compareByDescending<AppPowerEvidence> { it.estimatedPowerMah?.takeIf { value -> value.isFinite() && value >= 0.0 } ?: -1.0 }
                .thenBy { packageSortKey(it.packageNames) }
        )
        val targetPackages = (target as? VerificationTarget.AppGroup)?.packageNames
        val selectedTarget = ranked.firstOrNull { VerificationTarget.AppGroup(it.packageNames).packageNames == targetPackages }
        val selected = if (selectedTarget == null) ranked.take(if (targetPackages == null) 20 else 19)
            else listOf(selectedTarget) + ranked.filter { it !== selectedTarget }.take(19)
        selected.forEach { app ->
            val packages = app.packageNames
            metric(VerificationMetricKind.APP_TOTAL_POWER, packages, app.estimatedPowerMah, VerificationUnit.MAH,
                VerificationEstimate.ESTIMATED, "batterystats", EvidenceField.POWER_MAH)
            metric(VerificationMetricKind.APP_FOREGROUND_POWER, packages, app.foregroundPowerMah, VerificationUnit.MAH,
                VerificationEstimate.ESTIMATED, "batterystats", EvidenceField.POWER_MAH)
            metric(VerificationMetricKind.APP_BACKGROUND_POWER, packages, app.backgroundPowerMah, VerificationUnit.MAH,
                VerificationEstimate.ESTIMATED, "batterystats", EvidenceField.POWER_MAH)
            metric(VerificationMetricKind.FOREGROUND_DURATION, packages, app.foregroundDurationMillis?.toDouble(), VerificationUnit.MILLIS,
                VerificationEstimate.ACTUAL, "batterystats", EvidenceField.FOREGROUND_TIME)
            metric(VerificationMetricKind.BACKGROUND_DURATION, packages, app.backgroundDurationMillis?.toDouble(), VerificationUnit.MILLIS,
                VerificationEstimate.ACTUAL, "batterystats", EvidenceField.FOREGROUND_TIME)
            metric(VerificationMetricKind.WAKELOCK_DURATION, packages, app.wakeLockDurationMillis?.toDouble(), VerificationUnit.MILLIS,
                VerificationEstimate.ACTUAL, "batterystats", EvidenceField.WAKELOCK_TIME)
            metric(VerificationMetricKind.WAKEUP_COUNT, packages, app.wakeupCount?.toDouble(), VerificationUnit.COUNT,
                VerificationEstimate.ACTUAL, "alarm", EvidenceField.WAKEUP_ALARMS)
            metric(VerificationMetricKind.ALARM_COUNT, packages, app.alarmCount?.toDouble(), VerificationUnit.COUNT,
                VerificationEstimate.ACTUAL, "alarm", EvidenceField.WAKEUP_ALARMS)
            metric(VerificationMetricKind.JOB_COUNT, packages, app.jobCount?.toDouble(), VerificationUnit.COUNT,
                VerificationEstimate.ACTUAL, "jobscheduler", EvidenceField.JOBS)
        }
        if (metrics.none { it.value != null }) return null

        val device = when (inputSource) {
            DiagnosticInputSource.BUGREPORT -> {
                val ownership = if (context.device.ownership == VerificationOwnership.USER_CONFIRMED) VerificationOwnership.USER_CONFIRMED else VerificationOwnership.UNKNOWN
                (metadata.device ?: VerificationDeviceContext(null, null, null, null, ownership)).copy(ownership = ownership)
            }
            DiagnosticInputSource.ROOT -> (metadata.device ?: context.device).copy(ownership = VerificationOwnership.LOCAL_COLLECTION)
        }
        return DiagnosticVerificationSummary(inputSource, snapshot.sampledAtInstant, metadata.reportedAt, context.copy(device = device),
            sources, metrics.toList(), fingerprint(inputSource, sources, metrics))
    }

    private fun packageSortKey(packages: List<String>) = packages.distinct().sorted().joinToString("\u0000")

    private fun fingerprint(inputSource: DiagnosticInputSource, sources: Map<String, DiagnosticSourceStatus>, metrics: List<VerificationMetric>): String {
        // Length-prefixed tokens avoid delimiter collisions; generation/report times and user text are excluded.
        val canonical = buildString {
            fun token(value: String?) {
                if (value == null) append("-1:") else append(value.length).append(':').append(value)
            }
            token(when (inputSource) { DiagnosticInputSource.BUGREPORT -> "bugreport"; DiagnosticInputSource.ROOT -> "root" })
            sources.toSortedMap().forEach { (source, status) -> token(source); token(status.name) }
            metrics.sortedWith(compareBy<VerificationMetric> { it.key.kind.wireId }.thenBy { packageSortKey(it.key.packageNames) }).forEach { metric ->
                token(metric.key.kind.wireId)
                token(metric.key.packageNames.size.toString())
                metric.key.packageNames.forEach { token(it) }
                token(metric.value?.let { if (it == 0.0) "0" else BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() })
                token(metric.unit.wireId); token(metric.estimate.wireId); token(metric.status.name)
                val window = metric.window
                token(window.source); token(window.scope.wireId); token(window.startedAt?.toString()); token(window.endedAt?.toString())
                token(window.batteryDurationMillis?.toString()); token(window.resetId); token(window.charging.wireId)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
