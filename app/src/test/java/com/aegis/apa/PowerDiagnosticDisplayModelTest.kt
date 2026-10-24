package com.aegis.apa

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceResult
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.SystemPowerEvidence
import com.aegis.apa.model.EvidenceCoverage
import com.aegis.apa.model.EvidenceField
import com.aegis.apa.model.EvidenceFieldStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PowerDiagnosticDisplayModelTest {
    @Test
    fun composedClueCaveatsAreLocalizedWithoutLosingScopeOrSharedUidSafety() {
        val verdict = com.aegis.apa.agent.LocalPowerAttributionEngine.attribute(
            PowerDiagnosticSnapshot(
                sampledAtInstant = java.time.Instant.EPOCH, collectionDurationMillis = 0,
                sources = emptyMap(), findings = emptyList(), system = SystemPowerEvidence(),
                apps = listOf(com.aegis.apa.model.AppPowerEvidence(
                    uid = 1000, packageNames = listOf("android"),
                    wakeLockDurationMillis = 24 * 60_000L, wakeupCount = 500, sharedUid = true
                ))
            )
        )
        val reason = verdict.schedulingObservations.single().reason
        val english = diagnosticDisplayText(reason, AppLanguage.EN)
        assertTrue(english.contains("unverified"))
        assertTrue(english.contains("since boot"))
        assertTrue(english.contains("shared"))
        assertTrue(english.none { it in '\u4e00'..'\u9fff' })
        assertEquals(reason, diagnosticDisplayText(reason, AppLanguage.ZH_CN))
    }

    @Test
    fun englishRetainsUnknownEvidenceAndAdviceVerbatim() {
        val candidate = candidate("com.example.one", AdviceLevel.OBSERVE)
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot().copy(
            localVerdict = LocalPowerVerdict(PowerVerdictType.INSUFFICIENT, listOf(candidate), emptyList(),
                "设备独有的下一步", listOf("设备独有的证据限制"))
        ), AppLanguage.EN)

        assertEquals("设备独有的下一步", display.nextStepDisplay)
        assertTrue(display.limits.contains("设备独有的证据限制"))
        assertEquals("设备独有的事实", diagnosticDisplayText("设备独有的事实", AppLanguage.EN))
        assertEquals("Estimated drain: 123.4 mAh", diagnosticDisplayText("系统估算耗电 123.4 mAh", AppLanguage.EN))
        // A prefix that resembles a known template must not discard additional qualifiers.
        assertEquals("系统估算耗电 123.4 mAh（窗口未知）", diagnosticDisplayText("系统估算耗电 123.4 mAh（窗口未知）", AppLanguage.EN))
    }

    @Test
    fun independentSchedulingDataNeverBecomesBackgroundAnomaly() {
        val scheduling = candidate("com.example.timer", AdviceLevel.OBSERVE)
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot().copy(
            localVerdict = LocalPowerVerdict(PowerVerdictType.SUFFICIENT, emptyList(), emptyList(), null,
                emptyList(), schedulingObservations = listOf(scheduling))
        ), AppLanguage.EN)

        assertEquals(listOf(scheduling), display.schedulingObservations)
        assertTrue(display.backgroundSuspects.isEmpty())
        assertTrue(display.conclusion.contains("No app reached"))
        assertTrue(display.nextStepDisplay.contains("manually"))
    }

    @Test
    fun sourceDetailAndDirectCoverageRemainVisibleWithoutVerdict() {
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot().copy(
            sources = mapOf("jobs" to DiagnosticSourceResult("jobs", DiagnosticSourceStatus.TRUNCATED, "only first 20 rows")),
            evidenceCoverage = EvidenceCoverage(mapOf(EvidenceField.JOBS to EvidenceFieldStatus.TRUNCATED))
        ), AppLanguage.EN)

        assertTrue(display.limits.any { it.contains("only first 20 rows") && it.contains("Output truncated") })
        assertTrue(display.limits.any { it.contains("Background jobs") && it.contains("Output truncated") })
        assertTrue(display.sourceResults.single().contains("only first 20 rows"))
        assertTrue(display.conclusion.contains("insufficient"))
    }

    @Test
    fun missingVerdictRequestsAnotherSampleWithoutInventingAttribution() {
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot())

        assertTrue(display.totalConsumption.isEmpty())
        assertTrue(display.backgroundSuspects.isEmpty())
        assertTrue(display.nextStep?.contains("Bug Report") == true)
        assertTrue(display.conclusion.contains("证据不足"))
    }

    @Test
    fun sourceFailuresRemainVisibleWithoutRawStatusEnums() {
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot().copy(
            sources = mapOf("battery" to DiagnosticSourceResult("battery", DiagnosticSourceStatus.TIMED_OUT))
        ))

        assertTrue(display.limits.any { it.contains("battery") && it.contains("超时") })
        assertTrue(display.limits.none { it.contains("TIMED_OUT") })
    }

    @Test
    fun coverageLimitsExplainMissingEvidenceWithoutRawFieldStatuses() {
        val display = PowerDiagnosticDisplayModel.from(emptySnapshot().copy(
            localVerdict = LocalPowerVerdict(PowerVerdictType.INSUFFICIENT, emptyList(), emptyList(),
                "保留原始下一步", listOf("FOREGROUND_TIME: NOT_PARSED", "设备自带的未知限制"))
        ))

        assertTrue(display.limits.any { it.contains("前台时长") && it.contains("未解析") })
        assertTrue(display.limits.none { it.contains("NOT_PARSED") })
        assertTrue(display.limits.contains("设备自带的未知限制"))
        assertEquals("保留原始下一步", display.nextStep)
    }

    private fun emptySnapshot() = PowerDiagnosticSnapshot(
        sampledAtInstant = Instant.EPOCH,
        collectionDurationMillis = 10,
        sources = emptyMap(), apps = emptyList(), system = SystemPowerEvidence(), findings = emptyList()
    )

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
