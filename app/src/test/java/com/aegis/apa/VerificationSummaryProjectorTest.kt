package com.aegis.apa

import com.aegis.apa.agent.VerificationSummaryProjector
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class VerificationSummaryProjectorTest {
    private fun project(snapshot: PowerDiagnosticSnapshot = VerificationFixtures.snapshot(), metadata: DiagnosticVerificationMetadata = VerificationFixtures.metadata(),
        context: VerificationSampleContext = VerificationFixtures.context(), target: VerificationTarget = VerificationTarget.Overall) =
        VerificationSummaryProjector.project(snapshot, metadata, context, target)!!

    @Test
    fun targetSurvivesTopTwentyLimit() {
        // Catches dropping the selected target when it ranks below the first 20 groups.
        val snapshot = VerificationFixtures.snapshot().copy(apps = (1..21).map {
            AppPowerEvidence(it, listOf("com.example.app$it"), estimatedPowerMah = (22 - it).toDouble())
        })
        val result = project(snapshot, target = VerificationTarget.AppGroup(listOf("com.example.app21")))
        val groups = result.metrics.map { it.key.packageNames }.filter { it.isNotEmpty() }.distinct()
        assertEquals(20, groups.size)
        assertTrue(groups.contains(listOf("com.example.app21")))
        assertFalse(groups.contains(listOf("com.example.app20")))
    }

    @Test
    fun duplicatePackageGroupDoesNotConsumeExtraSlots() {
        // Catches rows from different UIDs spending the same package-group slot twice.
        val apps = (1..21).map {
            AppPowerEvidence(it, listOf("com.example.app$it"), estimatedPowerMah = (22 - it).toDouble())
        }
        val summary = project(VerificationFixtures.snapshot().copy(apps = apps + apps.first().copy(uid = 20001)),
            target = VerificationTarget.AppGroup(listOf("com.example.app1")))
        assertEquals(20, summary.metrics.map { it.key.packageNames }.filter { it.isNotEmpty() }.distinct().size)
        assertEquals(summary.metrics.size, summary.metrics.map { it.key }.distinct().size)
    }

    @Test
    fun duplicatePackageGroupValuesCannotBeAssignedEvenWhenEqual() {
        // Catches selecting/summing evidence when the package key cannot disambiguate profiles.
        val app = VerificationFixtures.snapshot().apps.single()
        val summary = project(VerificationFixtures.snapshot().copy(apps = listOf(app, app.copy(uid = 110123))))
        val groupMetrics = summary.metrics.filter { it.key.packageNames == listOf("com.example.app") }
        groupMetrics.forEach {
            assertNull(it.value)
            assertEquals(EvidenceFieldStatus.NOT_PARSED, it.status)
            assertEquals(VerificationEstimate.UNKNOWN, it.estimate)
        }
        assertEquals(9, groupMetrics.size)
        assertEquals(100.0, summary.metrics.first { it.key.kind == VerificationMetricKind.OVERALL_POWER }.value!!, 0.0)
    }

    @Test
    fun packageOrderDoesNotChangeFingerprint() {
        // Catches fingerprints using transient UID, labels, import time, or note text.
        val base = VerificationFixtures.snapshot().copy(apps = listOf(
            VerificationFixtures.snapshot().apps.single().copy(packageNames = listOf("z", "a", "z"), sharedUid = true)
        ))
        val reordered = base.copy(sampledAtInstant = base.sampledAtInstant.plusSeconds(999), apps = listOf(
            base.apps.single().copy(uid = 99999, packageNames = listOf("a", "z"), displayNames = listOf("Changed"))
        ))
        val original = project(base)
        val result = project(reordered, context = VerificationFixtures.context().copy(scenarioNote = "Another note"))
        assertEquals(original.fingerprint, result.fingerprint)
        assertEquals(64, result.fingerprint.length)
        assertEquals(listOf("a", "z"), result.metrics.first { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }.key.packageNames)
        assertEquals(100.0, result.metrics.first { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }.value!!, 0.0)
        assertEquals(1, result.metrics.filter { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }.size)
        assertNotEquals(original.fingerprint, project(base.copy(apps = listOf(base.apps.single().copy(estimatedPowerMah = 101.0)))).fingerprint)
        assertNotEquals(original.fingerprint, project(base, VerificationFixtures.metadata().copy(
            windowsBySource = mapOf("batterystats" to VerificationFixtures.window(startedAt = VerificationFixtures.start.plusSeconds(1)))
        )).fingerprint)
    }

    @Test
    fun missingValuesAndSourceSpecificUnknownWindowsStayUnknown() {
        // Catches inventing zeroes and borrowing batterystats windows for alarm/jobs data.
        val app = VerificationFixtures.snapshot().apps.single().copy(backgroundPowerMah = null, jobCount = null)
        val summary = project(VerificationFixtures.snapshot().copy(apps = listOf(app)),
            VerificationFixtures.metadata().copy(reportedAt = null, windowsBySource = mapOf("batterystats" to VerificationFixtures.window())))
        val background = summary.metrics.first { it.key.kind == VerificationMetricKind.APP_BACKGROUND_POWER }
        assertNull(background.value)
        assertEquals(EvidenceFieldStatus.NOT_PRESENT, background.status)
        assertEquals(VerificationEstimate.UNKNOWN, background.estimate)
        val jobs = summary.metrics.first { it.key.kind == VerificationMetricKind.JOB_COUNT }
        assertNull(jobs.value)
        assertNull(jobs.window.startedAt)
        assertNull(jobs.window.endedAt)
        assertNull(jobs.window.batteryDurationMillis)
        assertEquals(VerificationMetricScope.UNKNOWN, jobs.window.scope)
        assertEquals("jobscheduler", jobs.window.source)
        val alarms = summary.metrics.first { it.key.kind == VerificationMetricKind.ALARM_COUNT }
        assertEquals(12.0, alarms.value!!, 0.0)
        assertNull(alarms.window.startedAt)
        assertNull(summary.reportedAt)
    }

    @Test
    fun importedDeviceMetadataNeverFallsBackToCurrentDevice() {
        // Catches incorrectly assigning an imported report to the current handset.
        val summary = project(metadata = VerificationFixtures.metadata().copy(device = null))
        assertNull(summary.context.device.modelCode)
        assertNull(summary.context.device.apiLevel)
        assertEquals(VerificationOwnership.USER_CONFIRMED, summary.context.device.ownership)
        val root = project(VerificationFixtures.snapshot().copy(inputSource = DiagnosticInputSource.ROOT),
            VerificationFixtures.metadata().copy(device = null))
        assertEquals("Model", root.context.device.modelCode)
        assertEquals(VerificationOwnership.LOCAL_COLLECTION, root.context.device.ownership)
    }

    @Test
    fun importedMetadataCannotConfirmItsOwnDeviceOwnership() {
        // Catches treating untrusted report metadata as a user confirmation.
        val context = VerificationFixtures.context().copy(device = VerificationFixtures.context().device.copy(ownership = VerificationOwnership.UNKNOWN))
        val result = project(context = context)
        assertEquals("Model", result.context.device.modelCode)
        assertEquals(VerificationOwnership.UNKNOWN, result.context.device.ownership)
        val missing = project(metadata = VerificationFixtures.metadata().copy(device = null), context = context)
        assertNull(missing.context.device.modelCode)
        assertEquals(VerificationOwnership.UNKNOWN, missing.context.device.ownership)
    }

    @Test
    fun projectionRetainsTypedFailureAndDropsInvalidNumbers() {
        // Catches untrusted NaN/Infinity/negative data surviving as comparable evidence.
        val base = VerificationFixtures.snapshot()
        val summary = project(base.copy(apps = listOf(base.apps.single().copy(
            estimatedPowerMah = Double.NaN, foregroundPowerMah = Double.POSITIVE_INFINITY,
            backgroundPowerMah = -1.0, alarmCount = -1
        )), evidenceCoverage = EvidenceCoverage(base.evidenceCoverage.fields + (EvidenceField.JOBS to EvidenceFieldStatus.TRUNCATED))))
        listOf(VerificationMetricKind.APP_TOTAL_POWER, VerificationMetricKind.APP_FOREGROUND_POWER,
            VerificationMetricKind.APP_BACKGROUND_POWER, VerificationMetricKind.ALARM_COUNT).forEach { kind ->
            assertNull(summary.metrics.first { it.key.kind == kind }.value)
        }
        assertEquals(EvidenceFieldStatus.TRUNCATED, summary.metrics.first { it.key.kind == VerificationMetricKind.JOB_COUNT }.status)
        assertEquals(DiagnosticSourceStatus.AVAILABLE, summary.sources["batterystats"])
        assertEquals(VerificationEstimate.ACTUAL, summary.metrics.first { it.key.kind == VerificationMetricKind.OVERALL_POWER }.estimate)
    }

    @Test
    fun noSourceNoInputOrNoUsableWhiteListedValueCannotCreateSummary() {
        // Catches enabling records for empty diagnostics or inventing missing input provenance.
        val base = VerificationFixtures.snapshot()
        listOf(base.copy(inputSource = null), base.copy(sources = emptyMap()), base.copy(apps = emptyList(), system = SystemPowerEvidence()),
            base.copy(sources = base.sources.mapValues { it.value.copy(status = DiagnosticSourceStatus.PERMISSION_DENIED) })).forEach {
            assertNull(VerificationSummaryProjector.project(it, VerificationFixtures.metadata(), VerificationFixtures.context(), VerificationTarget.Overall))
        }
    }
}
