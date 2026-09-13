package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.EvidenceFieldStatus
import com.aegis.apa.model.LocalPowerVerdict
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import java.util.Locale

object AttributionPolicy {
    const val MIN_WAKELOCK_MS = 10 * 60_000L
    const val MIN_WAKEUP_ALARMS = 100L
    const val MIN_JOBS = 100L
    const val MIN_BACKGROUND_SIGNALS_FOR_RESTRICT = 2
    const val MAX_RESULTS = 3
}

object LocalPowerAttributionEngine {
    fun attribute(snapshot: PowerDiagnosticSnapshot): LocalPowerVerdict {
        val totalConsumption = snapshot.apps
            .filter { (it.estimatedPowerMah ?: 0.0) > 0.0 }
            .sortedByDescending { it.estimatedPowerMah }
            .take(AttributionPolicy.MAX_RESULTS)
            .map(::totalCandidate)

        val backgroundSuspects = snapshot.apps
            .mapNotNull(::backgroundCandidate)
            .sortedWith(
                compareByDescending<RankedPowerCandidate> { it.maxAdviceLevel == AdviceLevel.RESTRICT }
                    .thenByDescending { it.facts.size }
            )
            .take(AttributionPolicy.MAX_RESULTS)

        val hasEvidence = totalConsumption.isNotEmpty() || backgroundSuspects.isNotEmpty()
        val limitations = snapshot.evidenceCoverage.fields
            .filterValues { it != EvidenceFieldStatus.PARSED }
            .map { (field, status) -> "${field.name}: ${status.name}" }

        return LocalPowerVerdict(
            type = if (hasEvidence) PowerVerdictType.SUFFICIENT else PowerVerdictType.INSUFFICIENT,
            totalConsumption = totalConsumption,
            backgroundSuspects = backgroundSuspects,
            nextStep = if (hasEvidence) null else
                "完成一个正常使用时段后重新生成系统 Bug Report，或使用 Root 只读采集。",
            limits = limitations
        )
    }

    private fun totalCandidate(app: AppPowerEvidence): RankedPowerCandidate {
        val power = app.estimatedPowerMah ?: 0.0
        val foregroundDominated = app.foregroundDurationMillis != null &&
            app.foregroundDurationMillis > (app.backgroundDurationMillis ?: 0L)
        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            facts = listOf("系统估算耗电 ${formatMah(power)} mAh"),
            confidence = DiagnosticConfidence.LOW,
            maxAdviceLevel = AdviceLevel.OBSERVE,
            reason = if (foregroundDominated) {
                "前台使用时间明显更多，这更像前台使用成本，不能据此判断后台异常。"
            } else {
                "这是耗电总量排行，只能说明消耗较多，不能单独证明后台异常。"
            }
        )
    }

    private fun backgroundCandidate(app: AppPowerEvidence): RankedPowerCandidate? {
        val signals = buildList {
            app.wakeLockDurationMillis?.takeIf { it >= AttributionPolicy.MIN_WAKELOCK_MS }?.let {
                add("持有唤醒锁约 ${it / 60_000} 分钟")
            }
            val wakeups = maxOf(app.wakeupCount ?: 0L, app.alarmCount ?: 0L)
            if (wakeups >= AttributionPolicy.MIN_WAKEUP_ALARMS) {
                add("唤醒/闹钟计数最高 $wakeups 次")
            }
            app.jobCount?.takeIf { it >= AttributionPolicy.MIN_JOBS }?.let {
                add("调度后台任务 $it 次")
            }
        }
        if (signals.isEmpty()) return null

        val protected = isProtected(app)
        val shared = app.sharedUid || app.packageNames.size > 1
        val canRestrict = signals.size >= AttributionPolicy.MIN_BACKGROUND_SIGNALS_FOR_RESTRICT &&
            !protected && !shared
        val level = if (canRestrict) AdviceLevel.RESTRICT else AdviceLevel.OBSERVE
        val confidence = when {
            signals.size >= 3 -> DiagnosticConfidence.HIGH
            signals.size == 2 -> DiagnosticConfidence.MEDIUM
            else -> DiagnosticConfidence.LOW
        }
        val reason = when {
            shared -> "多项统计落在共享 UID，暂时不能把责任安全拆到其中一个应用。"
            protected -> "多项统计指向系统或关键应用，但限制它可能破坏电话、通知或系统稳定性。"
            canRestrict -> "至少两种独立后台证据同时超过保守阈值，可先做一次可回退的后台限制。"
            else -> "目前只有一种后台线索，只够继续观察，不能据此限制或冻结。"
        }

        return RankedPowerCandidate(
            uid = app.uid,
            packageNames = app.packageNames,
            facts = signals,
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
}
