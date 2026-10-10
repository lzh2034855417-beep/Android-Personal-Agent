package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.VerificationComparisonEngine
import com.aegis.apa.agent.VerificationResultFormatter
import com.aegis.apa.agent.VerificationSummaryProjector
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.model.*
import java.time.Instant

/** Metadata comes only from the diagnostic pipeline. Never substitute a report/import timestamp. */
internal fun verificationSummary(snapshot: PowerDiagnosticSnapshot, target: VerificationTarget,
    context: VerificationSampleContext): DiagnosticVerificationSummary? = VerificationSummaryProjector.project(
    snapshot, snapshot.verificationMetadata ?: DiagnosticVerificationMetadata(null, null, emptyMap()), context, target)

internal fun verificationInitialContext(snapshot: PowerDiagnosticSnapshot) = VerificationSampleContext(
    snapshot.verificationMetadata?.device?.copy(ownership = VerificationOwnership.UNKNOWN)
        ?: VerificationDeviceContext(null, null, null, null, VerificationOwnership.UNKNOWN),
    VerificationScenario.OTHER, "", VerificationConfirmation.UNKNOWN)

@Composable
internal fun VerificationSampleForm(snapshot: PowerDiagnosticSnapshot, record: VerificationRecord?, enabled: Boolean,
    onDismiss: () -> Unit, onSave: (VerificationTarget, VerificationSampleContext, String) -> Unit) =
    VerificationSampleForm(snapshot, record, enabled, onDismiss, onSave, null)

