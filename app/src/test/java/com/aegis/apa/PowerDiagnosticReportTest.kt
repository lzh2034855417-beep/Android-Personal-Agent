package com.aegis.apa

import com.aegis.apa.agent.PowerDiagnosticReportBuilder
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceResult
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerFinding
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.SystemPowerEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PowerDiagnosticReportTest {
    @Test
    fun reportSeparatesFactsFromAdviceAndNamesMissingSources() {
        val report = PowerDiagnosticReportBuilder.build(sampleSnapshot())

        assertTrue(report.contains("【系统耗电诊断】"))
        assertTrue(report.contains("证据：唤醒锁累计 24 分钟"))
        assertTrue(report.contains("置信度：中"))
        assertTrue(report.contains("建议级别：限制"))
        assertTrue(report.contains("未获取：thermalservice（不支持）"))
    }

    @Test
    fun truncatedSourceWithCapturedPrefixIsReportedAsPartialInsteadOfMissing() {
        val base = sampleSnapshot()
        val report = PowerDiagnosticReportBuilder.build(
            base.copy(
                sources = base.sources + (
                    "batterystats" to DiagnosticSourceResult(
                        "batterystats",
                        DiagnosticSourceStatus.TRUNCATED,
                        "输出过长，已截断"
                    )
                )
            )
        )

        assertTrue(report.contains("部分数据：batterystats（输出截断，已解析已获取的关键字段）"))
        assertFalse(report.contains("未获取：batterystats"))
        assertTrue(report.contains("证据：唤醒锁累计 24 分钟"))
    }

    @Test
    fun reportIsBoundedAndDoesNotContainRawOutput() {
        val report = PowerDiagnosticReportBuilder.build(
            sampleSnapshot(title = "x".repeat(50_000))
        )

        assertTrue(report.length <= PowerDiagnosticReportBuilder.MAX_REPORT_CHARS)
        assertTrue(report.endsWith("[报告已截断]"))
        assertFalse(report.contains("RAW_COMMAND_OUTPUT"))
    }

    @Test
    fun localVerdictReportSeparatesRankingFromBackgroundAdvice() {
        val candidate = RankedPowerCandidate(
            uid = 10123,
            packageNames = listOf("com.example.chat"),
            facts = listOf("持有唤醒锁约 24 分钟", "唤醒/闹钟计数最高 180 次"),
            confidence = DiagnosticConfidence.MEDIUM,
            maxAdviceLevel = AdviceLevel.RESTRICT,
            reason = "两种独立后台证据同时超过保守阈值。",
            sceneAction = "在 Scene 中只限制后台活动。",
            risk = "消息可能延迟。",
            rollback = "恢复默认后台策略。",
            retest = "正常使用一个观察时段后重新生成报告。"
        )
        val report = PowerDiagnosticReportBuilder.build(
            PowerDiagnosticSnapshot(
                sampledAtInstant = Instant.EPOCH,
                collectionDurationMillis = 50,
                sources = mapOf(
                    "alarm" to DiagnosticSourceResult(
                        "alarm",
                        DiagnosticSourceStatus.AVAILABLE,
                        "RAW_BUGREPORT_SECRET 13800138000"
                    )
                ),
                apps = emptyList(),
                system = SystemPowerEvidence(),
                findings = emptyList(),
                inputSource = DiagnosticInputSource.BUGREPORT,
                localVerdict = LocalPowerVerdict(
                    type = PowerVerdictType.SUFFICIENT,
                    totalConsumption = listOf(
                        candidate.copy(
                            facts = listOf("系统估算耗电 240.0 mAh"),
                            maxAdviceLevel = AdviceLevel.OBSERVE,
                            sceneAction = null,
                            risk = null,
                            rollback = null,
                            retest = null
                        )
                    ),
                    backgroundSuspects = listOf(candidate),
                    nextStep = null,
                    limits = listOf("FOREGROUND_TIME: NOT_PARSED")
                )
            )
        )

        assertTrue(report.contains("来源：系统 Bug Report"))
        assertTrue(report.contains("【耗电总量排行】"))
        assertTrue(report.contains("【后台异常嫌疑】"))
        assertTrue(report.contains("最高建议：限制"))
        assertTrue(report.contains("Scene 手动操作：在 Scene 中只限制后台活动。"))
        assertTrue(report.contains("风险：消息可能延迟。"))
        assertTrue(report.contains("回退：恢复默认后台策略。"))
        assertTrue(report.contains("复测：正常使用一个观察时段后重新生成报告。"))
        assertFalse(report.contains("RAW_BUGREPORT_SECRET"))
        assertFalse(report.contains("13800138000"))
    }

    private fun sampleSnapshot(title: String = "微信可能频繁唤醒系统") = PowerDiagnosticSnapshot(
        sampledAtInstant = Instant.parse("2026-09-11T00:00:00Z"),
        collectionDurationMillis = 1_500,
        sources = mapOf(
            "batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.AVAILABLE),
            "thermalservice" to DiagnosticSourceResult("thermalservice", DiagnosticSourceStatus.UNSUPPORTED)
        ),
        apps = emptyList(),
        system = SystemPowerEvidence(),
        findings = listOf(
            PowerFinding(
                id = "wake-lock-com.tencent.mm",
                title = title,
                packageNames = listOf("com.tencent.mm"),
                evidence = listOf("唤醒锁累计 24 分钟"),
                explanation = "可能增加熄屏待机耗电。",
                confidence = DiagnosticConfidence.MEDIUM,
                adviceLevel = AdviceLevel.RESTRICT,
                sceneAdvice = "先在 Scene 限制后台活动并保留通知。",
                caveat = "完全冻结可能导致消息延迟。"
            )
        )
    )
}
