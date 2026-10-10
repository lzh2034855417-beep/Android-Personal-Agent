package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.AppPowerEvidence
import java.util.Locale

object AppPowerRanking {
    fun build(snapshot: PowerDiagnosticSnapshot, language: AppLanguage): String {
        val english = language == AppLanguage.EN
        val measured = snapshot.apps.filter {
            it.estimatedPowerMah?.let { value -> value.isFinite() && value > 0 } == true
        }.withIndex().groupBy { indexed ->
            val app = indexed.value
            app.uid?.let { uid -> "uid:$uid" } ?:
                if (app.packageNames.isNotEmpty()) "packages:${app.packageNames.sorted()}"
                else "unmapped:${indexed.index}"
        }.values.map { indexedRows ->
                val rows = indexedRows.map { it.value }
                val highest = rows.maxBy { it.estimatedPowerMah!! }
                highest.copy(
                    packageNames = rows.flatMap { it.packageNames }.distinct(),
                    displayNames = rows.flatMap { it.displayNames }.distinct(),
                    sharedUid = rows.any { it.sharedUid } || rows.flatMap { it.packageNames }.distinct().size > 1
                )
            }.sortedWith(compareByDescending<AppPowerEvidence> { it.estimatedPowerMah }
                .thenBy { it.uid ?: Int.MAX_VALUE }.thenBy { it.packageNames.joinToString() })
        fun isGroup(app: AppPowerEvidence) = app.sharedUid || app.packageNames.size != 1 ||
            (app.uid != null && app.uid < 10_000)
        val conflicting = measured.filter { PowerEvidenceConsistency.issue(it, snapshot.system.batteryDrainWindow) != null }
        val comparable = measured.filterNot { it in conflicting }
        val apps = comparable.filterNot(::isGroup).take(5)
        val groups = comparable.filter(::isGroup).take(5)
        val window = snapshot.system.batteryDrainWindow
        val denominator = window?.drainMah?.takeIf { it.isFinite() && it > 0 }
        fun row(app: AppPowerEvidence): String {
            val mah = app.estimatedPowerMah!!
            val names = app.displayNames.ifEmpty { app.packageNames }.take(3).joinToString(" / ") { it.take(80) }
            val label = if (isGroup(app)) {
                val group = if (app.sharedUid || app.packageNames.size > 1) {
                    if (english) "Shared UID" else "共享 UID"
                } else if (english) "System / unmapped UID" else "系统／未归属 UID"
                "$group ${app.uid?.toString() ?: "?"}${if (names.isBlank()) "" else " ($names)"}"
            } else names
            val share = denominator?.takeIf { mah <= it &&
                PowerEvidenceConsistency.issue(app, snapshot.system.batteryDrainWindow) == null }?.let {
                val upper = window?.drainUpperMah ?: it
                if (upper > it) String.format(Locale.US, "%.1f%%–%.1f%%", mah / upper * 100, mah / it * 100)
                else String.format(Locale.US, "%.1f%%", mah / it * 100)
            } ?: if (english) "share unavailable" else "占比不可用"
            return "$label · ${String.format(Locale.US, "%.1f", mah)} mAh · $share"
        }
        return buildString {
            appendLine(if (english) "App drain Top 5 (same report window)" else "本周期应用耗电 Top 5")
            appendLine(if (snapshot.system.batteryDrainWindow != null) {
                if (english) "Window: since the last full charge; share of total window drain."
                else "窗口：自上次充满后；占比以周期总耗电为分母。"
            } else if (english) "Report window start/end unavailable; share unavailable."
                else "报告窗口起止未提供；占比不可用。")
            if (apps.isEmpty()) appendLine(if (english) "No app-level drain data." else "暂无可归属到单个应用的耗电数据。")
            apps.forEachIndexed { index, app -> appendLine("${index + 1}. ${row(app)}") }
            if (groups.isNotEmpty()) {
                appendLine(if (english) "Shared UID / system groups (not split into apps)" else "共享 UID／系统组（不拆分到单个应用）")
                groups.forEachIndexed { index, app -> appendLine("${index + 1}. ${row(app)}") }
            }
            if (conflicting.isNotEmpty()) {
                appendLine(if (english) "Measurement basis needs verification (excluded from this window's ranking)"
                    else "统计口径待核对（不参与本周期排行）")
                conflicting.take(5).forEach { app ->
                    appendLine("${row(app)}；${PowerEvidenceConsistency.issue(app, snapshot.system.batteryDrainWindow, language)}")
                }
            }
            append(if (english) "Higher rank does not prove abnormal drain; values are system estimates."
                else "排名高不等于耗电异常；耗电量为系统估算。")
        }
    }
}