@Composable
internal fun VerificationSampleForm(snapshot: PowerDiagnosticSnapshot, record: VerificationRecord?, enabled: Boolean,
    onDismiss: () -> Unit, onSave: (VerificationTarget, VerificationSampleContext, String) -> Unit,
    fixedTarget: VerificationTarget?) {
    var title by remember { mutableStateOf(record?.title ?: "") }
    var targetIndex by remember(snapshot) { mutableIntStateOf(0) }
    var scenario by remember { mutableStateOf(VerificationScenario.OTHER) }
    var note by remember { mutableStateOf("") }
    var ownership by remember { mutableStateOf(VerificationConfirmation.UNKNOWN) }
    var noCharging by remember { mutableStateOf(VerificationConfirmation.UNKNOWN) }
    var preview by remember(snapshot) { mutableStateOf<DiagnosticVerificationSummary?>(null) }
    val groups = remember(snapshot) { snapshot.apps.filter { it.packageNames.isNotEmpty() }
        .sortedByDescending { it.estimatedPowerMah ?: -1.0 }.map { VerificationTarget.AppGroup(it.packageNames) }.distinct().take(20) }
    val target = record?.target ?: fixedTarget ?: if (targetIndex == 0) VerificationTarget.Overall else groups[targetIndex - 1]
    val context = verificationInitialContext(snapshot).copy(scenario = scenario, scenarioNote = note, noCharging = noCharging,
        device = verificationInitialContext(snapshot).device.copy(ownership =
            if (ownership == VerificationConfirmation.YES) VerificationOwnership.USER_CONFIRMED else VerificationOwnership.UNKNOWN))
    val projected = preview
    val language = LocalAppLanguage.current
    val previewText = remember(projected, record, language) {
        projected?.let { summary ->
            val previewRecord = if (record != null) record.copy(retest = summary)
                else VerificationRecord("preview", 1, 1, "preview", summary.generatedAt, summary.generatedAt, 0,
                    title, VerificationState.BASELINE_SAVED, target, summary, null, null, null)
            VerificationResultFormatter.format(previewRecord, VerificationComparisonEngine.compare(previewRecord), language).split("\n\n")
        }.orEmpty()
    }
    val formMaxWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = formMaxWidth),
        title = { Text(if (record == null) verificationText("保存基线", "Save baseline") else verificationText("保存复测", "Save retest")) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).imePadding().testTag("verification-form-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(verificationText("在本机加密保存：来源、非唯一型号/系统信息、指标/窗口、最多 20 个耗电排序应用组（含应用目标及另外最多 19 组）、您的确认和备注。最多 20 条，每条 48 KiB / 全归档 1 MiB，创建后保留 30 天。",
                    "Encrypted on this phone: source, non-unique model/system context, metrics/windows, up to 20 ranked app groups (including the app target plus up to 19 others), your confirmations and notes. Up to 20 records, 48 KiB each / 1 MiB archive, retained for 30 days from creation.")) }
                item { Text(verificationText("不保存原报告、日志、Key、账号、唯一设备标识、全量应用列表或报告 URI。不自动联网或导出。包名、时间和备注仍可能敏感。",
                    "No raw report, logs, Key, account, unique device ID, full app list, or report URI is saved. No automatic network or export. Packages, times, and notes can still be sensitive.")) }
                item { Text(verificationText("统计窗口未知：无法计算调整效果。确认归属或场景不能补造窗口；导入时间不是报告时间。",
                    "Unknown statistics windows prevent calculating adjustment effects. Ownership or scenario confirmations cannot create windows; import time is not report time.")) }
                if (fixedTarget != null) item { Text(verificationText("本向导仅验证耗电，不验证发热。目标来自当前本地证据图：", "This guide verifies power only, not heat. Target from the current local evidence graph: ") +
                    (fixedTarget as? VerificationTarget.AppGroup)?.packageNames?.joinToString().orEmpty()) }
                if (projected == null) {
                    if (record == null) {
                        item { VerificationTextField(title, { title = it }, verificationText("标题", "Title"), 80, "verification-title") }
                        if (fixedTarget == null) {
                            item { Text(verificationText("选择验证目标", "Choose target")) }
                            item { FilterChip(selected = targetIndex == 0, onClick = { targetIndex = 0 }, label = {
                                Text(verificationText("整体耗电", "Overall power")) }) }
                            items(groups.size) { index -> FilterChip(selected = targetIndex == index + 1,
                                onClick = { targetIndex = index + 1 }, label = { Text(groups[index].packageNames.joinToString()) }) }
                        }
                    }
                    item { Text(verificationText("使用场景（同枚举不证明相似）", "Scenario (same category does not prove similarity)")) }
                    items(VerificationScenario.entries) { value -> FilterChip(selected = scenario == value,
                        onClick = { scenario = value }, label = { Text(verificationScenarioLabel(value)) }) }
                    item { VerificationTextField(note, { note = it }, verificationText("使用场景备注", "Scenario notes"), 500, "verification-scenario-note") }
                    if (snapshot.inputSource == DiagnosticInputSource.BUGREPORT) item {
                        VerificationConfirmationField(verificationText("导入报告来自我当前这台手机", "Imported report is from this phone"), ownership, { ownership = it }, "ownership")
                    } else item { Text(verificationText("Root 归属来自本机采集链路。", "Root ownership comes from the local collection path.")) }
                    item { VerificationConfirmationField(verificationText("这段样本期间没有充电", "No charging during this sample"), noCharging, { noCharging = it }, "charging") }
                    item { Button(onClick = { preview = verificationSummary(snapshot, target, context) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("verification-preview")) { Text(verificationText("预览将保存的摘要", "Preview retained summary")) } }
                } else {
                    item { Text(verificationText("摘要生成时间（不是报告时间）：", "Summary generation time (not report time): ") + projected.generatedAt)
                        Text(verificationText("报告时间：", "Report time: ") + (projected.reportedAt?.toString() ?: verificationText("未知", "unknown")))
                        Text(verificationText("场景备注：", "Scenario notes: ") + projected.context.scenarioNote)
                    }
                    item {
                        val device = projected.context.device
                        val unknown = verificationText("未知", "unknown")
                        Text(verificationText("设备上下文：", "Device context: ") +
                            listOf(device.manufacturer ?: unknown, device.modelCode ?: unknown,
                                device.apiLevel?.toString() ?: unknown, device.buildVersion ?: unknown).joinToString(" / "))
                        Text(verificationText("场景：", "Scenario: ") + verificationScenarioLabel(projected.context.scenario))
                        Text(verificationText("无充电声明：", "No-charging declaration: ") + verificationConfirmationLabel(projected.context.noCharging))
                    }
                    items(previewText) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    item { Text(if (record?.retest != null) verificationText("保存将替换原复测摘要。", "Saving replaces the previous retest summary.")
                        else verificationText("不可比时也可保存原值；不会生成节电结论。", "Raw values can be saved when incomparable; no savings conclusion is generated.")) }
                }
            }
        },
        confirmButton = {
            if (projected != null) TextButton(onClick = {
                onSave(target, projected.context, title.ifBlank { verificationDefaultTitle(language == com.aegis.apa.localization.AppLanguage.EN) })
            }, enabled = enabled, modifier = Modifier.testTag("verification-save-sample")) { Text(verificationText("保存摘要", "Save summary")) }
        },
        dismissButton = { TextButton(onClick = { if (projected == null) onDismiss() else preview = null }) {
            Text(if (projected == null) verificationText("取消", "Cancel") else verificationText("返回修改", "Back to edit")) } })
}

private fun verificationDefaultTitle(english: Boolean) = if (english) "Diagnostic verification" else "诊断验证"

