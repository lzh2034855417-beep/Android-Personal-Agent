package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import java.math.BigDecimal
import java.math.RoundingMode

object VerificationResultFormatter {
    fun format(record: VerificationRecord, comparison: VerificationComparison, language: AppLanguage): String {
        val zh = language == AppLanguage.ZH_CN
        fun text(chinese: String, english: String) = if (zh) chinese else english
        return buildString {
            appendLine(record.title)
            appendLine(text("比较规则版本", "Comparison rule version") + ": ${comparison.ruleVersion}")
            appendLine(text("来源", "Input source") + ": ${record.baseline.inputSource.name} → ${record.retest?.inputSource?.name ?: text("未知", "unknown")}")
            for ((label, summary) in listOf(text("基线归属", "Baseline ownership") to record.baseline, text("复测归属", "Retest ownership") to record.retest)) {
                val ownership = when (summary?.context?.device?.ownership) {
                    VerificationOwnership.USER_CONFIRMED -> text("用户确认", "user confirmed")
                    VerificationOwnership.LOCAL_COLLECTION -> text("本机采集", "local collection")
                    else -> text("未知", "unknown")
                }
                appendLine("$label: $ownership")
            }
            if (comparison.commonReasons.isNotEmpty()) {
                appendLine(text("共同限制", "Common limitations") + ":")
                comparison.commonReasons.forEach { appendLine(reason(it, zh)) }
            }
            comparison.metrics.forEach { result ->
                appendLine()
                appendLine(metricName(result.key.kind, zh) + " [${result.key.kind.wireId}]" +
                    if (result.key.packageNames.isEmpty()) "" else " (${result.key.packageNames.joinToString(", ")})")
                appendLine(text("调整前原值", "Before raw value") + ": " + raw(result.before, zh))
                appendLine(text("复测原值", "Retest raw value") + ": " + raw(result.after, zh))
                if (result.status == VerificationComparisonStatus.CHANGE_OBSERVED || result.status == VerificationComparisonStatus.NO_NUMERIC_CHANGE) {
                    val unit = when (result.rateUnit) {
                        "mAh/battery-hour" -> text("mAh/电池运行小时", "mAh/battery-hour")
                        "minutes/battery-hour" -> text("分钟/电池运行小时", "minutes/battery-hour")
                        else -> text("次/电池运行小时", "count/battery-hour")
                    }
                    appendLine(text("观察速率", "Observed rates") + ": ${number(result.beforeRate!!)} → ${number(result.afterRate!!)} $unit")
                    val observation = when {
                        result.status == VerificationComparisonStatus.NO_NUMERIC_CHANGE -> text("本次观察值相同", "Observed values equal")
                        result.delta!! < 0 -> text("本次观察值减少", "Observed value decreased")
                        else -> text("本次观察值增加", "Observed value increased")
                    }
                    appendLine("$observation: ${number(result.delta!!)} $unit")
                    result.relativeChange?.let {
                        val percentage = decimal(BigDecimal.valueOf(it).multiply(BigDecimal.valueOf(100)))
                        appendLine(text("相对观察变化", "Relative observed change") + ": $percentage%")
                    }
                } else {
                    appendLine(text("无法进行数值比较", "Numeric comparison unavailable") + " [${result.status.wireId}]")
                    result.reasons.forEach { appendLine(reason(it, zh)) }
                }
            }
            appendLine()
            appendLine(text("用户记录的副作用", "User reported side effects") + ": " + (record.change?.sideEffects?.takeIf { it.isNotBlank() } ?: text("未记录", "not recorded")))
            appendLine(text("原设置/回退说明", "Original setting / rollback") + ": " + (record.change?.rollback?.takeIf { it.isNotBlank() } ?: text("未记录", "not recorded")))
            appendLine(text("观察变化不能证明由本次调整造成；副作用需要独立考虑。", "Observed changes do not prove the adjustment caused them; consider side effects independently."))
            append(text("下一步可由你选择：补充独立复测、手动回退或结束记录。", "You can choose to collect an independent retest, manually roll back, or close this record."))
        }
    }

