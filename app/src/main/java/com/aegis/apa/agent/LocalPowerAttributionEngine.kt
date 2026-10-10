package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.EvidenceFieldStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import java.util.Locale

object AttributionPolicy {
    const val MIN_WAKELOCK_MS = 10 * 60_000L
    const val MIN_BACKGROUND_POWER_MAH = 100.0
    const val MIN_BACKGROUND_POWER_SHARE = 0.5
    const val MIN_BACKGROUND_SIGNALS_FOR_RESTRICT = 2
    const val MAX_RESULTS = 3
}

object LocalPowerAttributionEngine {
    fun attribute(snapshot: PowerDiagnosticSnapshot): LocalPowerVerdict {
        val comparableApps = snapshot.apps.filter {
            PowerEvidenceConsistency.issue(it, snapshot.system.batteryDrainWindow) == null
        }
        val totalConsumption = comparableApps
            .filter { (it.estimatedPowerMah ?: 0.0) > 0.0 }
            .sortedByDescending { it.estimatedPowerMah }
            .take(AttributionPolicy.MAX_RESULTS)
            .map { totalCandidate(it, snapshot.system.batteryDrainWindow) }

        val backgroundSuspects = snapshot.apps
            .mapNotNull { backgroundCandidate(it, snapshot.system.batteryDrainWindow) }
            .sortedWith(
                compareByDescending<RankedPowerCandidate> { it.maxAdviceLevel == AdviceLevel.RESTRICT }
                    .thenByDescending { it.facts.size }
            )
            .take(AttributionPolicy.MAX_RESULTS)

        val backgroundConsumption = comparableApps
            .filter { (it.backgroundPowerMah ?: 0.0) > 0.0 }
            .sortedByDescending { it.backgroundPowerMah }
            .take(AttributionPolicy.MAX_RESULTS)
            .map { backgroundConsumptionCandidate(it, snapshot.system.batteryDrainWindow) }

        val schedulingObservations = snapshot.apps
            .sortedWith(
                compareByDescending<AppPowerEvidence> { backgroundSignals(it).size == 1 }
                    .thenByDescending { it.wakeupCount ?: 0L }
                    .thenByDescending { it.alarmCount ?: 0L }
            )
            .mapNotNull(::schedulingObservation)
            .take(AttributionPolicy.MAX_RESULTS)

        val hasEvidence = totalConsumption.isNotEmpty() || backgroundConsumption.isNotEmpty() ||
            backgroundSuspects.isNotEmpty() || schedulingObservations.isNotEmpty()
        val limitations = snapshot.evidenceCoverage.fields
            .filterValues { it != EvidenceFieldStatus.PARSED }
            .map { (field, status) -> "${field.name}: ${status.name}" } + snapshot.apps.mapNotNull { app ->
                PowerEvidenceConsistency.issue(app, snapshot.system.batteryDrainWindow)?.let {
                    "${app.displayNames.firstOrNull() ?: app.packageNames.firstOrNull() ?: "UID ${app.uid}"}：$it"
                }
            }

        return LocalPowerVerdict(
            type = if (hasEvidence) PowerVerdictType.SUFFICIENT else PowerVerdictType.INSUFFICIENT,
            totalConsumption = totalConsumption,
            backgroundSuspects = backgroundSuspects,
            nextStep = if (hasEvidence) null else if (snapshot.inputSource == DiagnosticInputSource.BUGREPORT) {
                "在 APA 内完成一次未充电的应用内续航观察，用起止电量和时长判断平均掉电速度。"
            } else {
                "完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。"
            },
            limits = limitations,
            backgroundConsumption = backgroundConsumption,
            schedulingObservations = schedulingObservations
        )
    }

