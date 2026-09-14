package com.aegis.apa

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.tool.PowerDiagnosticPipeline
import com.aegis.apa.tool.RawDiagnosticSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class PowerDiagnosticPipelineTest {
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
    fun officialBugReportFormatsResolveAppAndProduceConservativeSceneAdvice() {
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
        val suspect = snapshot.localVerdict?.backgroundSuspects?.single()
        assertEquals(AdviceLevel.RESTRICT, suspect?.maxAdviceLevel)
        assertTrue(suspect?.sceneAction?.contains("Scene") == true)
        assertTrue(suspect?.rollback?.isNotBlank() == true)
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