    private fun raw(metric: VerificationMetric?, zh: Boolean): String {
        val unknown = if (zh) "未知" else "unknown"
        if (metric == null) return if (zh) "字段缺失" else "field missing"
        val estimate = when (metric.estimate) {
            VerificationEstimate.ACTUAL -> if (zh) "实际" else "actual"
            VerificationEstimate.ESTIMATED -> if (zh) "估算" else "estimated"
            VerificationEstimate.UNKNOWN -> unknown
        }
        val unit = when (metric.unit) {
            VerificationUnit.MAH -> "mAh"
            VerificationUnit.MILLIS -> if (zh) "毫秒" else "ms"
            VerificationUnit.COUNT -> if (zh) "次" else "count"
        }
        val window = metric.window
        val value = metric.value?.let { if (it.isFinite()) number(it) else "$unknown ($it)" } ?: unknown
        return "$value $unit ($estimate); ${window.source}; ${window.scope.wireId}; " +
            "${window.startedAt ?: unknown} → ${window.endedAt ?: unknown}; " +
            (if (zh) "电池运行毫秒" else "battery duration ms") + ": ${window.batteryDurationMillis ?: unknown}; " +
            (if (zh) "充电" else "charging") + ": ${window.charging.wireId}; ${metric.status.name}"
    }

    private fun number(value: Double) = decimal(BigDecimal.valueOf(value))
    private fun decimal(value: BigDecimal): String {
        val rounded = value.setScale(6, RoundingMode.HALF_UP)
        // Keep sub-display-precision evidence visible, including unequal raw values and tiny deltas.
        return if (rounded.compareTo(value) == 0) rounded.stripTrailingZeros().toPlainString()
            else value.stripTrailingZeros().toString()
    }

    private fun metricName(kind: VerificationMetricKind, zh: Boolean): String {
        val names = when (kind) {
            VerificationMetricKind.OVERALL_POWER -> "整体耗电" to "Overall power"
            VerificationMetricKind.APP_TOTAL_POWER -> "应用组总耗电" to "App group total power"
            VerificationMetricKind.APP_FOREGROUND_POWER -> "应用组前台耗电" to "App group foreground power"
            VerificationMetricKind.APP_BACKGROUND_POWER -> "应用组后台耗电" to "App group background power"
            VerificationMetricKind.FOREGROUND_DURATION -> "前台进程时长" to "Foreground process duration"
            VerificationMetricKind.BACKGROUND_DURATION -> "后台进程时长" to "Background process duration"
            VerificationMetricKind.WAKELOCK_DURATION -> "唤醒锁时长" to "Wakelock duration"
            VerificationMetricKind.WAKEUP_COUNT -> "唤醒次数" to "Wakeup count"
            VerificationMetricKind.ALARM_COUNT -> "闹钟次数" to "Alarm count"
            VerificationMetricKind.JOB_COUNT -> "任务次数" to "Job count"
        }
        return if (zh) names.first else names.second
    }