    private fun totalCandidate(app: AppPowerEvidence, window: com.aegis.apa.model.BatteryDrainWindowEvidence?): RankedPowerCandidate {
        val power = app.estimatedPowerMah ?: 0.0
        val conflict = PowerEvidenceConsistency.issue(app, window)
        val foregroundDominated = app.foregroundPowerMah?.let { it > power / 2.0 && it <= power } == true
        val foregroundTimeDominated = app.foregroundDurationMillis?.let {
            it > (app.backgroundDurationMillis ?: Long.MAX_VALUE)
        } == true
        val backgroundDominated = app.backgroundPowerMah != null && power > 0.0 &&
            app.backgroundPowerMah / power >= AttributionPolicy.MIN_BACKGROUND_POWER_SHARE
        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            displayNames = app.displayNames,
            facts = listOf("系统估算耗电 ${formatMah(power)} mAh") + listOfNotNull(conflict),
            confidence = DiagnosticConfidence.LOW,
            maxAdviceLevel = AdviceLevel.OBSERVE,
            reason = when {
                conflict != null -> "统计口径待核查：$conflict；不能作为本周期同口径排行或限制依据。"
                foregroundDominated -> "前台耗电占主要部分，这更像前台使用成本，不能据此判断后台异常。"
                backgroundDominated -> "后台耗电占主要部分，值得优先核对，但单一系统估算不能证明异常。"
                foregroundTimeDominated -> "前台状态计时较多，但时长不等于耗电占比；不能据此判断前台耗电占主导或后台异常。"
                else -> "这是耗电总量排行，只能说明消耗较多，不能单独证明后台异常。"
            }
        )
    }

    private fun backgroundConsumptionCandidate(app: AppPowerEvidence, window: com.aegis.apa.model.BatteryDrainWindowEvidence?): RankedPowerCandidate {
        val backgroundPower = app.backgroundPowerMah ?: 0.0
        val totalPower = app.estimatedPowerMah
        val conflict = PowerEvidenceConsistency.issue(app, window)
        val facts = buildList {
            if (totalPower != null && totalPower > 0.0 && backgroundPower <= totalPower) {
                add(
                    "系统估算后台耗电 ${formatMah(backgroundPower)} mAh，" +
                        "占该应用总耗电 ${formatPercent(backgroundPower / totalPower * 100.0)}%"
                )
            } else {
                add("系统估算后台耗电 ${formatMah(backgroundPower)} mAh")
            }
            app.backgroundDurationMillis?.let { add("后台状态累计 ${formatDuration(it)}") }
            conflict?.let { add(it) }
        }
        val protected = isProtected(app)
        val shared = app.sharedUid || app.packageNames.size > 1
        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            displayNames = app.displayNames,
            facts = facts,
            confidence = if (totalPower != null && conflict == null) DiagnosticConfidence.MEDIUM else DiagnosticConfidence.LOW,
            maxAdviceLevel = AdviceLevel.OBSERVE,
            reason = when {
                conflict != null -> "统计口径待核查：$conflict；不能据此确认后台异常或模块因果。"
                shared -> "后台耗电落在共享 UID，只能作为排行观察，不能安全拆分或限制。"
                protected -> "后台耗电指向系统或关键应用，仅供解释总量，不得限制或冻结。"
                else -> "后台耗电排行，值得优先核对；单一系统估算不足以证明异常。"
            }
        )
    }

    private fun hasHighBackgroundPower(app: AppPowerEvidence): Boolean {
        val background = app.backgroundPowerMah ?: return false
        val total = app.estimatedPowerMah ?: return false
        return background.isFinite() && total.isFinite() && total > 0 &&
            background >= AttributionPolicy.MIN_BACKGROUND_POWER_MAH &&
            background / total >= AttributionPolicy.MIN_BACKGROUND_POWER_SHARE
    }

    private fun backgroundSignals(app: AppPowerEvidence): List<String> = buildList {
        val backgroundPower = app.backgroundPowerMah
        val totalPower = app.estimatedPowerMah
        if (hasHighBackgroundPower(app)) {
            add(
                "系统估算后台耗电 ${formatMah(backgroundPower!!)} mAh，" +
                    if (backgroundPower <= totalPower!!) "占该应用总耗电 ${formatPercent(backgroundPower / totalPower * 100.0)}%"
                    else "后台估算超过应用总量，统计口径待核对"
            )
        }
        app.wakeLockDurationMillis?.takeIf { it >= AttributionPolicy.MIN_WAKELOCK_MS }?.let {
            add("持有唤醒锁约 ${it / 60_000} 分钟")
        }
    }

    private fun backgroundCandidate(app: AppPowerEvidence,
        window: com.aegis.apa.model.BatteryDrainWindowEvidence?): RankedPowerCandidate? {
        val signals = backgroundSignals(app)
        val conflict = PowerEvidenceConsistency.issue(app, window)
        if (signals.size < AttributionPolicy.MIN_BACKGROUND_SIGNALS_FOR_RESTRICT &&
            !hasHighBackgroundPower(app) && conflict == null) return null
        if (signals.size == 1 && app.packageNames.isEmpty() && (app.uid ?: Int.MAX_VALUE) < 10_000) {
            return null
        }

        val protected = isProtected(app)
        val shared = app.sharedUid || app.packageNames.size > 1
        val canRestrict = !protected && !shared && conflict == null &&
            signals.size >= AttributionPolicy.MIN_BACKGROUND_SIGNALS_FOR_RESTRICT
        val level = if (canRestrict) AdviceLevel.RESTRICT else AdviceLevel.OBSERVE
        val confidence = when {
            conflict != null -> DiagnosticConfidence.LOW
            signals.size >= 3 -> DiagnosticConfidence.HIGH
            signals.size == 2 -> DiagnosticConfidence.MEDIUM
            else -> DiagnosticConfidence.LOW
        }
        val reason = when {
            conflict != null -> "统计口径待核查：$conflict；不能据此确认应用异常或模块因果，不建议限制或冻结。"
            shared -> "多项统计落在共享 UID，暂时不能把责任安全拆到其中一个应用。"
            protected -> "多项统计指向系统或关键应用，但限制它可能破坏电话、通知或系统稳定性。"
            canRestrict -> "至少两种独立后台证据同时超过保守阈值，可先做一次可回退的后台限制。"
            else -> "后台耗电偏高，待核查；单一系统估算尚不能确认异常或模块因果，不建议限制或冻结。"
        }

        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            displayNames = app.displayNames,
            facts = signals + listOfNotNull(conflict),
            confidence = confidence,
            maxAdviceLevel = level,
            reason = reason,
            sceneAction = if (level == AdviceLevel.RESTRICT)
                "在 Scene 中只限制该应用的后台活动，先保留通知与前台性能。" else null,
            risk = if (level == AdviceLevel.RESTRICT)
                "可能造成后台同步或消息提醒延迟。" else null,
            rollback = if (level == AdviceLevel.RESTRICT)
                "出现功能或通知异常时，把该应用的 Scene 后台策略恢复为默认。" else null,
            retest = if (level == AdviceLevel.RESTRICT)
                "一次只改这个策略，正常使用一个完整观察时段后重新生成报告对比。" else null
        )
    }

    private fun schedulingObservation(app: AppPowerEvidence): RankedPowerCandidate? {
        // Reuse the observation channel, keeping uncorroborated battery evidence separate
        // from cumulative scheduler counters. Counts never supply a same-window signal.
        val backgroundClues = backgroundSignals(app).takeIf { it.size == 1 }.orEmpty()
        val facts = buildList {
            addAll(backgroundClues)
            app.wakeupCount?.let { add("真实唤醒累计 $it 次") }
            app.alarmCount?.let { add("定时任务累计 $it 次") }
            app.jobCount?.let { add("后台任务累计 $it 次") }
        }
        if (facts.isEmpty()) return null
        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            displayNames = app.displayNames,
            facts = facts,
            confidence = DiagnosticConfidence.LOW,
            maxAdviceLevel = AdviceLevel.OBSERVE,
            reason = buildString {
                if (backgroundClues.isNotEmpty()) {
                    append("待验证后台线索：目前只有一种后台证据，不能确认异常或因果；可在使用强度相近的窗口核对该指标是否持续偏高。")
                }
                if (app.wakeupCount != null || app.alarmCount != null || app.jobCount != null) {
                    append("该统计为独立累计值，周期可能是开机以来；不与本次耗电窗口混合归因，也不据此限制或冻结。")
                }
                if (app.sharedUid || app.packageNames.size > 1 || isProtected(app)) {
                    append("系统或共享 UID 仅供观察，不能安全拆分责任或据此限制。")
                }
            }
        )
    }

    private fun isProtected(app: AppPowerEvidence): Boolean {
        if ((app.uid ?: Int.MAX_VALUE) < 10_000) return true
        return app.packageNames.any { packageName ->
            val value = packageName.lowercase(Locale.ROOT)
            value == "android" ||
                value.startsWith("com.android.") ||
                listOf("phone", "telephony", "sms", "launcher", "payment", "magisk", "kernelsu", "root")
                    .any(value::contains)
        }
    }

    private fun formatMah(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return "${hours}小时${minutes}分钟${seconds}秒"
    }
}
