package com.aegis.apa

import com.aegis.apa.model.*
import java.time.Instant

object VerificationFixtures {
    fun legacyPreviewBytes(): ByteArray = java.util.Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/verification-preview-v1.base64")).readText().trim())
    val start: Instant = Instant.parse("2026-10-01T00:00:00Z")
    val end: Instant = start.plusSeconds(3600)

    fun context() = VerificationSampleContext(
        device = VerificationDeviceContext("Example", "Model", 36, "build-1", VerificationOwnership.USER_CONFIRMED),
        scenario = VerificationScenario.IDLE,
        scenarioNote = "Desk overnight",
        noCharging = VerificationConfirmation.YES
    )

    fun window(source: String = "batterystats", startedAt: Instant = start) = MetricWindow(
        source, VerificationMetricScope.EXPLICIT_WINDOW, startedAt, startedAt.plusSeconds(3600),
        3_600_000, null, VerificationConfirmation.NO
    )

    fun metadata() = DiagnosticVerificationMetadata(
        reportedAt = end,
        device = context().device,
        windowsBySource = listOf("batterystats", "alarm", "jobscheduler").associateWith { window(it) }
    )

    fun snapshot() = PowerDiagnosticSnapshot(
        sampledAtInstant = end.plusSeconds(60),
        collectionDurationMillis = 10,
        sources = listOf("packages", "batterystats", "alarm", "jobscheduler").associateWith {
            DiagnosticSourceResult(it, DiagnosticSourceStatus.AVAILABLE, "raw error must not persist")
        },
        apps = listOf(AppPowerEvidence(
            uid = 10123, packageNames = listOf("com.example.app"), displayNames = listOf("Example"),
            estimatedPowerMah = 100.0, foregroundPowerMah = 20.0, backgroundPowerMah = 80.0,
            wakeLockDurationMillis = 120_000, wakeupCount = 10, alarmCount = 12, jobCount = 4,
            foregroundDurationMillis = 600_000, backgroundDurationMillis = 1_800_000
        )),
        system = SystemPowerEvidence(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, 100.0, true)),
        findings = emptyList(),
        evidenceCoverage = EvidenceCoverage(EvidenceField.entries.associateWith { EvidenceFieldStatus.PARSED }),
        inputSource = DiagnosticInputSource.BUGREPORT
    )

    // Complete production-shaped whitelist: 20 groups, all nine app fields, no user notes.
    fun fullSnapshot() = snapshot().copy(apps = (1..20).map { index ->
        snapshot().apps.single().copy(uid = 10000 + index,
            packageNames = listOf("com.example.app%02d".format(java.util.Locale.ROOT, index)))
    })

    fun productionMetadata() = metadata().copy(reportedAt = null, windowsBySource = mapOf(
        "batterystats" to MetricWindow("batterystats", VerificationMetricScope.SINCE_LAST_CHARGE,
            null, null, 3_600_000, null, VerificationConfirmation.UNKNOWN)))

    fun summary(power: Double = 100.0, startedAt: Instant = start) = DiagnosticVerificationSummary(
        inputSource = DiagnosticInputSource.BUGREPORT,
        generatedAt = startedAt.plusSeconds(3660),
        reportedAt = startedAt.plusSeconds(3600),
        context = context(),
        sources = mapOf("batterystats" to DiagnosticSourceStatus.AVAILABLE),
        metrics = listOf(VerificationMetric(
            VerificationMetricKey(VerificationMetricKind.APP_TOTAL_POWER, listOf("com.example.app")),
            power, VerificationUnit.MAH, VerificationEstimate.ESTIMATED, window(startedAt = startedAt),
            EvidenceFieldStatus.PARSED
        )),
        fingerprint = "sample-$power-$startedAt"
    )

    fun change() = VerificationChange(
        "Disable optional sync", "Re-enable sync", end.plusSeconds(60),
        VerificationConfirmation.YES, VerificationConfirmation.YES, "None noticed"
    )

    fun record() = VerificationRecord(
        id = "record-1", schemaVersion = 1, ruleVersion = 1, scopeId = "local-scope",
        createdAt = end, updatedAt = end.plusSeconds(7200), revision = 2,
        title = "Sync test", state = VerificationState.RETEST_SAVED,
        target = VerificationTarget.AppGroup(listOf("com.example.app")),
        baseline = summary(), change = change(), retest = summary(80.0, end.plusSeconds(120)), closeReason = null
    )
}
