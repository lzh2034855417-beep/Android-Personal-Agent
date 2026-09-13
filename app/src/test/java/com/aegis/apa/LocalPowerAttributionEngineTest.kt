package com.aegis.apa

import com.aegis.apa.agent.LocalPowerAttributionEngine
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
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
                    alarmCount = 180
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
    fun protectedAndSharedUidAppsStayObservationOnly() {
        val protected = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 1001,
                    packageNames = listOf("com.android.phone"),
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
    fun evenThreeStrongSignalsNeverRecommendFreezeInFirstVersion() {
        val suspect = LocalPowerAttributionEngine.attribute(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.social"),
                    wakeLockDurationMillis = 90 * 60_000L,
                    wakeupCount = 800,
                    jobCount = 500
                )
            )
        ).backgroundSuspects.single()

        assertEquals(AdviceLevel.RESTRICT, suspect.maxAdviceLevel)
        assertEquals(DiagnosticConfidence.HIGH, suspect.confidence)
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

    private fun snapshotFor(vararg apps: AppPowerEvidence) = PowerDiagnosticSnapshot(
        sampledAtInstant = Instant.EPOCH,
        collectionDurationMillis = 100,
        sources = emptyMap(),
        apps = apps.toList(),
        system = SystemPowerEvidence(),
        findings = emptyList()
    )
}
