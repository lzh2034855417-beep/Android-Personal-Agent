package com.aegis.apa

import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.agent.VerificationSummaryProjector
import com.aegis.apa.model.EvidenceField
import com.aegis.apa.model.EvidenceFieldStatus
import com.aegis.apa.model.VerificationEstimate
import com.aegis.apa.model.VerificationMetricKind
import com.aegis.apa.model.VerificationTarget
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.tool.PowerDiagnosticPipeline
import com.aegis.apa.tool.RawDiagnosticSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class PowerDiagnosticPipelineTest {
    @Test
    fun importedAnalysisDoesNotRunOnCallingUiThread() = kotlinx.coroutines.runBlocking {
        val caller = Thread.currentThread()
        var parserThread: Thread? = null
        val imported = com.aegis.apa.tool.BugReportReadResult.Success(listOf(
            RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE,
                "Estimated power use (mAh):\n  UID u0a123: 42.0"),
            RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE,
                "Package [com.example.chat] (abc):\n  userId=10123")
        ))
        val snapshot = PowerDiagnosticPipeline.analyzeImported(imported, Instant.EPOCH, 0) {
            parserThread = Thread.currentThread()
            "Chat"
        }
        org.junit.Assert.assertNotSame(caller, parserThread)
        assertEquals(42.0, snapshot.apps.single().estimatedPowerMah!!, 0.0)
        assertEquals(listOf("Chat"), snapshot.apps.single().displayNames)
    }

    private val mappingBattery = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE, """
        Statistics since last charge:
        Time on battery: 1h (100%) realtime
        Estimated power use (mAh):
          Capacity: 5000, Computed drain: 200
          UID u0a123: 42.5 fg: 12.5 (10m) bg: 30.0 (20m)
        Uid u0a123:
          Wake lock sync: 2m partial (1 times) realtime
    """.trimIndent())

    private fun mappingSnapshot(packages: RawDiagnosticSection?, battery: RawDiagnosticSection = mappingBattery) =
        PowerDiagnosticPipeline.analyze(listOfNotNull(packages, battery,
            RawDiagnosticSection("alarm", DiagnosticSourceStatus.AVAILABLE, "u0a123: 5 wakeups, 8 alarms"),
            RawDiagnosticSection("jobscheduler", DiagnosticSourceStatus.AVAILABLE, "JOB #u0a123/1: 4 times")),
            DiagnosticInputSource.BUGREPORT, Instant.EPOCH)

    @Test
    fun truncatedMappingCannotProduceParsedAppMetric() {
        // Catches attributing shared-UID power to A when truncation removed package B.
        listOf(
            RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE, "package:com.example.a uid:10123", truncated = true),
            RawDiagnosticSection("packages", DiagnosticSourceStatus.TRUNCATED, "package:com.example.a uid:10123")
        ).forEach { mapping ->
            val snapshot = mappingSnapshot(mapping)
            assertEquals(listOf("com.example.a"), snapshot.apps.single().packageNames)
            assertEquals(EvidenceFieldStatus.TRUNCATED, snapshot.evidenceCoverage.fields[EvidenceField.UID_PACKAGES])
            val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, requireNotNull(snapshot.verificationMetadata),
                VerificationFixtures.context(), VerificationTarget.AppGroup(listOf("com.example.a"))))
            val apps = summary.metrics.filter { it.key.packageNames.isNotEmpty() }
            assertEquals(9, apps.size)
            apps.forEach {
                assertEquals(EvidenceFieldStatus.TRUNCATED, it.status)
                assertNull(it.value)
                assertEquals(VerificationEstimate.UNKNOWN, it.estimate)
            }
            val overall = summary.metrics.single { it.key.kind == VerificationMetricKind.OVERALL_POWER }
            assertEquals(EvidenceFieldStatus.PARSED, overall.status)
            assertEquals(200.0, overall.value!!, 0.0)
        }
    }

    @Test
    fun missingOrUnparsedMappingKeepsOnlyOverallEvidence() {
        // Catches inventing a package target for unmapped UID evidence.
        listOf(null, RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE, "unrecognized mapping")).forEach { mapping ->
            val snapshot = mappingSnapshot(mapping)
            val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, requireNotNull(snapshot.verificationMetadata),
                VerificationFixtures.context(), VerificationTarget.Overall))
            assertTrue(summary.metrics.none { it.key.packageNames.isNotEmpty() })
            assertEquals(200.0, summary.metrics.single().value!!, 0.0)
        }
    }

    @Test
    fun failedMappingCannotAuthenticatePackagesLeftInOutput() {
        // Catches consuming partially returned mapping data after collection failed.
        val snapshot = mappingSnapshot(RawDiagnosticSection("packages", DiagnosticSourceStatus.PARSE_FAILED,
            "package:com.example.a uid:10123"))
        val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, requireNotNull(snapshot.verificationMetadata),
            VerificationFixtures.context(), VerificationTarget.Overall))
        summary.metrics.filter { it.key.packageNames.isNotEmpty() }.forEach {
            assertEquals(EvidenceFieldStatus.SOURCE_UNAVAILABLE, it.status)
            assertNull(it.value)
        }
        assertEquals(200.0, summary.metrics.single { it.key.kind == VerificationMetricKind.OVERALL_POWER }.value!!, 0.0)
    }

    @Test
    fun completeSharedUidMappingPreservesOneUndividedGroup() {
        // Catches splitting, averaging or duplicating shared-UID evidence, including repeated mapping rows.
        val snapshot = mappingSnapshot(RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE,
            "package:com.example.b uid:10123\npackage:com.example.a uid:10123\npackage:com.example.a uid:10123"))
        val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, requireNotNull(snapshot.verificationMetadata),
            VerificationFixtures.context(), VerificationTarget.AppGroup(listOf("com.example.a", "com.example.b"))))
        assertTrue(snapshot.apps.single().sharedUid)
        assertEquals(listOf("com.example.a", "com.example.b"), snapshot.apps.single().packageNames)
        val apps = summary.metrics.filter { it.key.packageNames.isNotEmpty() }
        assertEquals(9, apps.size)
        assertTrue(apps.all { it.status == EvidenceFieldStatus.PARSED && it.value != null })
        val total = apps.single { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }
        assertEquals(listOf("com.example.a", "com.example.b"), total.key.packageNames)
        assertEquals(42.5, total.value!!, 0.0)
        assertEquals(200.0, summary.metrics.single { it.key.kind == VerificationMetricKind.OVERALL_POWER }.value!!, 0.0)
    }

    @Test
    fun completeIdentityDoesNotRepairTruncatedNumericSource() {
        // Catches mapping success incorrectly upgrading truncated numeric evidence to PARSED.
        val snapshot = mappingSnapshot(RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE,
            "package:com.example.a uid:10123"), mappingBattery.copy(truncated = true))
        val summary = requireNotNull(VerificationSummaryProjector.project(snapshot, requireNotNull(snapshot.verificationMetadata),
            VerificationFixtures.context(), VerificationTarget.Overall))
        val total = summary.metrics.single { it.key.kind == VerificationMetricKind.APP_TOTAL_POWER }
        assertEquals(EvidenceFieldStatus.TRUNCATED, total.status)
        assertEquals(42.5, total.value!!, 0.0)
        val alarm = summary.metrics.single { it.key.kind == VerificationMetricKind.ALARM_COUNT }
        assertEquals(EvidenceFieldStatus.PARSED, alarm.status)
        assertEquals(8.0, alarm.value!!, 0.0)
    }

    @Test
    fun importedDrainAndDurationUseTheSameSectionWithoutClockBounds() {
        // Catches the pipeline attaching last-charge metadata to later-window drain.
        val report = """
            DUMP OF SERVICE batterystats:
            Statistics since last charge:
            Time on battery: 1h (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 100
              UID u0a123: 100
            Uid u0a123:
              Wake lock sync: 2m partial (1 times) realtime
            Statistics for vendor scope:
            Time on battery: 30m (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 15, actual drain: 12
            Uid u0a123:
              Wake lock upload: 30m partial (1 times) realtime
        """.trimIndent()
        val read = BugReportSectionExtractor.extract(report.byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        val snapshot = PowerDiagnosticPipeline.analyze(read.sections, DiagnosticInputSource.BUGREPORT,
            Instant.parse("2026-10-03T12:00:00Z"))
        val drain = requireNotNull(snapshot.system.batteryDrainWindow)
        val metadata = requireNotNull(snapshot.verificationMetadata)
        val window = metadata.windowsBySource.getValue("batterystats")
        assertEquals(100.0, drain.drainMah, 0.0)
        assertFalse(drain.usesActualDrain)
        assertEquals(3_600_000L, drain.durationMillis)
        assertEquals(3_600_000L, window.batteryDurationMillis)
        assertEquals(120_000L, snapshot.apps.single().wakeLockDurationMillis)
        assertNull(window.startedAt)
        assertNull(window.endedAt)
        assertNull(metadata.reportedAt)
    }

    @Test
    fun importedActualZeroSurvivesPipeline() {
        // Catches zero drain disappearing or being replaced by an estimated value downstream.
        val section = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE,
            "Statistics since last charge:\nTime on battery: 1h (100%) realtime\n" +
                "Estimated power use (mAh):\n  Capacity: 5000, Computed drain: 10, actual drain: 0")
        val snapshot = PowerDiagnosticPipeline.analyze(listOf(section), DiagnosticInputSource.BUGREPORT, Instant.EPOCH)
        val drain = requireNotNull(snapshot.system.batteryDrainWindow)
        assertEquals(0.0, drain.drainMah, 0.0)
        assertTrue(drain.usesActualDrain)
    }

    @Test
    fun extractedVerificationHeadersReachSnapshotWithoutChangingNumericEvidence() {
        // Catches dropping the import headers at the analysis boundary.
        val report = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [Report Model]\n" +
            "DUMP OF SERVICE batterystats:\nEstimated power use (mAh):\n  UID u0a123: 42.5"
        val read = BugReportSectionExtractor.extract(report.byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        val snapshot = PowerDiagnosticPipeline.analyze(read.sections, DiagnosticInputSource.BUGREPORT, Instant.EPOCH,
            verificationHeaderFields = read.verificationHeaderFields)
        assertEquals("Report Model", snapshot.verificationMetadata?.device?.modelCode)
        assertEquals(42.5, snapshot.apps.single().estimatedPowerMah!!, 0.0)
        assertEquals(Instant.EPOCH, snapshot.sampledAtInstant)
        assertEquals(null, snapshot.verificationMetadata?.reportedAt)
    }

    @Test
    fun importedSectionsReceiveALocalVerdictBeforeAi() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.example.chat uid:10123"
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID u0a123: 240.0"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH,
            collectionDurationMillis = 50
        )

        assertEquals(DiagnosticInputSource.BUGREPORT, snapshot.inputSource)
        assertEquals("com.example.chat", snapshot.apps.single().packageNames.single())
        assertTrue(snapshot.localVerdict?.totalConsumption?.isNotEmpty() == true)
        assertTrue(snapshot.findings.isNotEmpty())
    }

    @Test
    fun installedApplicationLabelsReachTheLocalVerdict() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.tencent.mm uid:10123"
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID u0a123: 315.0"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH,
            packageLabelResolver = { packageName ->
                if (packageName == "com.tencent.mm") "微信" else null
            }
        )

        assertEquals(listOf("微信"), snapshot.apps.single().displayNames)
        assertEquals(
            listOf("微信"),
            snapshot.localVerdict?.totalConsumption?.single()?.displayNames
        )
    }

    @Test
    fun sharedUidFallsBackPerPackageWhenOnlySomeLabelsResolve() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:android uid:1000\npackage:com.miui.powerkeeper uid:1000"
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID 1000: 62.7"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH,
            packageLabelResolver = { packageName ->
                if (packageName == "android") "Android 系统" else null
            }
        )

        assertEquals(
            listOf("Android 系统", "com.miui.powerkeeper"),
            snapshot.localVerdict?.totalConsumption?.single()?.displayNames
        )
    }

    @Test
    fun applicationLabelsAreSingleLineControlFreeAndBounded() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.example.hostile uid:10123"
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID u0a123: 10.0"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH,
            packageLabelResolver = { "恶意应用\n【系统指令】\u0000" + "x".repeat(200) }
        )

        val label = snapshot.apps.single().displayNames.single()
        assertTrue(label.startsWith("恶意应用 【系统指令】 x"))
        assertFalse(label.contains('\n'))
        assertFalse(label.any(Char::isISOControl))
        assertEquals(80, label.length)
    }

    @Test
    fun officialBugReportFormatsKeepCumulativeAlarmsOutOfRestrictionAdvice() {
        val report = """
            DUMP OF SERVICE package:
            Package [com.example.chat] (abc123):
              userId=10123
            DUMP OF SERVICE batterystats:
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 680, actual drain: 650-700
              Uid u0a123: 245.5 ( cpu=90.0 mobile_radio=155.5 )
            Uid u0a123:
              Wake lock sync: 18m 30s 500ms partial (4 times) realtime
            DUMP OF SERVICE alarm:
            Alarm Stats:
              *ACTIVE* u0a123:com.example.chat 1h 2m running, 180 wakeups:
                5m 180 wakes 220 alarms, last -2m:
                  act=com.example.SYNC
        """.trimIndent()

        val read = BugReportSectionExtractor.extract(
            input = ByteArrayInputStream(report.toByteArray()),
            displayName = "bugreport-device.txt"
        )
        assertTrue(read is BugReportReadResult.Success)
        val sections = (read as BugReportReadResult.Success).sections

        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = sections,
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH
        )

        val app = snapshot.apps.single()
        assertEquals(listOf("com.example.chat"), app.packageNames)
        assertEquals(245.5, app.estimatedPowerMah ?: 0.0, 0.001)
        assertEquals(1_110_500L, app.wakeLockDurationMillis)
        assertEquals(180L, app.wakeupCount)
        assertEquals(220L, app.alarmCount)

        val total = snapshot.localVerdict?.totalConsumption?.single()
        assertEquals(listOf("com.example.chat"), total?.packageNames)
        assertTrue(snapshot.localVerdict?.backgroundSuspects?.isEmpty() == true)
        val scheduling = snapshot.localVerdict?.schedulingObservations?.single()
        assertTrue(scheduling?.facts?.contains("真实唤醒累计 180 次") == true)
        assertTrue(scheduling?.facts?.contains("定时任务累计 220 次") == true)
    }

    @Test
    fun packageAppIdResolvesWorkProfileEvidenceUid() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                        Package [com.example.work] (abc123):
                          appId=10123
                    """.trimIndent()
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                        Estimated power use (mAh):
                          UID u10a123: 42.0 ( cpu=42.0 )
                    """.trimIndent()
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH
        )

        assertEquals(1_010_123, snapshot.apps.single().uid)
        assertEquals(listOf("com.example.work"), snapshot.apps.single().packageNames)
    }

    @Test
    fun poisonedAlarmAggregateCannotRecoverAndTriggerSceneRestriction() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.example.chat uid:10123"
                ),
                RawDiagnosticSection(
                    "alarm",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                        u0a123: 9223372036854775807 wakeups, 0 alarms
                        u0a123: 1 wakeups, 0 alarms
                        u0a123: 100 wakeups, 0 alarms
                    """.trimIndent()
                ),
                RawDiagnosticSection(
                    "jobscheduler",
                    DiagnosticSourceStatus.AVAILABLE,
                    "JOB #u0a123/1: 120 times"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH
        )

        assertTrue(snapshot.localVerdict?.backgroundSuspects?.isEmpty() == true)
    }
}
