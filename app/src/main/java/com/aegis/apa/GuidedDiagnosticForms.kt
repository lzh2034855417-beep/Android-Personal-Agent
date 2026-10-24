package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import java.time.Instant

@Composable
internal fun guidedText(zh: String, en: String) = GuidedDiagnosticCopy.choose(LocalAppLanguage.current, zh, en)

@Composable
internal fun GuidedAnswersForm(session: GuidedDiagnosticSession, onEvent: (GuidedUiEvent) -> Unit) {
    val answers = session.answers
    var start by remember(session.id) { mutableStateOf(answers.startedAt?.toString().orEmpty()) }
    var end by remember(session.id) { mutableStateOf(answers.endedAt?.toString().orEmpty()) }
    fun parsed(value: String) = if (value.isBlank()) null else runCatching { Instant.parse(value.trim()) }.getOrNull()
    val valid = (start.isBlank() || parsed(start) != null) && (end.isBlank() || parsed(end) != null)
    fun edit(value: GuidedAnswers) = onEvent(GuidedUiEvent.UpdateAnswers(session.key, value.copy(reviewed = false)))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(guidedText("用户声明：所有选项初始为不确定。确认不确定也是有效回答；不是系统观测。", "User declarations: all choices initially are unsure. Reviewing unknown is valid; these are not system observations."))
        OutlinedTextField(start, { start = it; editGuidedTimeAnswer(it) { value -> edit(answers.copy(startedAt = value)) } },
            label = { Text(guidedText("问题开始时间（可留空）", "Problem start (may be blank)")) },
            supportingText = { Text(guidedText("UTC 格式，例如 2026-10-01T10:00:00Z", "UTC format, e.g. 2026-10-01T10:00:00Z")) },
            modifier = Modifier.fillMaxWidth().testTag("guided-start-time"), isError = start.isNotBlank() && parsed(start) == null)
        OutlinedTextField(end, { end = it; editGuidedTimeAnswer(it) { value -> edit(answers.copy(endedAt = value)) } },
            label = { Text(guidedText("问题结束时间（可留空）", "Problem end (may be blank)")) },
            modifier = Modifier.fillMaxWidth().testTag("guided-end-time"), isError = end.isNotBlank() && parsed(end) == null)
        GuidedDeclaration(guidedText("问题期间是否充电", "Charging during the problem"), "charging", answers.charging) { edit(answers.copy(charging = it)) }
        if (session.scenario == GuidedScenario.IDLE_DRAIN) {
            GuidedDeclaration(guidedText("主要处于息屏闲置", "Mostly screen-off and idle"), "mostlyIdle", answers.mostlyIdle) { edit(answers.copy(mostlyIdle = it)) }
            GuidedDeclaration(guidedText("是否跨重启", "Did the interval cross a reboot"), "rebooted", answers.rebooted) { edit(answers.copy(rebooted = it)) }
        } else {
            GuidedDeclaration(guidedText("现在是否仍在发热", "Is it still hot now"), "stillHot", answers.stillHot) { edit(answers.copy(stillHot = it)) }
            Text(guidedText("发生时的主要任务（用户声明）", "Main activity at the time (user declaration)"))
            GuidedActivity.entries.forEach { activity ->
                FilterChip(selected = answers.activity == activity, onClick = { edit(answers.copy(activity = activity)) },
                    modifier = Modifier.testTag("guided-activity-${activity.name}"), label = { Text(guidedActivityLabel(activity)) })
            }
        }
        OutlinedTextField(answers.note, { edit(answers.copy(note = it)) }, label = { Text(guidedText("可选本地备注（不自动发送）", "Optional local notes (not sent automatically)")) },
            supportingText = { Text("${answers.note.codePointCount(0, answers.note.length)}/500") }, maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("guided-note"))
        Button(onClick = { onEvent(GuidedUiEvent.UpdateAnswers(session.key, answers.copy(startedAt = parsed(start), endedAt = parsed(end), reviewed = true))) },
            enabled = valid, modifier = Modifier.fillMaxWidth().testTag("guided-confirm-answers")) {
            Text(guidedText("明确确认这些声明并本地评估", "Confirm these declarations and evaluate locally"))
        }
    }
}

@Composable
private fun GuidedDeclaration(label: String, tag: String, value: VerificationConfirmation, onChange: (VerificationConfirmation) -> Unit) {
    Column {
        Text(label)
        VerificationConfirmation.entries.forEach { option -> FilterChip(selected = value == option,
            onClick = { onChange(option) }, modifier = Modifier.testTag("guided-answer-$tag-${option.wireId}"),
            label = { Text(GuidedDiagnosticCopy.status(option.name, LocalAppLanguage.current)) }) }
    }
}

@Composable
private fun guidedActivityLabel(activity: GuidedActivity) = when (activity) {
    GuidedActivity.UNKNOWN -> guidedText("不确定", "Unsure")
    GuidedActivity.GAME -> guidedText("游戏", "Gaming")
    GuidedActivity.VIDEO -> guidedText("视频", "Video")
    GuidedActivity.NAVIGATION -> guidedText("导航", "Navigation")
    GuidedActivity.CHARGING_USE -> guidedText("充电时使用", "Use while charging")
    GuidedActivity.OTHER -> guidedText("其他", "Other")
}