@Composable
internal fun VerificationChangeForm(record: VerificationRecord, enabled: Boolean, onDismiss: () -> Unit,
    onSave: (VerificationChange) -> Unit) {
    var description by remember { mutableStateOf(record.change?.description ?: "") }
    var rollback by remember { mutableStateOf(record.change?.rollback ?: "") }
    var sideEffects by remember { mutableStateOf(record.change?.sideEffects ?: "") }
    // A newly confirmed adjustment starts with unknown declarations, even when editing an old one.
    var similar by remember { mutableStateOf(VerificationConfirmation.UNKNOWN) }
    var other by remember { mutableStateOf(VerificationConfirmation.UNKNOWN) }
    var invalidate by remember { mutableStateOf(false) }
    val change = VerificationChange(description, rollback, Instant.EPOCH, similar, other, sideEffects)
    val formMaxWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = formMaxWidth),
        title = { Text(verificationText("记录手动调整", "Record manual adjustment")) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).imePadding().testTag("verification-form-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(verificationText("只记录一项调整。APA 不读取、验证或执行设置；确认时间在点击后由本机控制器记录。",
                    "Record one adjustment. APA does not read, verify, or perform settings; the local controller records the time when you confirm.")) }
                item { VerificationTextField(description, { description = it }, verificationText("调整说明", "Adjustment description"), 500, "verification-description") }
                item { VerificationTextField(rollback, { rollback = it }, verificationText("原设置 / 回退方式", "Original setting / rollback"), 500, "verification-rollback") }
                item { VerificationConfirmationField(verificationText("前后使用场景相似", "Before and after scenarios are similar"), similar, { similar = it }, "similar") }
                item { VerificationConfirmationField(verificationText("没有其他调整", "No other adjustments"), other, { other = it }, "other") }
                item { VerificationTextField(sideEffects, { sideEffects = it }, verificationText("副作用备注", "Side effects"), 500, "verification-side-effects") }
                item { Button(onClick = { if (record.retest != null) invalidate = true else onSave(change) },
                    enabled = enabled && description.isNotBlank() && rollback.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("verification-confirm-change")) { Text(verificationText("已手动调整，确认记录", "I adjusted manually; confirm record")) } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(verificationText("取消", "Cancel")) } })
    if (invalidate) AlertDialog(onDismissRequest = { invalidate = false },
        title = { Text(verificationText("确认清除旧复测？", "Remove old retest?")) },
        text = { Text(verificationText("这会清除已保存的复测及旧比较结果，并重新记录调整确认时间。", "This removes the saved retest and old comparison and records a new adjustment confirmation time.")) },
        confirmButton = { TextButton(onClick = { onSave(change); invalidate = false }, enabled = enabled,
            modifier = Modifier.testTag("verification-confirm-invalidate")) { Text(verificationText("清复测并保存调整", "Remove retest and save adjustment")) } },
        dismissButton = { TextButton(onClick = { invalidate = false }) { Text(verificationText("取消", "Cancel")) } })
}

@Composable
private fun VerificationTextField(value: String, onValueChange: (String) -> Unit, label: String, limit: Int, tag: String) {
    OutlinedTextField(value, { onValueChange(boundedUnicodeText(it, limit)) }, modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) }, supportingText = { Text("${value.codePointCount(0, value.length)}/$limit") }, maxLines = 5)
}

@Composable
private fun VerificationConfirmationField(label: String, value: VerificationConfirmation,
    onChange: (VerificationConfirmation) -> Unit, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        // Vertical choices remain reachable at 320dp and 1.5x font scale.
        VerificationConfirmation.entries.forEach { option ->
            FilterChip(selected = value == option, onClick = { onChange(option) },
                modifier = Modifier.testTag("verification-$tag-${option.wireId}"), label = { Text(verificationConfirmationLabel(option)) })
        }
    }
}

@Composable
private fun verificationConfirmationLabel(value: VerificationConfirmation) = when (value) {
    VerificationConfirmation.YES -> verificationText("是（用户声明）", "Yes (user declaration)")
    VerificationConfirmation.NO -> verificationText("否", "No")
    VerificationConfirmation.UNKNOWN -> verificationText("未知 / 未确认", "Unknown / unconfirmed")
}

@Composable
private fun verificationScenarioLabel(scenario: VerificationScenario) = when (scenario) {
    VerificationScenario.IDLE -> verificationText("待机", "Idle")
    VerificationScenario.LIGHT -> verificationText("轻度交互", "Light interaction")
    VerificationScenario.GAME -> verificationText("游戏", "Gaming")
    VerificationScenario.VIDEO -> verificationText("视频", "Video")
    VerificationScenario.OTHER -> verificationText("其他 / 未指定", "Other / unspecified")
}
