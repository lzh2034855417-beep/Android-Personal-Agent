package com.aegis.apa

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.SystemPowerEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PowerDiagnosticDisplayModelTest {
    @Test
    fun keepsConsumptionAndBackgroundSectionsSeparate() {
        val total = candidate("com.example.video", AdviceLevel.OBSERVE)
        val background = candidate("com.example.chat", AdviceLevel.RESTRICT)
        val snapshot = PowerDiagnosticSnapshot(
            sampledAtInstant = Instant.EPOCH,
            collectionDurationMillis = 10,
            sources = emptyMap(),
            apps = emptyList(),
            system = SystemPowerEvidence(),
            findings = emptyList(),
            inputSource = DiagnosticInputSource.BUGREPORT,
            localVerdict = LocalPowerVerdict(
                PowerVerdictType.SUFFICIENT,
                listOf(total),
                listOf(background),
                null,
                listOf("FOREGROUND_TIME: NOT_PARSED")
            )
        )

        val display = PowerDiagnosticDisplayModel.from(snapshot)

        assertEquals("系统 Bug Report", display.sourceLabel)
        assertEquals("com.example.video", display.totalConsumption.single().packageNames.single())
        assertEquals("com.example.chat", display.backgroundSuspects.single().packageNames.single())
        assertNull(display.nextStep)
    }

    private fun candidate(packageName: String, level: AdviceLevel) = RankedPowerCandidate(
        uid = 10123,
        packageNames = listOf(packageName),
        facts = listOf("事实"),
        confidence = DiagnosticConfidence.MEDIUM,
        maxAdviceLevel = level,
        reason = "解释",
        sceneAction = "Scene 操作",
        risk = "风险",
        rollback = "回退",
        retest = "复测"
    )
}
