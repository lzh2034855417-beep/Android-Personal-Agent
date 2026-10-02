package com.aegis.apa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.BatteryObservationAnalyzer
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.model.BatteryObservationResult
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.PowerVerdictType
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.SampleTime
import com.aegis.apa.model.packageCopyText
import com.aegis.apa.model.userFacingName
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.LocalAppLanguage

@Composable
fun PowerDiagnosticPanel(
    state: PowerDiagnosticUiState,
    snapshot: PowerDiagnosticSnapshot?,
    observationStart: BatteryObservationPoint?,
    observationResult: BatteryObservationResult?,
    observationNotice: String?,
    selected: Boolean,
    rootAvailable: Boolean,
    onCollect: () -> Unit,
    onImportBugReport: () -> Unit,
    onStartObservation: () -> Unit,
    onFinishObservation: (Boolean) -> Unit,
    onClearObservation: () -> Unit,
    onToggleSelected: () -> Unit,
    onRemove: () -> Unit,
    onCopyPackage: (String) -> Unit,
    showDiagnosticDetails: Boolean = true,
    modifier: Modifier = Modifier,
    onOpenVerification: () -> Unit = {}
) {
    val language = LocalAppLanguage.current
    val english = language == AppLanguage.EN
    var showImportHelp by rememberSaveable { mutableStateOf(false) }
    var showObservation by rememberSaveable {
        mutableStateOf(observationStart != null || observationResult != null)
    }
    var showFinishConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmation = false },
            title = { Text(if (english) "Was the phone charged during this period?" else "期间是否充过电？") },
            text = {
                Text(if (english) "APA does not stay active in the background, so it may miss charging events. Answer from what actually happened; if unsure, choose charged." else "APA 不需要常驻后台，因此无法保证记录到所有充电事件。请按实际情况确认；不确定时按“充过电”处理。")
            },
            confirmButton = {
                Button(onClick = {
                    showFinishConfirmation = false
                    onFinishObservation(false)
                }) { Text(if (english) "Not charged — calculate" else "没有充过电，计算") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinishConfirmation = false
                    onFinishObservation(true)
                }) { Text(if (english) "Charged or unsure — discard" else "充过电或不确定，本次作废") }
            }
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.power_diagnostic), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.power_diagnostic_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(stringResource(R.string.app_power_attribution), style = MaterialTheme.typography.titleSmall)
            when (state) {
                PowerDiagnosticUiState.Idle -> Text(if (english) "No report imported or collected. Standard users can import an Android Bug Report." else "尚未导入或采集；普通用户可导入系统 Bug Report。")
                PowerDiagnosticUiState.Importing -> Text(if (english) "Reading the system report safely…" else "正在安全读取系统报告…")
                is PowerDiagnosticUiState.Collecting -> Text(if (english) "Collecting ${state.completed}/${state.total}: ${state.source}" else "正在采集 ${state.completed}/${state.total}：${state.source}")
                PowerDiagnosticUiState.Ready -> Text(
                    if (english) {
                        if (selected) "Local reading complete. The diagnostic summary is attached; the original system report will not be sent."
                        else "Local reading complete. The diagnostic summary is not attached."
                    } else if (selected) "本地读取完成；已附加诊断摘要，原始系统报告不会发送。"
                    else "本地读取完成；本次没有附加诊断摘要。"
                )
                is PowerDiagnosticUiState.Error -> Text(if (english) "Read failed: ${state.message}" else "读取失败：${state.message}")
                PowerDiagnosticUiState.Interrupted -> Text(if (english) "The previous read was interrupted. Retry manually." else "上次读取已中断，请手动重试。")
            }
            val busy = state is PowerDiagnosticUiState.Collecting || state == PowerDiagnosticUiState.Importing
            TextButton(onClick = onOpenVerification, modifier = Modifier.fillMaxWidth()) {
                Text(if (english) "Local verification records" else "本地验证记录")
            }
            PowerDiagnosticEntryPoints.forRootAvailability(rootAvailable).forEach { entryPoint ->
                when (entryPoint) {
                    PowerDiagnosticEntryPoint.IMPORT_BUGREPORT -> Button(
                        onClick = onImportBugReport,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (snapshot == null) R.string.import_system_report else R.string.reimport_system_report))
                    }
                    PowerDiagnosticEntryPoint.ROOT_READ_ONLY -> Button(
                        onClick = onCollect,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (snapshot == null) R.string.start_root_diagnostic else R.string.rerun_root_diagnostic))
                    }
                }
            }
            TextButton(onClick = { showImportHelp = !showImportHelp }) {
                Text(stringResource(if (showImportHelp) R.string.collapse_generation_steps else R.string.how_generate_system_report))
            }
            if (showImportHelp) {
                BugReportImportHelpContent()
            }
            TextButton(onClick = { showObservation = !showObservation }) {
                Text(stringResource(if (showObservation) R.string.collapse_battery_measurement else R.string.rough_battery_measurement))
            }
            if (showObservation) {
                Text(
                    if (english) "This compares only the starting and ending charge. It cannot identify a specific app; a system report is the main attribution source for standard users." else "只比较起止电量，不能定位具体耗电应用；系统报告才是普通用户归因的主入口。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                when {
                    observationStart != null -> {
                        Text(
                            if (english) {
                                "Observing: ${observationStart.levelPercent}% · ${SampleTime.format(observationStart.sampledAtInstant)}\n" +
                                    "You can leave APA. Keep the phone unplugged and return after at least 30 minutes."
                            } else {
                                "观察中：${observationStart.levelPercent}% · ${SampleTime.format(observationStart.sampledAtInstant)}\n" +
                                    "可以退出 APA；保持不充电，至少 30 分钟后回来结束。"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showFinishConfirmation = true }) { Text(if (english) "Finish and calculate" else "结束观察并计算") }
                            TextButton(onClick = onClearObservation) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                    observationResult != null -> {
                        val rate = observationResult.drainPercentPerHour
                        Text(
                            if (rate != null) {
                                val quality = observationResult.measurementQuality
                                    ?.let { BatteryObservationAnalyzer.qualityLabel(it, language) }
                                    ?: if (english) "Unknown" else "未知"
                                if (english) {
                                    "Down ${observationResult.dropPercent} percentage points · Average ${"%.2f".format(java.util.Locale.US, rate)}%/hour · Reliability: $quality"
                                } else {
                                    "下降 ${observationResult.dropPercent} 个百分点 · 平均 ${"%.2f".format(java.util.Locale.US, rate)}%/小时 · 测量可靠性：$quality"
                                }
                            } else {
                                BatteryObservationAnalyzer.explanation(observationResult.validity, language)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStartObservation) { Text(if (english) "Start again" else "重新开始") }
                            TextButton(onClick = onClearObservation) { Text(stringResource(R.string.common_clear)) }
                        }
                    }
                    else -> Button(onClick = onStartObservation, modifier = Modifier.fillMaxWidth()) {
                        Text(if (english) "Start rough measurement" else "开始粗略测量")
                    }
                }
                observationNotice?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            snapshot?.let { report ->
                if (showDiagnosticDetails) {
                    PowerDiagnosticReportCards(report, onCopyPackage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleSelected) {
                        Text(stringResource(if (selected) R.string.attached_diagnostic_summary else R.string.attach_diagnostic_summary))
                    }
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.common_remove)) }
                }
            }
        }
    }
}

