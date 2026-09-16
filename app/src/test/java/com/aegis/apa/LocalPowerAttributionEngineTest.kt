package com.aegis.apa

import com.aegis.apa.agent.LocalPowerAttributionEngine
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.SystemPowerEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocalPowerAttributionEngineTest {
    @Test
    fun mahAloneRanksConsumptionWithoutAccusingBackgroundActivity() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.video"),
                    estimatedPowerMah = 300.0
                )
            )
        )

        assertEquals(listOf("com.example.video"), verdict.totalConsumption.single().packageNames)
        assertTrue(verdict.backgroundSuspects.isEmpty())
        assertEquals(AdviceLevel.OBSERVE, verdict.totalConsumption.single().maxAdviceLevel)
    }

    @Test
    fun twoIndependentBackgroundSignalsProduceRestrictAdvice() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.chat"),
                    wakeLockDurationMillis = 24 * 60_000L,
                    estimatedPowerMah = 300.0,
                    backgroundPowerMah = 240.0
                )
            )
        )

        val suspect = verdict.backgroundSuspects.single()
        assertEquals(AdviceLevel.RESTRICT, suspect.maxAdviceLevel)
        assertEquals(DiagnosticConfidence.MEDIUM, suspect.confidence)
        assertNotNull(suspect.sceneAction)
        assertNotNull(suspect.risk)
        assertNotNull(suspect.rollback)
        assertNotNull(suspect.retest)
    }

    @Test
    fun backgroundPowerRankingSeparatesBackgroundDrainFromForegroundUsage() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10266,
                    packageNames = listOf("com.tencent.mm"),
                    estimatedPowerMah = 1575.0,
                    foregroundPowerMah = 74.8,
                    backgroundPowerMah = 1485.0,
                    foregroundDurationMillis = 1_434_524L,
                    backgroundDurationMillis = 54_917_443L
                ),
                AppPowerEvidence(
                    uid = 10434,
                    packageNames = listOf("com.ss.android.ugc.aweme"),
                    estimatedPowerMah = 1006.0,
                    foregroundPowerMah = 706.0,
                    backgroundPowerMah = 69.5
                )
            )
        )

        val background = verdict.backgroundConsumption
        assertEquals(listOf("com.tencent.mm"), background.first().packageNames)
        assertTrue(background.first().facts.any { it.contains("后台耗电 1485.0 mAh") })
        assertTrue(background.first().facts.any { it.contains("94.3%") })
        assertTrue(verdict.totalConsumption.first().reason.contains("后台耗电占主要部分"))
        assertTrue(verdict.totalConsumption[1].reason.contains("前台使用成本"))
    }

    @Test
    fun cumulativeAlarmCountersAreSchedulingObservationsNotBackgroundSuspects() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 1000,
                    packageNames = listOf("android", "com.miui.powerkeeper"),
                    wakeupCount = 5_234,
                    alarmCount = 28_916,
                    sharedUid = true
                )
            ).copy(inputSource = DiagnosticInputSource.BUGREPORT)
        )

        assertTrue(verdict.backgroundSuspects.isEmpty())
        val observation = verdict.schedulingObservations.single()
        assertTrue(observation.facts.contains("真实唤醒累计 5234 次"))
        assertTrue(observation.facts.contains("定时任务累计 28916 次"))
        assertEquals(AdviceLevel.OBSERVE, observation.maxAdviceLevel)
        assertTrue(observation.sceneAction == null)
        assertTrue(observation.reason.contains("不与本次耗电窗口混合归因"))
    }

    @Test
    fun protectedAndSharedUidAppsStayObservationOnly() {
        val protected = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 1001,
                    packageNames = listOf("com.android.phone"),
                    estimatedPowerMah = 300.0,
                    backgroundPowerMah = 250.0,
                    wakeLockDurationMillis = 60 * 60_000L,
                    alarmCount = 500,
                    jobCount = 500
                )
            )
        ).backgroundSuspects.single()
        val shared = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.one", "com.example.two"),
                    estimatedPowerMah = 300.0,
                    backgroundPowerMah = 250.0,
                    wakeLockDurationMillis = 60 * 60_000L,
                    alarmCount = 500,
                    jobCount = 500,
                    sharedUid = true
                )
            )
        ).backgroundSuspects.single()

        assertEquals(AdviceLevel.OBSERVE, protected.maxAdviceLevel)
        assertEquals(AdviceLevel.OBSERVE, shared.maxAdviceLevel)
        assertTrue(shared.reason.contains("共享 UID"))
    }

    @Test
    fun bareSystemUidWithOneCumulativeCounterIsNotAnAppSuspect() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 1000,
                    packageNames = emptyList(),
                    alarmCount = 28_916
                )
            )
        )

        assertTrue(verdict.backgroundSuspects.isEmpty())
    }

    @Test
    fun sameWindowBackgroundSignalsNeverRecommendFreezeInFirstVersion() {
        val suspect = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.social"),
                    wakeLockDurationMillis = 90 * 60_000L,
                    estimatedPowerMah = 600.0,
                    backgroundPowerMah = 500.0
                )
            )
        ).backgroundSuspects.single()

        assertEquals(AdviceLevel.RESTRICT, suspect.maxAdviceLevel)
        assertEquals(DiagnosticConfidence.MEDIUM, suspect.confidence)
    }

    @Test
    fun foregroundDominatedPowerIsExplainedAsUsageCost() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.game"),
                    estimatedPowerMah = 600.0,
                    foregroundDurationMillis = 3 * 60 * 60_000L,
                    backgroundDurationMillis = 10 * 60_000L
                )
            )
        )

        assertTrue(verdict.totalConsumption.single().reason.contains("前台使用成本"))
        assertTrue(verdict.backgroundSuspects.isEmpty())
    }

    @Test
    fun noParsedAppEvidenceReturnsOneConcreteSamplingStep() {
        val verdict = LocalPowerAttributionEngine.attribute(snapshotFor())

        assertEquals(PowerVerdictType.INSUFFICIENT, verdict.type)
        assertTrue(verdict.totalConsumption.isEmpty())
        assertTrue(verdict.backgroundSuspects.isEmpty())
        assertEquals(
            "完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。",
            verdict.nextStep
        )
    }

    @Test
    fun oneSameWindowSignalDoesNotBecomeAnAnomalyCandidate() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.chat"),
                    wakeLockDurationMillis = 24 * 60_000L
                )
            )
        )

        assertTrue(verdict.backgroundSuspects.isEmpty())
    }

    @Test
    fun importedReportWithInsufficientEvidenceRequestsObservationInsteadOfAnotherImport() {
        val verdict = LocalPowerAttributionEngine.attribute(
            snapshotFor().copy(inputSource = DiagnosticInputSource.BUGREPORT)
        )

        assertTrue(verdict.nextStep.orEmpty().contains("应用内续航观察"))
        assertTrue(!verdict.nextStep.orEmpty().contains("Bug Report"))
    }

    private fun snapshotFor(vararg apps: AppPowerEvidence) = PowerDiagnosticSnapshot(
        sampledAtInstant = Instant.EPOCH,
        collectionDurationMillis = 100,
        sources = emptyMap(),
        apps = apps.toList(),
        system = SystemPowerEvidence(),
        findings = emptyList()
    )
}
