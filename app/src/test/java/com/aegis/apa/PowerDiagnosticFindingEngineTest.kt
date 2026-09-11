package com.aegis.apa

import com.aegis.apa.agent.PowerDiagnosticFindingEngine
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.SystemPowerEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PowerDiagnosticFindingEngineTest {
    @Test
    fun corroboratedLongWakeLockProducesRestrictAdvice() {
        val findings = PowerDiagnosticFindingEngine.find(
            snapshotFor(
                AppPowerEvidence(
                    uid = 10123,
                    packageNames = listOf("com.example.chat"),
                    estimatedPowerMah = 240.0,
                    wakeLockDurationMillis = 24 * 60_000L,
                    wakeupCount = 186
                )
            )
        )

        assertEquals(AdviceLevel.RESTRICT, findings.single().adviceLevel)
        assertEquals(DiagnosticConfidence.HIGH, findings.single().confidence)
        assertTrue(findings.single().evidence.size >= 3)
    }

    @Test
    fun protectedPackagesNeverBecomeFreezeCandidates() {
        val findings = PowerDiagnosticFindingEngine.find(
            snapshotFor(
                AppPowerEvidence(
                    uid = 1001,
                    packageNames = listOf("com.android.phone"),
                    estimatedPowerMah = 900.0,
                    wakeLockDurationMillis = 90 * 60_000L,
                    wakeupCount = 500
                )
            )
        )

        assertTrue(findings.none { it.adviceLevel == AdviceLevel.FREEZE_CANDIDATE })
    }

    @Test
    fun singlePowerEstimateOnlyProducesObservation() {
        val findings = PowerDiagnosticFindingEngine.find(
            snapshotFor(AppPowerEvidence(uid = 10123, packageNames = listOf("com.example.video"), estimatedPowerMah = 180.0))
        )

        assertEquals(AdviceLevel.OBSERVE, findings.single().adviceLevel)
        assertEquals(DiagnosticConfidence.LOW, findings.single().confidence)
    }

    private fun snapshotFor(app: AppPowerEvidence) = PowerDiagnosticSnapshot(
        sampledAtInstant = Instant.EPOCH,
        collectionDurationMillis = 100,
        sources = emptyMap(),
        apps = listOf(app),
        system = SystemPowerEvidence(),
        findings = emptyList()
    )
}
