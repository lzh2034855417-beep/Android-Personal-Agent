package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.PowerDiagnosticSnapshot

object PowerDiagnosticReportBuilder {
    const val MAX_REPORT_CHARS = 16 * 1024
    private const val TRUNCATION_MARKER = "\n[报告已截断]"

    fun build(snapshot: PowerDiagnosticSnapshot): String {
        val text = buildString {
            appendLine("【系统耗电诊断】")
            appendLine("采样时间：${snapshot.sampledAtInstant}")
            appendLine("采集耗时：${snapshot.collectionDurationMillis} ms")

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

            if (snapshot.findings.isEmpty()) {
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
                    appendLine("Scene 建议：${finding.sceneAdvice}")
                    appendLine("限制：${finding.caveat}")
                }
            }
        }.trimEnd()

        if (text.length <= MAX_REPORT_CHARS) return text
        return text.take(MAX_REPORT_CHARS - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
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
}
