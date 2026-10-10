package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.BatteryDrainWindowEvidence
import java.util.Locale

/** A basis conflict is a verification warning, not proof of an app or parser fault. */
internal object PowerEvidenceConsistency {
    fun issue(app: AppPowerEvidence, window: BatteryDrainWindowEvidence?,
              language: AppLanguage = AppLanguage.ZH_CN): String? {
        val total = app.estimatedPowerMah?.takeIf { it.isFinite() && it > 0 } ?: return null
        val background = app.backgroundPowerMah?.takeIf { it.isFinite() && it >= 0 }
        val drain = window?.drainMah?.takeIf { it.isFinite() && it > 0 }
        fun mah(value: Double) = String.format(Locale.US, "%.1f", value)
        return when {
            background != null && background > total -> if (language == AppLanguage.EN)
                "Background estimate ${mah(background)} mAh exceeds app total ${mah(total)} mAh; measurement basis needs verification"
            else "后台估算 ${mah(background)} mAh 超过应用总量 ${mah(total)} mAh；统计口径待核对"
            drain != null && total > drain -> if (language == AppLanguage.EN)
                "App estimate ${mah(total)} mAh exceeds window total ${mah(drain)} mAh; measurement basis needs verification (system estimates or window alignment may differ)"
            else "应用估算 ${mah(total)} mAh 超过周期总量 ${mah(drain)} mAh；统计口径待核对（可能是系统估算或窗口不一致）"
            else -> null
        }
    }
}
