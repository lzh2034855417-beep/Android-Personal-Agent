package com.aegis.apa

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*

data class PowerDiagnosticDisplayModel(
    val sourceLabel: String,
    val totalConsumption: List<RankedPowerCandidate>,
    val backgroundSuspects: List<RankedPowerCandidate>,
    val nextStep: String?,
    val limits: List<String>,
    val conclusion: String,
    val sampleTime: String,
    val collectionDurationMillis: Long,
    val sourceResults: List<String>,
    val backgroundConsumption: List<RankedPowerCandidate>,
    val schedulingObservations: List<RankedPowerCandidate>,
    val nextStepDisplay: String
) {
    companion object {
        fun from(snapshot: PowerDiagnosticSnapshot, language: AppLanguage = AppLanguage.ZH_CN): PowerDiagnosticDisplayModel {
            val verdict = snapshot.localVerdict
            val english = language == AppLanguage.EN
            fun text(zh: String, en: String) = if (english) en else zh
            val nextStep = verdict?.nextStep ?: if (verdict == null || verdict.type == PowerVerdictType.INSUFFICIENT) {
                text("完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。",
                    "After a normal usage period, generate another system Bug Report or use Root read-only collection.")
            } else null
            val sourceResults = snapshot.sources.values.map {
                "${it.source}：${it.status.displayLabel(language)}" +
                    (it.detail?.takeIf(String::isNotBlank)?.let { detail -> " · $detail" } ?: "")
            }
            val limits = buildList {
                snapshot.sources.values.filter { it.status != DiagnosticSourceStatus.AVAILABLE }.forEach {
                    add("${it.source}：${it.status.displayLabel(language)}" +
                        (it.detail?.takeIf(String::isNotBlank)?.let { detail -> " · $detail" } ?: ""))
                }
                snapshot.evidenceCoverage.fields.filterValues { it != EvidenceFieldStatus.PARSED }.forEach { (field, status) ->
                    add(coverageLabel(field, status, language))
                }
                verdict?.limits.orEmpty().forEach { add(displayLimit(it, language)) }
            }.distinct()
            return PowerDiagnosticDisplayModel(
                sourceLabel = when (snapshot.inputSource) {
                    DiagnosticInputSource.BUGREPORT -> text("系统 Bug Report", "System Bug Report")
                    DiagnosticInputSource.ROOT -> text("Root 只读采集", "Root read-only collection")
                    null -> text("未知来源", "Unknown source")
                },
                totalConsumption = verdict?.totalConsumption.orEmpty(),
                backgroundSuspects = verdict?.backgroundSuspects.orEmpty(),
                nextStep = nextStep?.let { diagnosticDisplayText(it, language) },
                limits = limits,
                conclusion = when {
                    verdict == null || verdict.type == PowerVerdictType.INSUFFICIENT -> text(
                        "当前应用级证据不足，不建议限制或冻结任何应用。",
                        "App-level evidence is insufficient. Do not restrict or freeze any app on this evidence.")
                    verdict.backgroundSuspects.isNotEmpty() -> text(
                        "${verdict.backgroundSuspects.size} 个候选存在同窗口后台线索；相关性不等于已证明责任。请结合各项置信度与限制。",
                        "${verdict.backgroundSuspects.size} candidates have background signals in the same window. Correlation does not prove responsibility; review each confidence level and limitation.")
                    else -> text("已解析到耗电或调度统计；没有达到后台异常阈值的应用。耗电多不等于后台异常。",
                        "Consumption or scheduling statistics are available. No app reached the background-anomaly threshold; high consumption does not prove an anomaly.")
                },
                sampleTime = SampleTime.format(snapshot.sampledAtInstant),
                collectionDurationMillis = snapshot.collectionDurationMillis,
                sourceResults = sourceResults,
                backgroundConsumption = verdict?.backgroundConsumption.orEmpty(),
                schedulingObservations = verdict?.schedulingObservations.orEmpty(),
                nextStepDisplay = nextStep?.let { diagnosticDisplayText(it, language) } ?: text(
                    "先核对下方证据与风险。仅按候选的最高建议手动操作，一次只改一项，并保留回退与复测。",
                    "Review the evidence and risks below. Follow each candidate's maximum advice manually, change one policy at a time, and keep rollback and retest steps.")
            )
        }
    }
}