    private fun reason(reason: VerificationReason, zh: Boolean): String {
        val names = when (reason) {
            VerificationReason.MISSING_CHANGE -> "尚未记录并确认手动调整" to "Manual adjustment has not been recorded and confirmed"
            VerificationReason.MISSING_RETEST -> "缺少复测摘要" to "Retest summary is missing"
            VerificationReason.LOCAL_SCOPE_UNKNOWN -> "本机作用域未知" to "Local record scope is unknown"
            VerificationReason.INPUT_SOURCE_MISMATCH -> "诊断来源不同" to "Diagnostic input sources differ"
            VerificationReason.DEVICE_CONFLICT -> "设备型号或系统信息冲突" to "Device or system context conflicts"
            VerificationReason.DEVICE_CONTEXT_MISSING -> "缺少设备型号或系统信息" to "Required device or system context is missing"
            VerificationReason.OWNERSHIP_UNCONFIRMED -> "设备归属未经确认" to "Device ownership is unconfirmed"
            VerificationReason.SCENARIO_UNCONFIRMED -> "使用场景相似性未经确认" to "Scenario similarity is unconfirmed"
            VerificationReason.OTHER_CHANGES_UNCONFIRMED -> "没有其他调整的声明未经确认" to "Absence of other adjustments is unconfirmed"
            VerificationReason.NO_CHARGING_UNCONFIRMED -> "未充电声明未经确认" to "No charging declaration is unconfirmed"
            VerificationReason.DUPLICATE_SUMMARY -> "复测与基线为重复摘要" to "Retest duplicates the baseline summary"
            VerificationReason.WINDOW_MISSING -> "统计窗口未知，需要独立且可核验的窗口" to "Statistics window is unknown; an independent verifiable window is needed"
            VerificationReason.WINDOW_INVALID -> "统计窗口无效或超出支持范围" to "Statistics window is invalid or outside supported range"
            VerificationReason.WINDOW_OVERLAP -> "统计窗口重叠" to "Statistics windows overlap"
            VerificationReason.CHANGE_OUTSIDE_WINDOWS -> "统计窗口与调整确认时间次序不符" to "Statistics windows do not bracket the adjustment confirmation"
            VerificationReason.SAME_CUMULATIVE_CYCLE -> "双方来自同一累计周期" to "Both samples use the same cumulative cycle"
            VerificationReason.SAMPLE_TOO_SHORT -> "样本过短，窗口和电池运行时长均需至少30分钟" to "Sample too short; both window and battery duration require at least 30 minutes"
            VerificationReason.BATTERY_DURATION_MISSING -> "电池运行时长未知" to "Battery duration is unknown"
            VerificationReason.BATTERY_DURATION_INVALID -> "电池运行时长无效或超过窗口" to "Battery duration is invalid or exceeds its window"
            VerificationReason.BATTERY_DURATION_RATIO -> "电池运行时长比值超出0.8至1.25" to "Battery duration ratio is outside 0.8 to 1.25"
            VerificationReason.FIELD_MISSING -> "指标字段缺失，不能视为零" to "Metric field is missing and cannot be treated as zero"
            VerificationReason.FIELD_UNAVAILABLE -> "指标字段未成功解析" to "Metric field was not successfully parsed"
            VerificationReason.FIELD_TRUNCATED -> "指标字段被截断" to "Metric field is truncated"
            VerificationReason.VALUE_INVALID -> "指标数值无效或计算超出支持范围" to "Metric value is invalid or calculation exceeds supported range"
            VerificationReason.SOURCE_UNAVAILABLE -> "指标来源未完整成功采集或解析" to "Metric source was not fully collected or parsed"
            VerificationReason.METRIC_SOURCE_MISMATCH -> "指标所属来源不匹配" to "Metric source provenance does not match"
            VerificationReason.ESTIMATE_MISMATCH -> "实际与估算类别不一致" to "Actual and estimated categories differ"
            VerificationReason.ESTIMATE_UNKNOWN -> "实际或估算类别未知" to "Actual or estimated category is unknown"
            VerificationReason.UNIT_MISMATCH -> "指标单位不匹配" to "Metric units do not match"
            VerificationReason.SCOPE_MISMATCH -> "统计范围不同" to "Statistics scopes differ"
            VerificationReason.SCOPE_UNKNOWN -> "统计范围未知" to "Statistics scope is unknown"
            VerificationReason.GROUP_CHANGED -> "应用组完整包名集合变化" to "Complete package membership of the app group changed"
            VerificationReason.WINDOW_CHARGING -> "统计窗口存在充电或充电状态未知" to "Statistics window includes charging or charging status is unknown"
            VerificationReason.METRIC_AMBIGUOUS -> "指标键重复，无法确定原值" to "Duplicate metric keys make the original value ambiguous"
        }
        return "[${reason.wireId}] " + if (zh) names.first else names.second
    }
}