@Composable
internal fun GuidedSamplePreviewForm(preview: GuidedSamplePreview, confirmed: List<GuidedSample>, onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit) {
    // This state belongs to this exact issued receipt, including candidate revision and answer key.
    var reviewed by remember(preview) { mutableStateOf(false) }
    var replacement by remember(preview) { mutableStateOf<String?>(null) }
    val replacing = confirmed.size >= 2 && confirmed.none { it.id == preview.sample.id }
    val language = LocalAppLanguage.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(guidedText("预览待确认的本地摘要", "Preview the local summary to confirm")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).imePadding().verticalScroll(rememberScrollState())
                .testTag("guided-preview-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(guidedText("此摘要尚未加入会话；当前声明与证据一起确认。最多两份、二十个应用组、48 KiB。", "This summary has not been added; confirm its evidence with the current declarations. At most two samples, twenty app groups and 48 KiB each."))
                if (replacing) {
                    Text(guidedText("明确选择要替换的样本", "Explicitly select the sample to replace"))
                    confirmed.forEach { sample -> FilterChip(selected = replacement == sample.id,
                        onClick = { replacement = sample.id }, modifier = Modifier.testTag("guided-replace-${sample.id}"),
                        label = { Text(sample.id) }) }
                }
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
                    .toggleable(value = reviewed, role = Role.Checkbox, onValueChange = { reviewed = it })
                    .testTag("guided-preview-reviewed")) {
                    Checkbox(checked = reviewed, onCheckedChange = null)
                    Text(guidedText("我已查看并确认此摘要与声明；替换时移除所选旧样本。", "I reviewed and confirm this summary and declarations; replacement removes the selected old sample."))
                }
                Text(guidedText("样本编号：", "Sample ID: ") + preview.sample.id)
                Text(guidedText("当前用户声明：", "Current user declarations: ") + guidedAnswersDescription(preview.sample.answersAtConfirmation, language))
                Text(guidedText("省略应用组：", "Omitted app groups: ") + preview.sample.omittedAppGroupCount)
                Text(guidedText("报告输入：", "Report input: ") + when (preview.sample.diagnosticInputSource) {
                    DiagnosticInputSource.BUGREPORT -> guidedText("导入 Bug Report", "Imported Bug Report")
                    DiagnosticInputSource.ROOT -> guidedText("本机 Root 只读采集", "Local Root read-only collection")
                    null -> guidedText("无报告 / 未知", "No report / unknown")
                })
                Text(guidedText("报告设备归属：", "Report device ownership: ") + when (preview.sample.diagnosticDevice?.ownership) {
                    VerificationOwnership.LOCAL_COLLECTION -> guidedText("本机采集", "Local collection")
                    VerificationOwnership.USER_CONFIRMED -> guidedText("用户已确认", "User confirmed")
                    else -> guidedText("未知；可能来自过去或其他设备", "Unknown; may be historical or from another device")
                })
                preview.sample.sources.forEach { (source, status) -> Text(GuidedDiagnosticCopy.source(source, language) + ": " + GuidedDiagnosticCopy.status(status.name, language)) }
                preview.sample.evidence.forEach { fact -> Text(GuidedDiagnosticCopy.fact(fact, language)) }
                preview.sample.gaps.forEach { Text(GuidedDiagnosticCopy.text(it.id, language)) }
            }
        }, confirmButton = {
            TextButton(onClick = { onConfirm(replacement) }, enabled = reviewed && (!replacing || replacement != null),
                modifier = Modifier.testTag("guided-confirm-sample")) { Text(guidedText("确认样本 / 所选替换", "Confirm sample / selected replacement")) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(guidedText("取消", "Cancel")) } })
}

internal fun guidedAnswersDescription(answers: GuidedAnswers, language: AppLanguage): String {
    fun status(value: VerificationConfirmation) = GuidedDiagnosticCopy.status(value.name, language)
    val activity = when (answers.activity) {
        GuidedActivity.UNKNOWN -> "不确定" to "Unsure"; GuidedActivity.GAME -> "游戏" to "Gaming"
        GuidedActivity.VIDEO -> "视频" to "Video"; GuidedActivity.NAVIGATION -> "导航" to "Navigation"
        GuidedActivity.CHARGING_USE -> "充电时使用" to "Use while charging"; GuidedActivity.OTHER -> "其他" to "Other"
    }.let { GuidedDiagnosticCopy.choose(language, it.first, it.second) }
    val unknown = GuidedDiagnosticCopy.status("UNKNOWN", language)
    return GuidedDiagnosticCopy.choose(language,
        "${answers.startedAt ?: unknown} → ${answers.endedAt ?: unknown}\n充电 ${status(answers.charging)} · 闲置 ${status(answers.mostlyIdle)} · 重启 ${status(answers.rebooted)} · 仍热 ${status(answers.stillHot)} · 任务 $activity\n备注：${answers.note}",
        "${answers.startedAt ?: unknown} → ${answers.endedAt ?: unknown}\nCharging ${status(answers.charging)} · idle ${status(answers.mostlyIdle)} · reboot ${status(answers.rebooted)} · still hot ${status(answers.stillHot)} · activity $activity\nNotes: ${answers.note}")
}