internal fun DiagnosticSourceStatus.displayLabel(language: AppLanguage): String = when (this) {
    DiagnosticSourceStatus.AVAILABLE -> if (language == AppLanguage.EN) "Available" else "可用"
    DiagnosticSourceStatus.UNSUPPORTED -> if (language == AppLanguage.EN) "Unsupported" else "不支持"
    DiagnosticSourceStatus.PERMISSION_DENIED -> if (language == AppLanguage.EN) "Permission denied" else "权限不足"
    DiagnosticSourceStatus.TIMED_OUT -> if (language == AppLanguage.EN) "Timed out" else "超时"
    DiagnosticSourceStatus.TRUNCATED -> if (language == AppLanguage.EN) "Output truncated" else "输出已截断"
    DiagnosticSourceStatus.PARSE_FAILED -> if (language == AppLanguage.EN) "Could not parse" else "无法解析"
}

private fun coverageLabel(field: EvidenceField, status: EvidenceFieldStatus, language: AppLanguage): String {
    val english = language == AppLanguage.EN
    val name = when (field) {
        EvidenceField.UID_PACKAGES -> if (english) "UID/package mapping" else "UID 与包名映射"
        EvidenceField.POWER_MAH -> if (english) "Estimated consumption" else "估算耗电"
        EvidenceField.FOREGROUND_TIME -> if (english) "Foreground time" else "前台时长"
        EvidenceField.WAKELOCK_TIME -> if (english) "Wake lock time" else "唤醒锁时长"
        EvidenceField.WAKEUP_ALARMS -> if (english) "Wakeups/alarms" else "唤醒与闹钟"
        EvidenceField.JOBS -> if (english) "Background jobs" else "后台任务"
    }
    val state = when (status) {
        EvidenceFieldStatus.PARSED -> if (english) "Parsed" else "已解析"
        EvidenceFieldStatus.NOT_PRESENT -> if (english) "Not present" else "报告未包含"
        EvidenceFieldStatus.NOT_PARSED -> if (english) "Not parsed" else "未解析"
        EvidenceFieldStatus.SOURCE_UNAVAILABLE -> if (english) "Source unavailable" else "来源不可用"
        EvidenceFieldStatus.TRUNCATED -> if (english) "Output truncated" else "输出已截断"
    }
    return "$name：$state"
}

private fun displayLimit(value: String, language: AppLanguage): String {
    val parts = value.split(':', limit = 2).map(String::trim)
    val field = EvidenceField.entries.firstOrNull { it.name == parts.firstOrNull() }
    val status = EvidenceFieldStatus.entries.firstOrNull { it.name == parts.getOrNull(1) }
    return if (field != null && status != null) coverageLabel(field, status, language) else diagnosticDisplayText(value, language)
}

