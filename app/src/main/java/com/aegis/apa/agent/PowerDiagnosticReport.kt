package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.userFacingName
import java.util.Locale
import com.aegis.apa.localization.AppLanguage

object PowerDiagnosticReportBuilder {
    const val MAX_REPORT_CHARS = 16 * 1024
    private const val MIN_RUNTIME_PROJECTION_WINDOW_MILLIS = 2 * 60 * 60 * 1_000L
    private const val TRUNCATION_MARKER = "\n[报告已截断]"

    fun build(
        snapshot: PowerDiagnosticSnapshot,
        includeAdvancedActions: Boolean = true,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String {
        if (language == AppLanguage.EN) return buildEnglish(snapshot, includeAdvancedActions)
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

    private fun buildEnglish(snapshot: PowerDiagnosticSnapshot, includeAdvancedActions: Boolean): String {
        val text = buildString {
            appendLine("[System power diagnostic]")
            appendLine("Sample: ${snapshot.sampledAtInstant}")
            appendLine("Collection time: ${snapshot.collectionDurationMillis} ms")
            snapshot.inputSource?.let { appendLine("Source: ${if (it == DiagnosticInputSource.BUGREPORT) "Android Bug Report" else "Root read-only collection"}") }
            snapshot.system.batteryDrainWindow?.let { window ->
                val hours = window.durationMillis / 3_600_000.0
                val mahPerHour = window.drainMah / hours
                val percentPerHour = window.drainMah / window.capacityMah / hours * 100.0
                appendLine("Window: since the last full charge")
                appendLine("Time on battery: ${formatDurationEnglish(window.durationMillis)}")
                appendLine("Window drain: ${formatDecimal(window.drainMah)} mAh / ${formatDecimal(window.capacityMah)} mAh")
                appendLine("Average drain: ${formatDecimal(mahPerHour)} mAh/hour (${formatDecimal(percentPerHour)}%/hour)")
                if (window.durationMillis >= MIN_RUNTIME_PROJECTION_WINDOW_MILLIS) {
                    appendLine("Projected runtime at the same intensity: ${formatDecimal(window.capacityMah / mahPerHour)} hours")
                } else appendLine("Window note: under 2 hours; keep the measured rate only and do not project full runtime.")
            }
            val unavailable = snapshot.sources.values.filter { it.status != DiagnosticSourceStatus.AVAILABLE }
            if (unavailable.isNotEmpty()) appendLine("Incomplete sources: " + unavailable.joinToString("; ") { "${it.source} (${it.status.name})" })
            val verdict = snapshot.localVerdict
            if (verdict == null) {
                if (snapshot.findings.isEmpty()) {
                    appendLine("Evidence is insufficient; no app restriction or freezing advice was generated.")
                } else {
                    snapshot.findings.forEachIndexed { index, finding ->
                        appendLine()
                        appendLine("${index + 1}. ${englishFindingTitle(finding.title)}")
                        if (finding.packageNames.isNotEmpty()) appendLine("Packages: ${finding.packageNames.joinToString()}")
                        appendLine("Evidence: ${finding.evidence.joinToString("; ") { englishFact(it) }}")
                        appendLine("Interpretation: ${englishExplanation(finding.explanation)}")
                        appendLine("Confidence: ${finding.confidence.englishLabel()}")
                        appendLine("Advice level: ${finding.adviceLevel.englishLabel()}")
                        if (includeAdvancedActions) appendLine("Manual Scene action: ${englishAction(finding.sceneAdvice)}")
                        appendLine("Limit: ${englishExplanation(finding.caveat)}")
                    }
                }
            } else {
                appendLine("[Local verdict]")
                if (verdict.type == PowerVerdictType.INSUFFICIENT) {
                    appendLine("Conclusion: app-level evidence is insufficient; do not restrict or freeze any app.")
                    verdict.nextStep?.let { appendLine("Next step: ${englishAction(it)}") }
                }
                appendEnglishCandidates("Total consumption", verdict.totalConsumption, false, includeAdvancedActions)
                appendEnglishCandidates("Background consumption", verdict.backgroundConsumption, false, includeAdvancedActions)
                appendEnglishCandidates("Same-window background anomaly evidence", verdict.backgroundSuspects, true, includeAdvancedActions)
                appendEnglishCandidates("Scheduling observations", verdict.schedulingObservations, false, includeAdvancedActions)
                if (verdict.limits.isNotEmpty()) appendLine("Evidence limits: ${verdict.limits.joinToString("; ") { englishLimit(it) }}")
            }
        }.trimEnd()
        if (text.length <= MAX_REPORT_CHARS) return text
        val marker = "\n[Report truncated]"
        return text.take(MAX_REPORT_CHARS - marker.length) + marker
    }

    private fun StringBuilder.appendEnglishCandidates(
        title: String,
        candidates: List<RankedPowerCandidate>,
        actions: Boolean,
        includeAdvancedActions: Boolean
    ) {
        appendLine("[$title]")
        if (candidates.isEmpty()) {
            appendLine("No comparable data.")
            return
        }
        candidates.forEachIndexed { index, candidate ->
            appendLine("${index + 1}. ${englishCandidateName(candidate)}")
            if (candidate.packageNames.isNotEmpty()) appendLine("Packages: ${candidate.packageNames.joinToString()}")
            appendLine("Facts: ${candidate.facts.joinToString("; ") { englishFact(it) }}")
            appendLine("Interpretation: ${englishExplanation(candidate.reason)}")
            appendLine("Confidence: ${candidate.confidence.englishLabel()}")
            appendLine("Maximum advice: ${candidate.maxAdviceLevel.englishLabel()}")
            if (actions) {
                if (includeAdvancedActions) candidate.sceneAction?.let { appendLine("Manual Scene action: ${englishAction(it)}") }
                candidate.risk?.let { appendLine("Risk: ${englishAction(it)}") }
                candidate.rollback?.let { appendLine("Rollback: ${englishAction(it)}") }
                candidate.retest?.let { appendLine("Retest: ${englishAction(it)}") }
            }
        }
    }

    private fun englishCandidateName(candidate: RankedPowerCandidate): String {
        if (candidate.packageNames.size > 1) {
            val groupLabel = if (
                (candidate.uid ?: Int.MAX_VALUE) < 10_000 ||
                candidate.packageNames.any { it == "android" || it.startsWith("com.android.") || it.startsWith("com.miui.") }
            ) "System component group" else "Shared-UID app group"
            return "$groupLabel (${candidate.displayNames.ifEmpty { candidate.packageNames }.joinToString()})"
        }
        val packageName = candidate.packageNames.firstOrNull()
        val displayName = candidate.displayNames.firstOrNull()?.takeIf { it.isNotBlank() && it != packageName }
        return when {
            packageName != null && displayName != null -> "$displayName ($packageName)"
            packageName != null -> packageName
            else -> candidate.uid?.let { "UID $it" } ?: "Unknown app"
        }
    }

    private fun englishFact(value: String): String {
        Regex("系统估算后台耗电 ([0-9.]+) mAh，占该应用总耗电 ([0-9.]+)%").matchEntire(value)?.let {
            return "Estimated background drain: ${it.groupValues[1]} mAh; ${it.groupValues[2]}% of this app's total drain"
        }
        Regex("系统估算后台耗电 ([0-9.]+) mAh").matchEntire(value)?.let {
            return "Estimated background drain: ${it.groupValues[1]} mAh"
        }
        Regex("系统估算耗电 ([0-9.]+) mAh").matchEntire(value)?.let {
            return "Estimated drain: ${it.groupValues[1]} mAh"
        }
        if (value.startsWith("后台状态累计 ")) {
            return "Background state time: ${durationTextEnglish(value.removePrefix("后台状态累计 "))}"
        }
        Regex("持有唤醒锁约 ([0-9]+) 分钟").matchEntire(value)?.let {
            return "Wake lock held for about ${it.groupValues[1]} min"
        }
        Regex("真实唤醒累计 ([0-9]+) 次").matchEntire(value)?.let {
            return "Wakeups: ${it.groupValues[1]} total"
        }
        Regex("定时任务累计 ([0-9]+) 次").matchEntire(value)?.let {
            return "Alarms: ${it.groupValues[1]} total"
        }
        Regex("后台任务累计 ([0-9]+) 次").matchEntire(value)?.let {
            return "Background jobs: ${it.groupValues[1]} total"
        }
        return if (value.containsChinese()) "Additional diagnostic evidence was recorded." else value
    }

    private fun englishExplanation(value: String): String = when (value) {
        "前台耗电占主要部分，这更像前台使用成本，不能据此判断后台异常。" ->
            "Foreground use accounts for most of the drain; this does not indicate a background anomaly."
        "后台耗电占主要部分，值得优先核对，但单一系统估算不能证明异常。" ->
            "Background drain accounts for most of the total; check it first, but one system estimate does not prove abnormal behavior."
        "这是耗电总量排行，只能说明消耗较多，不能单独证明后台异常。" ->
            "This is a total-drain ranking; higher consumption alone does not prove a background anomaly."
        "后台耗电落在共享 UID，只能作为排行观察，不能安全拆分或限制。" ->
            "The background drain belongs to a shared UID; use it only for ranking because it cannot be safely split or restricted."
        "后台耗电指向系统或关键应用，仅供解释总量，不得限制或冻结。" ->
            "The background drain points to a system or critical app; use it only to explain the total and do not restrict or freeze it."
        "后台耗电排行，值得优先核对；单一系统估算不足以证明异常。" ->
            "Background-drain ranking; check this app first, but one system estimate does not prove abnormal behavior."
        "多项统计落在共享 UID，暂时不能把责任安全拆到其中一个应用。" ->
            "Multiple signals belong to a shared UID, so responsibility cannot be safely assigned to one app."
        "多项统计指向系统或关键应用，但限制它可能破坏电话、通知或系统稳定性。" ->
            "Multiple signals point to a system or critical app; restricting it may affect calls, notifications, or system stability."
        "至少两种独立后台证据同时超过保守阈值，可先做一次可回退的后台限制。" ->
            "At least two independent background signals exceed conservative thresholds; a reversible background restriction may be tested."
        "目前只有一种后台线索，只够继续观察，不能据此限制或冻结。" ->
            "Only one background signal is available; continue observing and do not restrict or freeze the app."
        "该统计为独立累计值，周期可能是开机以来；不与本次耗电窗口混合归因，也不据此限制或冻结。" ->
            "This is an independent cumulative count, possibly since boot; do not mix it with this drain window or use it to restrict or freeze an app."
        "多项系统统计指向该应用可能在后台保持活跃；这是相关性诊断，不等同于已经证明唯一责任。" ->
            "Multiple system statistics suggest background activity; this is correlation, not proof of sole responsibility."
        "检测到系统或关键应用特征，不建议冻结；错误限制可能影响电话、通知、桌面或系统稳定性。" ->
            "This appears to be a system or critical app; freezing it may affect calls, notifications, the launcher, or system stability."
        "建议一次只改一个 Scene 策略，并至少观察一个完整充放电周期，便于回退和判断效果。" ->
            "Change only one Scene policy at a time and observe at least one full charge cycle so the effect can be evaluated and reverted."
        "可能增加熄屏待机耗电。" -> "It may increase screen-off standby drain."
        "完全冻结可能导致消息延迟。" -> "Freezing the app completely may delay messages."
        else -> if (value.containsChinese()) {
            "This item is included for observation; the available evidence does not prove abnormal behavior."
        } else value
    }

    private fun englishAction(value: String): String = when (value) {
        "在 Scene 中只限制该应用的后台活动，先保留通知与前台性能。" ->
            "Restrict only this app's background activity in Scene; keep notifications and foreground performance enabled."
        "可能造成后台同步或消息提醒延迟。" ->
            "Background sync or message notifications may be delayed."
        "出现功能或通知异常时，把该应用的 Scene 后台策略恢复为默认。" ->
            "If features or notifications break, restore this app's Scene background policy to default."
        "一次只改这个策略，正常使用一个完整观察时段后重新生成报告对比。" ->
            "Change only this policy, use the phone normally for one full observation window, then generate another report for comparison."
        "在 APA 内完成一次未充电的应用内续航观察，用起止电量和时长判断平均掉电速度。" ->
            "Run one unplugged battery observation in APA and use the start level, end level, and duration to measure average drain."
        "完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。" ->
            "After a normal usage period, generate another system Bug Report or use Root read-only collection."
        "先在 Scene 限制后台活动并保留通知。" ->
            "Restrict background activity in Scene while keeping notifications enabled."
        else -> if (value.containsChinese()) "Use one reversible change at a time and retest." else value
    }

    private fun englishFindingTitle(value: String): String = when {
        value.endsWith("存在耗电线索") -> "${value.removeSuffix("存在耗电线索").trim()} has power-use signals"
        value.containsChinese() -> "Power-use signal"
        else -> value
    }

    private fun englishLimit(value: String): String {
        Regex("([A-Z_]+) 未解析").matchEntire(value)?.let { return "${it.groupValues[1]} was not parsed" }
        val parts = value.split(":", limit = 2).map(String::trim)
        if (parts.size == 2) {
            val status = when (parts[1]) {
                "NOT_PRESENT" -> "not present"
                "NOT_PARSED" -> "not parsed"
                "SOURCE_UNAVAILABLE" -> "source unavailable"
                "TRUNCATED" -> "source output truncated"
                else -> parts[1].lowercase().replace('_', ' ')
            }
            return "${parts[0]}: $status"
        }
        return if (value.containsChinese()) "Some diagnostic evidence was unavailable" else value
    }

    private fun durationTextEnglish(value: String): String = value
        .replace(Regex("([0-9]+)\\s*小时"), "$1 h ")
        .replace(Regex("([0-9]+)\\s*分(?:钟)?"), "$1 min ")
        .replace(Regex("([0-9]+)\\s*秒"), "$1 sec")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.containsChinese(): Boolean = any { it in '\u4E00'..'\u9FFF' }

    private fun DiagnosticConfidence.englishLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

    private fun AdviceLevel.englishLabel(): String = name.lowercase().replace('_', ' ')

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
        val name = candidate.userFacingName()
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

    private fun formatDurationEnglish(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1_000
        return "${totalSeconds / 3_600}h ${totalSeconds % 3_600 / 60}m ${totalSeconds % 60}s"
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
}