@Composable
internal fun PowerDiagnosticReportCards(report: PowerDiagnosticSnapshot, onCopyPackage: (String) -> Unit) {
    val language = LocalAppLanguage.current
    val english = language == AppLanguage.EN
    val display = PowerDiagnosticDisplayModel.from(report, language)
    var expanded by rememberSaveable(report.sampledAtInstant.toString()) { mutableStateOf(false) }
    DiagnosticSection(if (english) "Conclusion" else "本地结论") {
        Text(display.conclusion)
        Text(if (english) "Sample: ${display.sampleTime} · Collection: ${display.collectionDurationMillis} ms"
            else "采样：${display.sampleTime} · 采集用时：${display.collectionDurationMillis} ms",
            style = MaterialTheme.typography.labelSmall)
        Text(if (english) "Source: ${display.sourceLabel}" else "来源：${display.sourceLabel}",
            style = MaterialTheme.typography.labelMedium)
        if (display.sourceResults.isNotEmpty()) {
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                display.sourceResults.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    DiagnosticSection(if (english) "Supporting evidence" else "支持证据") {
        CandidateSection(if (english) "Total consumption" else "耗电总量排行", display.totalConsumption,
            if (english) "No comparable app-consumption data." else "没有可比较的应用耗电量数据。", false, onCopyPackage)
        CandidateSection(if (english) "Background anomaly suspects" else "后台异常嫌疑", display.backgroundSuspects,
            if (report.localVerdict?.type != PowerVerdictType.SUFFICIENT) {
                if (english) "Evidence is insufficient to assess background anomalies."
                else "证据不足，尚不能判断后台是否异常。"
            } else if (english) "No app reached the background-anomaly threshold in the available evidence; high consumption does not prove an anomaly."
            else "已获取的证据中没有达到后台异常阈值的应用；耗电多不等于后台异常。", true, onCopyPackage)
    }
    DiagnosticSection(if (english) "Next step" else "下一步") {
        Text(display.nextStepDisplay)
        Text(if (english) "Advice is manual. APA does not change Scene policies or freeze apps."
            else "建议需手动执行；APA 不会更改 Scene 策略或冻结应用。", style = MaterialTheme.typography.bodySmall)
    }
    DiagnosticSection(if (english) "Evidence coverage and limits" else "证据覆盖与限制") {
        if (display.limits.isEmpty()) {
            Text(if (english) "No additional coverage limits were reported; this does not prove complete coverage."
                else "未报告额外覆盖限制；这不代表证据覆盖完整。", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                display.limits.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (english) { if (expanded) "Collapse additional evidence" else "Show additional evidence" }
            else if (expanded) "收起补充证据" else "展开补充证据")
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            report.system.batteryDrainWindow?.let { window ->
                Text(if (english) "Battery window evidence" else "电量窗口证据", style = MaterialTheme.typography.titleSmall)
                Text(if (english) "Window duration: ${window.durationMillis / 60_000} min"
                    else "窗口时长：${window.durationMillis / 60_000} 分钟", style = MaterialTheme.typography.bodySmall)
                Text(if (english) "Window drain: ${window.drainMah} mAh · Capacity: ${window.capacityMah} mAh"
                    else "窗口耗电：${window.drainMah} mAh · 容量：${window.capacityMah} mAh", style = MaterialTheme.typography.bodySmall)
                Text(if (english) { if (window.usesActualDrain) "Drain uses the recorded charge decrease." else "Drain is a system estimate." }
                    else if (window.usesActualDrain) "耗电量采用记录的电量下降值。" else "耗电量采用系统估算值。", style = MaterialTheme.typography.bodySmall)
            }
            CandidateSection(if (english) "Background consumption" else "后台耗电排行", display.backgroundConsumption,
                if (english) "No comparable background-consumption data." else "没有可比较的应用后台耗电量。", false, onCopyPackage)
            CandidateSection(if (english) "Scheduling observations" else "系统调度观察", display.schedulingObservations,
                if (english) "No cumulative scheduling data parsed." else "没有解析到应用调度累计数据。", false, onCopyPackage)
            Text(if (english) "Cumulative scheduling counts can cover another period; they do not establish a same-window anomaly."
                else "调度累计值可能来自另一统计周期，不能据此认定本次窗口的后台异常。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DiagnosticSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun CandidateSection(title: String, candidates: List<RankedPowerCandidate>, emptyText: String,
    showAction: Boolean, onCopyPackage: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (candidates.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
    else candidates.forEach { PowerCandidateCard(it, showAction, onCopyPackage) }
}

@Composable
internal fun BugReportImportHelpContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "部分小米 / Redmi / POCO：在系统拨号盘输入 *#*#284#*#*，等待系统生成 Bug Report ZIP；再回到 APA 点“导入系统报告”选择该文件。不同系统版本的保存位置可能不同。",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "其他 Android：先开启开发者选项，在设置中搜索“获取错误报告”或“提交错误报告”，选择互动式报告；生成完成后回到 APA 导入 ZIP。",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "系统报告可能包含账号、通知或设备标识等敏感内容。请只使用自己的报告，不要公开上传；APA 只在本机提取耗电诊断白名单和硬件供应商字段，原始文件不会发送给模型。",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PowerCandidateCard(
    candidate: RankedPowerCandidate,
    showAction: Boolean,
    onCopyPackage: (String) -> Unit
) {
    val language = LocalAppLanguage.current
    val english = language == AppLanguage.EN
    val name = if (english) {
        when {
            candidate.packageNames.size > 1 -> {
                val group = if ((candidate.uid ?: Int.MAX_VALUE) < 10_000 || candidate.packageNames.any {
                    it == "android" || it.startsWith("com.android.") || it.startsWith("com.miui.")
                }) "System component group" else "Shared UID group"
                "$group: ${candidate.displayNames.ifEmpty { candidate.packageNames }.joinToString()}"
            }
            candidate.packageNames.isEmpty() -> candidate.uid?.let { "UID $it" } ?: "Unknown app"
            else -> candidate.displayNames.firstOrNull()?.let { "$it (${candidate.packageNames.single()})" }
                ?: candidate.packageNames.single()
        }
    } else candidate.userFacingName()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            if (candidate.packageNames.isNotEmpty()) {
                Text((if (english) "Packages: " else "包名：") + candidate.packageNames.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
            Text((if (english) "Facts: " else "事实：") + candidate.facts.joinToString(if (english) "; " else "；") { diagnosticDisplayText(it, language) })
            Text((if (english) "Interpretation: " else "解释：") + diagnosticDisplayText(candidate.reason, language))
            if (english && (candidate.facts + listOfNotNull(candidate.reason, candidate.sceneAction, candidate.risk,
                    candidate.rollback, candidate.retest)).any { text -> diagnosticDisplayText(text, language).any { it in '\u4E00'..'\u9FFF' } }) {
                Text("Unrecognized evidence or advice is shown in its original language.", style = MaterialTheme.typography.labelSmall)
            }
            Text(if (english) "Confidence: ${candidate.confidence.englishLabel()} · Maximum advice: ${candidate.maxAdviceLevel.englishLabel()}" else "置信度：${candidate.confidence.label()} · 最高建议：${candidate.maxAdviceLevel.label()}")
            if (showAction) {
                candidate.sceneAction?.let { Text((if (english) "Manual Scene action: " else "Scene 手动操作：") + diagnosticDisplayText(it, language)) }
                candidate.risk?.let { Text((if (english) "Risk: " else "风险：") + diagnosticDisplayText(it, language), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.rollback?.let { Text((if (english) "Rollback: " else "回退：") + diagnosticDisplayText(it, language), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.retest?.let { Text((if (english) "Retest: " else "复测：") + diagnosticDisplayText(it, language), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            candidate.packageCopyText()?.let { copyText ->
                TextButton(onClick = { onCopyPackage(copyText) }) {
                    Text(if (english) {
                        if (candidate.packageNames.size > 1) "Copy all package names" else "Copy package $copyText"
                    } else if (candidate.packageNames.size > 1) "复制全部包名" else "复制包名 $copyText")
                }
            }
        }
    }
}

private fun DiagnosticConfidence.label() = when (this) {
    DiagnosticConfidence.LOW -> "低"
    DiagnosticConfidence.MEDIUM -> "中"
    DiagnosticConfidence.HIGH -> "高"
}

private fun AdviceLevel.label() = when (this) {
    AdviceLevel.OBSERVE -> "观察"
    AdviceLevel.RESTRICT -> "限制"
    AdviceLevel.FREEZE_CANDIDATE -> "冻结候选"
}

private fun DiagnosticConfidence.englishLabel() = when (this) {
    DiagnosticConfidence.LOW -> "Low"
    DiagnosticConfidence.MEDIUM -> "Medium"
    DiagnosticConfidence.HIGH -> "High"
}

private fun AdviceLevel.englishLabel() = when (this) {
    AdviceLevel.OBSERVE -> "Observe"
    AdviceLevel.RESTRICT -> "Restrict"
    AdviceLevel.FREEZE_CANDIDATE -> "Freeze candidate"
}
