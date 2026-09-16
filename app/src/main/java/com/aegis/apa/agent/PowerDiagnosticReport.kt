package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import java.util.Locale

object PowerDiagnosticReportBuilder {
    const val MAX_REPORT_CHARS = 16 * 1024
    private const val MIN_RUNTIME_PROJECTION_WINDOW_MILLIS = 2 * 60 * 60 * 1_000L
    private const val TRUNCATION_MARKER = "\n[报告已截断]"

    fun build(
        snapshot: PowerDiagnosticSnapshot,
        includeAdvancedActions: Boolean = true
    ): String {
        val text = buildString {
            appendLine("【系统耗电诊断】")
            appendLine("采样时间：${snapshot.sampledAtInstant}")
            appendLine("采集耗时：${snapshot.collectionDurationMillis} ms")
            snapshot.inputSource?.let { source ->
                appendLine("来源：${source.chineseLabel()}")
            }
            snapshot.system.batteryDrainWindow?.let { window ->
                val hours = window.durationMillis / 3_600_000.0
                val mahPerHour = window.drainMah / hours
                val percentPerHour = window.drainMah / window.capacityMah / hours * 100.0
                val projectedHours = window.capacityMah / mahPerHour
                appendLine("统计周期：自上次充满后")
                appendLine("在电池上运行：${formatDuration(window.durationMillis)}")
                appendLine("周期耗电：${formatDecimal(window.drainMah)} mAh / ${formatDecimal(window.capacityMah)} mAh")
                appendLine("平均耗电：${formatDecimal(mahPerHour)} mAh/小时（${formatDecimal(percentPerHour)}%/小时）")
                if (window.durationMillis >= MIN_RUNTIME_PROJECTION_WINDOW_MILLIS) {
                    appendLine("同等使用强度预计续航：${formatDecimal(projectedHours)}小时")
                } else {
                    appendLine("窗口提示：统计窗口不足 2 小时，仅保留实测速率，不外推完整续航。")
                }
            }

            val partial = snapshot.sources.values
                .filter { it.status == DiagnosticSourceStatus.TRUNCATED }
                .sortedBy { it.source }
            if (partial.isNotEmpty()) {
                appendLine("部分数据：" + partial.joinToString("；") {
                    "${it.source}（输出截断，已解析已获取的关键字段）"
                })
            }

            val unavailable = snapshot.sources.values
                .filter { it.status != DiagnosticSourceStatus.AVAILABLE && it.status != DiagnosticSourceStatus.TRUNCATED }
                .sortedBy { it.source }
            if (unavailable.isNotEmpty()) {
                appendLine("未获取：" + unavailable.joinToString("；") {
                    "${it.source}（${it.status.chineseLabel()}）"
                })
            }

            if (snapshot.localVerdict != null) {
                appendLocalVerdict(snapshot.localVerdict, includeAdvancedActions)
            } else if (snapshot.findings.isEmpty()) {
                appendLine("当前证据不足，未生成应用限制或冻结建议。")
            } else {
                snapshot.findings.forEachIndexed { index, finding ->
                    appendLine()
                    appendLine("${index + 1}. ${finding.title}")
                    if (finding.packageNames.isNotEmpty()) {
                        appendLine("包名：${finding.packageNames.joinToString()}")
                    }
                    appendLine("证据：${finding.evidence.joinToString("；")}")
                    appendLine("解释：${finding.explanation}")
                    appendLine("置信度：${finding.confidence.chineseLabel()}")
                    appendLine("建议级别：${finding.adviceLevel.chineseLabel()}")
                    if (includeAdvancedActions) {
                        appendLine("Scene 建议：${finding.sceneAdvice}")
                    }
                    appendLine("限制：${finding.caveat}")
                }
            }
        }.trimEnd()

        if (text.length <= MAX_REPORT_CHARS) return text
        return text.take(MAX_REPORT_CHARS - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
    }

    private fun StringBuilder.appendLocalVerdict(
        verdict: LocalPowerVerdict,
        includeAdvancedActions: Boolean
    ) {
        appendLine()
        appendLine("【本地裁决】")
        if (verdict.type == PowerVerdictType.INSUFFICIENT) {
            appendLine("结论：当前应用级证据不足，不建议限制或冻结任何应用。")
            verdict.nextStep?.let { appendLine("下一步：$it") }
        }

        appendLine("【耗电总量排行】")
        if (verdict.totalConsumption.isEmpty()) {
            appendLine("没有可比较的应用耗电量数据。")
        } else {
            verdict.totalConsumption.forEachIndexed { index, candidate ->
                appendCandidate(index, candidate, includeActions = false)
            }
        }

        appendLine("【后台耗电排行】")
        if (verdict.backgroundConsumption.isEmpty()) {
            appendLine("没有解析到可比较的应用后台耗电量。")
        } else {
            verdict.backgroundConsumption.forEachIndexed { index, candidate ->
                appendCandidate(index, candidate, includeActions = false)
            }
        }

        appendLine("【同窗口后台异常证据】")
        if (verdict.backgroundSuspects.isEmpty()) {
            appendLine("没有达到后台异常建议阈值的应用；高耗电或后台耗电较高不等于异常。")
        } else {
            verdict.backgroundSuspects.forEachIndexed { index, candidate ->
                appendCandidate(index, candidate, includeActions = true, includeAdvancedActions)
            }
        }

        appendLine("【系统调度观察】")
        if (verdict.schedulingObservations.isEmpty()) {
            appendLine("没有解析到应用调度累计数据。")
        } else {
            verdict.schedulingObservations.forEachIndexed { index, candidate ->
                appendCandidate(index, candidate, includeActions = false)
            }
        }
        if (verdict.limits.isNotEmpty()) {
            appendLine("证据覆盖限制：${verdict.limits.joinToString("；")}")
        }
    }

    private fun StringBuilder.appendCandidate(
        index: Int,
        candidate: RankedPowerCandidate,
        includeActions: Boolean,
        includeAdvancedActions: Boolean = true
    ) {
        val name = candidate.packageNames.firstOrNull() ?: candidate.uid?.let { "UID $it" } ?: "未知应用"
        appendLine("${index + 1}. $name")
        if (candidate.packageNames.isNotEmpty()) appendLine("包名：${candidate.packageNames.joinToString()}")
        appendLine("事实：${candidate.facts.joinToString("；")}")
        appendLine("解释：${candidate.reason}")
        appendLine("置信度：${candidate.confidence.chineseLabel()}")
        appendLine("最高建议：${candidate.maxAdviceLevel.chineseLabel()}")
        if (includeActions) {
            if (includeAdvancedActions) {
                candidate.sceneAction?.let { appendLine("Scene 手动操作：$it") }
            }
            candidate.risk?.let { appendLine("风险：$it") }
            candidate.rollback?.let { appendLine("回退：$it") }
            candidate.retest?.let { appendLine("复测：$it") }
        }
    }

    private fun DiagnosticInputSource.chineseLabel(): String = when (this) {
        DiagnosticInputSource.BUGREPORT -> "系统 Bug Report"
        DiagnosticInputSource.ROOT -> "Root 只读采集"
    }

    private fun DiagnosticSourceStatus.chineseLabel(): String = when (this) {
        DiagnosticSourceStatus.AVAILABLE -> "可用"
        DiagnosticSourceStatus.UNSUPPORTED -> "不支持"
        DiagnosticSourceStatus.PERMISSION_DENIED -> "权限不足"
        DiagnosticSourceStatus.TIMED_OUT -> "超时"
        DiagnosticSourceStatus.TRUNCATED -> "已截断"
        DiagnosticSourceStatus.PARSE_FAILED -> "无法解析"
    }

    private fun DiagnosticConfidence.chineseLabel(): String = when (this) {
        DiagnosticConfidence.LOW -> "低"
        DiagnosticConfidence.MEDIUM -> "中"
        DiagnosticConfidence.HIGH -> "高"
    }

    private fun AdviceLevel.chineseLabel(): String = when (this) {
        AdviceLevel.OBSERVE -> "观察"
        AdviceLevel.RESTRICT -> "限制"
        AdviceLevel.FREEZE_CANDIDATE -> "冻结候选"
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return "${hours}小时${minutes}分钟${seconds}秒"
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
}
