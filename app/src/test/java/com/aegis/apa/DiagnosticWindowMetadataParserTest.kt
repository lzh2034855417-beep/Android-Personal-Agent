package com.aegis.apa

import com.aegis.apa.agent.VerificationSummaryProjector
import com.aegis.apa.model.*
import com.aegis.apa.tool.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DiagnosticWindowMetadataParserTest {
    @Test
    fun indentedCrLfHeadingKeepsOnlyItsOwnDurationAndUnknownEndpoints() {
        // Catches heading/line-ending differences changing selection or invented clock bounds.
        val output = "  Statistics since last charge:\r\n  Time on battery: 1h (100%) realtime\r\n" +
            "  Statistics for vendor scope:\r\n  Time on battery: 30m (100%) realtime"
        val section = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE, output)
        val window = DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.SINCE_LAST_CHARGE, window.scope)
        assertEquals(3_600_000L, window.batteryDurationMillis)
        assertNull(window.startedAt)
        assertNull(window.endedAt)
    }

    @Test
    fun nearLastChargeHeadingRemainsUnknown() {
        // Catches substring-matching a similar OEM heading as the supported scope.
        val section = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE,
            "Statistics since last charge: vendor extension\nTime on battery: 1h (100%) realtime")
        val window = DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.UNKNOWN, window.scope)
        assertNull(window.batteryDurationMillis)
    }

    private val battery = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE, """
        Statistics since last charge:
        Time on battery: 1h 30m (90%) realtime, 1h uptime
        Estimated power use (mAh):
          Capacity: 5000, Computed drain: 100
          UID u0a123: 100
    """.trimIndent())

    @Test
    fun legacyCumulativeReportDoesNotAcquireWindowFromImportTime() {
        // Catches back-calculating a start/end from the import clock.
        val snapshot = PowerDiagnosticPipeline.analyze(listOf(battery), DiagnosticInputSource.BUGREPORT, Instant.parse("2026-10-02T12:00:00Z"))
        val metadata = requireNotNull(snapshot.verificationMetadata)
        val window = metadata.windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.SINCE_LAST_CHARGE, window.scope)
        assertEquals(5_400_000L, window.batteryDurationMillis)
        assertNull(window.startedAt)
        assertNull(window.endedAt)
        assertNull(metadata.reportedAt)
        assertNull(window.resetId)
        assertEquals(VerificationConfirmation.UNKNOWN, window.charging)
    }

    @Test
    fun foreignModelIsNotReplacedByCurrentDevice() {
        // Catches losing report identifiers and substituting current handset identifiers.
        val metadata = DiagnosticWindowMetadataParser.parse(listOf(battery), mapOf(
            "ro.product.manufacturer" to "Foreign Maker", "ro.product.model" to "Foreign Model",
            "ro.build.version.sdk" to "35", "ro.build.version.incremental" to "foreign-build"
        ))
        assertEquals(VerificationDeviceContext("Foreign Maker", "Foreign Model", 35, "foreign-build", VerificationOwnership.UNKNOWN), metadata.device)
        val snapshot = PowerDiagnosticPipeline.analyze(listOf(battery), DiagnosticInputSource.BUGREPORT, Instant.EPOCH)
        val context = VerificationSampleContext(VerificationDeviceContext("Local", "Current Model", 36, "current", VerificationOwnership.USER_CONFIRMED),
            VerificationScenario.IDLE, "", VerificationConfirmation.UNKNOWN)
        val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, metadata, context, VerificationTarget.Overall))
        assertEquals("Foreign Model", summary.context.device.modelCode)
        assertEquals(VerificationOwnership.USER_CONFIRMED, summary.context.device.ownership)
    }

    @Test
    fun batteryWindowIsNotCopiedToAlarm() {
        // Catches assigning battery duration/scope to another cumulative source.
        val metadata = DiagnosticWindowMetadataParser.parse(listOf(battery, RawDiagnosticSection("alarm", DiagnosticSourceStatus.AVAILABLE, "u0a123: 5 wakeups")), emptyMap())
        assertEquals(5_400_000L, metadata.windowsBySource.getValue("batterystats").batteryDurationMillis)
        val alarm = metadata.windowsBySource.getValue("alarm")
        assertEquals("alarm", alarm.source)
        assertEquals(VerificationMetricScope.UNKNOWN, alarm.scope)
        assertNull(alarm.startedAt)
        assertNull(alarm.endedAt)
        assertNull(alarm.batteryDurationMillis)
    }

    @Test
    fun truncatedOrUnavailableOrDuplicateSourceCannotSupplyBatteryDuration() {
        // Catches parsing partial/unavailable/ambiguous source metadata as complete.
        val inputs = listOf(listOf(battery.copy(truncated = true)), listOf(battery.copy(status = DiagnosticSourceStatus.TRUNCATED)),
            listOf(battery.copy(status = DiagnosticSourceStatus.TIMED_OUT)), listOf(battery, battery))
        inputs.forEach { sections ->
            val window = DiagnosticWindowMetadataParser.parse(sections, emptyMap()).windowsBySource.getValue("batterystats")
            assertEquals(VerificationMetricScope.UNKNOWN, window.scope)
            assertNull(window.batteryDurationMillis)
            assertNull(window.startedAt)
            assertNull(window.endedAt)
        }
    }

    @Test
    fun invalidTimezoneFreeAndInventedTimestampsRemainUnknown() {
        // Catches recognizing unverified header or OEM window formats.
        listOf("invalid", "2026-10-02 12:00:00", "2026-10-02T12:00:00Z").forEach { time ->
            val metadata = DiagnosticWindowMetadataParser.parse(listOf(battery.copy(output = battery.output + "\nWindow start: $time\nWindow end: $time")),
                mapOf("reportedAt" to time, "startedAt" to time, "endedAt" to time))
            assertNull(metadata.reportedAt)
            val window = metadata.windowsBySource.getValue("batterystats")
            assertNull(window.startedAt)
            assertNull(window.endedAt)
        }
    }

    @Test
    fun batteryDurationDoesNotRequireCapacityOrInventMissingDuration() {
        // Catches treating capacity/drain as prerequisites for available duration metadata.
        val durationOnly = battery.copy(output = "Statistics since last charge:\nTime on battery: 30m (100%) realtime")
        assertEquals(1_800_000L, DiagnosticWindowMetadataParser.parse(listOf(durationOnly), emptyMap()).windowsBySource.getValue("batterystats").batteryDurationMillis)
        val absent = battery.copy(output = "Estimated power use (mAh):\n  Capacity: 5000, Computed drain: 100")
        val window = DiagnosticWindowMetadataParser.parse(listOf(absent), emptyMap()).windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.UNKNOWN, window.scope)
        assertNull(window.batteryDurationMillis)
    }

    @Test
    fun headerMentionInProseDoesNotSelectEarlierBatteryDuration() {
        // Catches substring matching preceding prose rather than the exact existing section header.
        val section = battery.copy(output = "Note: Statistics since last charge:\nTime on battery: 10m (100%) realtime\n" + battery.output)
        assertEquals(5_400_000L, DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats").batteryDurationMillis)
    }

    @Test
    fun laterStatisticsBlockCannotSupplyMissingLastChargeDuration() {
        // Catches assigning a last-unplugged duration to the preceding last-charge block.
        val section = battery.copy(output = "Statistics since last charge:\nStatistics since last unplugged:\nTime on battery: 40m (100%) realtime")
        val window = DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.SINCE_LAST_CHARGE, window.scope)
        assertNull(window.batteryDurationMillis)
    }

    @Test
    fun ownLastChargeDurationSurvivesUnrelatedLaterDuration() {
        // Catches allowing an unrelated block's duration to make the selected block ambiguous.
        val section = battery.copy(output = battery.output + "\nStatistics since last unplugged:\nTime on battery: 40m (100%) realtime")
        assertEquals(5_400_000L, DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats").batteryDurationMillis)
    }

    @Test
    fun unknownStatisticsBlockAlsoEndsSelectedDurationScan() {
        // Catches stopping only at familiar statistics headers and borrowing from unknown sections.
        val section = battery.copy(output = "Statistics since last charge:\nStatistics for unrecognized scope:\nTime on battery: 40m (100%) realtime")
        assertNull(DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats").batteryDurationMillis)
    }

    @Test
    fun duplicateLastChargeBlocksRemainUnknown() {
        // Catches selecting one of multiple same-scope blocks instead of preserving ambiguity.
        val section = battery.copy(output = battery.output + "\n" + battery.output)
        val window = DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats")
        assertEquals(VerificationMetricScope.UNKNOWN, window.scope)
        assertNull(window.batteryDurationMillis)
    }

    @Test
    fun invalidConflictingAndOversizeBatteryDurationsStayUnknown() {
        // Catches silently choosing the first duration or accepting invalid/overflow values.
        listOf("0ms", "oops", "999999999999999999999h", "30m (100%) realtime\nTime on battery: 40m").forEach { duration ->
            val section = battery.copy(output = "Statistics since last charge:\nTime on battery: $duration (100%) realtime")
            assertNull(DiagnosticWindowMetadataParser.parse(listOf(section), emptyMap()).windowsBySource.getValue("batterystats").batteryDurationMillis)
        }
    }

    @Test
    fun missingMetadataDoesNotGainIdentifiersFromOwnershipConfirmation() {
        // Catches imported user confirmation filling absent report identity.
        val metadata = DiagnosticWindowMetadataParser.parse(listOf(battery), emptyMap())
        assertNull(metadata.device)
        val snapshot = PowerDiagnosticPipeline.analyze(listOf(battery), DiagnosticInputSource.BUGREPORT, Instant.EPOCH)
        val context = VerificationSampleContext(VerificationDeviceContext("Local", "Local", 35, "local", VerificationOwnership.USER_CONFIRMED), VerificationScenario.IDLE, "", VerificationConfirmation.YES)
        val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, metadata, context, VerificationTarget.Overall))
        assertNull(summary.context.device.manufacturer)
        assertNull(summary.context.device.modelCode)
        assertNull(summary.context.device.apiLevel)
        assertNull(summary.context.device.buildVersion)
        assertEquals(VerificationOwnership.USER_CONFIRMED, summary.context.device.ownership)
    }

    @Test
    fun directHeadersStillEnforceWhitelistAndUtf8ValueBounds() {
        // Catches bypassing extraction validation through the public parser.
        val metadata = DiagnosticWindowMetadataParser.parse(emptyList(), mapOf(
            "ro.product.manufacturer" to "M", "ro.product.model" to "界".repeat(86),
            "ro.build.version.sdk" to "35junk", "ro.build.version.incremental" to "build\nforged",
            "ro.serialno" to "unique", "ro.build.fingerprint" to "unique-fingerprint"
        ))
        assertEquals(VerificationDeviceContext("M", null, null, null, VerificationOwnership.UNKNOWN), metadata.device)
        assertNull(DiagnosticWindowMetadataParser.parse(emptyList(), mapOf("ro.serialno" to "unique")).device)
    }
}
