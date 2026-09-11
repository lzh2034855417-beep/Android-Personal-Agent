package com.aegis.apa.agent

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerFinding
import java.util.Locale

object PowerDiagnosticFindingEngine {
    private const val POWER_THRESHOLD_MAH = 100.0
    private const val WAKELOCK_THRESHOLD_MS = 10 * 60_000L
    private const val WAKEUP_THRESHOLD = 100L

    fun find(snapshot: PowerDiagnosticSnapshot): List<PowerFinding> = snapshot.apps.mapNotNull(::findingFor)

    private fun findingFor(app: AppPowerEvidence): PowerFinding? {
        val evidence = buildList {
            app.estimatedPowerMah?.takeIf { it >= POWER_THRESHOLD_MAH }?.let {
                add("系统估算耗电 ${String.format(Locale.US, "%.1f", it)} mAh")
            }
            app.wakeLockDurationMillis?.takeIf { it >= WAKELOCK_THRESHOLD_MS }?.let {
                add("持有唤醒锁约 ${it / 60_000} 分钟")
            }
            app.wakeupCount?.takeIf { it >= WAKEUP_THRESHOLD }?.let {
                add("唤醒设备 $it 次")
            }
            app.alarmCount?.takeIf { it >= WAKEUP_THRESHOLD }?.let {
                add("触发闹钟 $it 次")
            }
            app.jobCount?.takeIf { it >= WAKEUP_THRESHOLD }?.let {
                add("调度后台任务 $it 次")
            }
        }
        if (evidence.isEmpty()) return null

        val protected = isProtected(app)
        val rawLevel = when {
            evidence.size >= 3 &&
                (app.estimatedPowerMah ?: 0.0) >= 300.0 &&
                (app.wakeLockDurationMillis ?: 0L) >= 30 * 60_000L -> AdviceLevel.FREEZE_CANDIDATE
            evidence.size >= 2 -> AdviceLevel.RESTRICT
            else -> AdviceLevel.OBSERVE
        }
        val level = if (protected && rawLevel == AdviceLevel.FREEZE_CANDIDATE) AdviceLevel.RESTRICT else rawLevel
        val confidence = when {
            evidence.size >= 3 -> DiagnosticConfidence.HIGH
            evidence.size == 2 -> DiagnosticConfidence.MEDIUM
            else -> DiagnosticConfidence.LOW
        }
        val name = app.packageNames.firstOrNull() ?: app.uid?.let { "UID $it" } ?: "未知应用"
        val sceneAdvice = when (level) {
            AdviceLevel.OBSERVE -> "先在 Scene 中观察该应用的后台活动，不要仅凭这一条证据冻结。"
            AdviceLevel.RESTRICT -> "可在 Scene 的应用策略中先限制后台活动或降低后台性能，观察一轮续航后再决定是否冻结。"
            AdviceLevel.FREEZE_CANDIDATE -> "如果它不是通讯、桌面、支付、Root 或其他关键应用，可在 Scene 中手动冻结并观察通知和功能是否正常。"
        }

        return PowerFinding(
            id = "app:${app.uid ?: name}",
            title = "$name 存在耗电线索",
            packageNames = app.packageNames,
            evidence = evidence,
            explanation = "多项系统统计指向该应用可能在后台保持活跃；这是相关性诊断，不等同于已经证明唯一责任。",
            confidence = confidence,
            adviceLevel = level,
            sceneAdvice = sceneAdvice,
            caveat = if (protected) {
                "检测到系统或关键应用特征，不建议冻结；错误限制可能影响电话、通知、桌面或系统稳定性。"
            } else {
                "建议一次只改一个 Scene 策略，并至少观察一个完整充放电周期，便于回退和判断效果。"
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
}