/** Only exact engine messages/templates are translated. Unrecognized evidence remains verbatim. */
internal fun diagnosticDisplayText(value: String, language: AppLanguage): String {
    if (language != AppLanguage.EN) return value
    diagnosticEnglishText[value]?.let { return it }
    val templates = listOf(
        Regex("系统估算耗电 ([0-9.]+) mAh") to { m: MatchResult -> "Estimated drain: ${m.groupValues[1]} mAh" },
        Regex("系统估算后台耗电 ([0-9.]+) mAh，占该应用总耗电 ([0-9.]+)%") to { m: MatchResult -> "Estimated background drain: ${m.groupValues[1]} mAh; ${m.groupValues[2]}% of this app's total" },
        Regex("系统估算后台耗电 ([0-9.]+) mAh") to { m: MatchResult -> "Estimated background drain: ${m.groupValues[1]} mAh" },
        Regex("持有唤醒锁约 ([0-9]+) 分钟") to { m: MatchResult -> "Wake lock held for about ${m.groupValues[1]} min" },
        Regex("后台状态累计 ([0-9]+)小时([0-9]+)分钟([0-9]+)秒") to { m: MatchResult -> "Background state time: ${m.groupValues[1]}h ${m.groupValues[2]}m ${m.groupValues[3]}s" },
        Regex("真实唤醒累计 ([0-9]+) 次") to { m: MatchResult -> "Wakeups: ${m.groupValues[1]} total" },
        Regex("定时任务累计 ([0-9]+) 次") to { m: MatchResult -> "Alarms: ${m.groupValues[1]} total" },
        Regex("后台任务累计 ([0-9]+) 次") to { m: MatchResult -> "Background jobs: ${m.groupValues[1]} total" }
    )
    templates.forEach { (pattern, translate) -> pattern.matchEntire(value)?.let { return translate(it) } }
    return value
}

private val diagnosticEnglishText = mapOf(
    "前台耗电占主要部分，这更像前台使用成本，不能据此判断后台异常。" to "Foreground use accounts for most of the drain; this does not establish a background anomaly.",
    "后台耗电占主要部分，值得优先核对，但单一系统估算不能证明异常。" to "Background drain accounts for most of the total; one system estimate does not prove an anomaly.",
    "这是耗电总量排行，只能说明消耗较多，不能单独证明后台异常。" to "Higher total consumption alone does not prove a background anomaly.",
    "后台耗电落在共享 UID，只能作为排行观察，不能安全拆分或限制。" to "Background drain belongs to a shared UID; use it only for ranking. It cannot be safely split or restricted.",
    "后台耗电指向系统或关键应用，仅供解释总量，不得限制或冻结。" to "Background drain points to a system or critical app; use it only to explain consumption. Do not restrict or freeze it.",
    "后台耗电排行，值得优先核对；单一系统估算不足以证明异常。" to "Check this background-consumption ranking first; one system estimate does not prove an anomaly.",
    "多项统计落在共享 UID，暂时不能把责任安全拆到其中一个应用。" to "Multiple signals belong to a shared UID; responsibility cannot safely be assigned to one app.",
    "多项统计指向系统或关键应用，但限制它可能破坏电话、通知或系统稳定性。" to "Multiple signals point to a system or critical app; restricting it may affect calls, notifications, or system stability.",
    "至少两种独立后台证据同时超过保守阈值，可先做一次可回退的后台限制。" to "At least two independent background signals exceed conservative thresholds; a reversible background restriction may be tested.",
    "目前只有一种后台线索，只够继续观察，不能据此限制或冻结。" to "Only one background signal is available; observe further. Do not restrict or freeze on this evidence.",
    "该统计为独立累计值，周期可能是开机以来；不与本次耗电窗口混合归因，也不据此限制或冻结。" to "These are independent cumulative counts, possibly since boot. Do not mix them with this drain window or use them to restrict or freeze an app.",
    "在 Scene 中只限制该应用的后台活动，先保留通知与前台性能。" to "In Scene, restrict only this app's background activity; preserve notifications and foreground performance.",
    "可能造成后台同步或消息提醒延迟。" to "Background sync or notifications may be delayed.",
    "出现功能或通知异常时，把该应用的 Scene 后台策略恢复为默认。" to "If features or notifications break, restore this app's Scene background policy to default.",
    "一次只改这个策略，正常使用一个完整观察时段后重新生成报告对比。" to "Change only this policy; use the phone normally for a full observation window, then generate another report to compare.",
    "在 APA 内完成一次未充电的应用内续航观察，用起止电量和时长判断平均掉电速度。" to "Run an unplugged battery observation in APA; use starting charge, ending charge, and duration to measure average drain.",
    "完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。" to "After a normal usage period, generate another system Bug Report or use Root read-only collection."
)
