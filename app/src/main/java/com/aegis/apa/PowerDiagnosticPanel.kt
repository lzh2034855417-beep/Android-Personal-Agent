package com.aegis.apa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.agent.PowerDiagnosticReportBuilder
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.SampleTime
import com.aegis.apa.model.packageCopyText
import com.aegis.apa.model.packageSummary
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
    modifier: Modifier = Modifier
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
                    if (english) {
                        Text(
                            PowerDiagnosticReportBuilder.build(
                                snapshot = report,
                                includeAdvancedActions = true,
                                language = AppLanguage.EN
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                    val display = PowerDiagnosticDisplayModel.from(report)
                    Text(if (english) "Sample: ${report.sampledAtInstant} · ${report.collectionDurationMillis} ms" else "采样：${report.sampledAtInstant} · ${report.collectionDurationMillis} ms", style = MaterialTheme.typography.labelSmall)
                    Text(if (english) "Source: ${display.sourceLabel}" else "来源：${display.sourceLabel}", style = MaterialTheme.typography.labelMedium)
                    val unavailable = report.sources.values.filter { it.status != DiagnosticSourceStatus.AVAILABLE }
                    if (unavailable.isNotEmpty()) {
                        Text(
                            "未完整获取：" + unavailable.joinToString { "${it.source}(${it.status.name})" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(if (english) "On-device verdict" else "本地结论（不联网也可用）", style = MaterialTheme.typography.titleSmall)
                    display.nextStep?.let { Text(if (english) "Insufficient evidence: $it" else "证据不足：$it") }
                    Text(if (english) "Total consumption" else "耗电总量排行", style = MaterialTheme.typography.titleSmall)
                    if (display.totalConsumption.isEmpty()) {
                        Text(if (english) "No comparable app-consumption data." else "没有可比较的应用耗电量数据。")
                    } else {
                        display.totalConsumption.forEach { candidate ->
                            PowerCandidateCard(candidate, showAction = false, onCopyPackage)
                        }
                    }
                    Text(if (english) "Background anomaly suspects" else "后台异常嫌疑", style = MaterialTheme.typography.titleSmall)
                    if (display.backgroundSuspects.isEmpty()) {
                        Text(if (english) "No app reached the background-anomaly threshold; high consumption is not proof of an anomaly." else "没有达到后台异常阈值的应用；耗电多不等于后台异常。")
                    } else {
                        display.backgroundSuspects.forEach { candidate ->
                            PowerCandidateCard(candidate, showAction = true, onCopyPackage)
                        }
                    }
                    if (display.limits.isNotEmpty()) {
                        Text(
                            "证据覆盖与限制：${display.limits.joinToString("；")}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    }
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
    val english = LocalAppLanguage.current == AppLanguage.EN
    val name = candidate.userFacingName()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            candidate.packageSummary()?.let { summary ->
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (english) "Facts: ${candidate.facts.joinToString("; ")}" else "事实：${candidate.facts.joinToString("；")}")
            Text(if (english) "Interpretation: ${candidate.reason}" else "解释：${candidate.reason}")
            Text(if (english) "Confidence: ${candidate.confidence.name.lowercase()} · Maximum advice: ${candidate.maxAdviceLevel.name.lowercase().replace('_', ' ')}" else "置信度：${candidate.confidence.label()} · 最高建议：${candidate.maxAdviceLevel.label()}")
            if (showAction) {
                candidate.sceneAction?.let { Text("Scene：$it") }
                candidate.risk?.let { Text(if (english) "Risk: $it" else "风险：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.rollback?.let { Text(if (english) "Rollback: $it" else "回退：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.retest?.let { Text(if (english) "Retest: $it" else "复测：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
