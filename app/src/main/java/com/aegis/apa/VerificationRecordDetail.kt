package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.VerificationComparisonEngine
import com.aegis.apa.agent.VerificationResultFormatter
import com.aegis.apa.agent.VerificationExplanationUiState
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.model.*

@Composable
internal fun VerificationRecordDetail(record: VerificationRecord, diagnostic: PowerDiagnosticSnapshot?, enabled: Boolean,
    onChange: (String, Long, VerificationChange) -> Unit,
    onRetest: (String, Long, VerificationSampleContext) -> Unit,
    onClose: (String, Long, VerificationCloseReason) -> Unit,
    onDelete: (String) -> Unit, unsaved: Boolean, recoveryEnabled: Boolean,
    onRetrySave: (String, Long) -> Unit, onDiscardUnsaved: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
    explanation: VerificationExplanationUiState = VerificationExplanationUiState(),
    onExplanationPreview: () -> Unit = {}, onExplanationDismiss: () -> Unit = {},
    onExplanationNotes: (Boolean) -> Unit = {}, onExplanationSend: () -> Unit = {}, onExplanationStop: () -> Unit = {}) {
    var change by remember(record.id) { mutableStateOf(false) }
    var retest by remember(record.id) { mutableStateOf(false) }
    var delete by remember(record.id) { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val paragraphs = remember(record, language) {
        VerificationResultFormatter.format(record, VerificationComparisonEngine.compare(record), language).split("\n\n")
    }
    val active = enabled && record.state != VerificationState.CLOSED
    LazyColumn(modifier.fillMaxWidth().testTag("verification-detail-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(if (unsaved) verificationText("未保存的状态：", "Unsaved state: ") + when (record.state) {
                VerificationState.BASELINE_SAVED -> verificationText("基线", "Baseline")
                VerificationState.CHANGE_RECORDED -> verificationText("手动调整", "Manual adjustment")
                VerificationState.RETEST_SAVED -> verificationText("复测", "Retest")
                VerificationState.CLOSED -> verificationText("结束", "Closed")
            } else verificationStateLabel(record.state))
        }
        if (unsaved) {
            item { Button(onClick = { onRetrySave(record.id, record.revision) }, enabled = recoveryEnabled,
                modifier = Modifier.fillMaxWidth().testTag("verification-retry-save")) {
                Text(verificationText("重试保存", "Retry save"))
            } }
            item { OutlinedButton(onClick = { onDiscardUnsaved(record.id, record.revision) }, enabled = recoveryEnabled,
                modifier = Modifier.fillMaxWidth().testTag("verification-discard-unsaved")) {
                Text(verificationText("放弃未保存修改", "Discard unsaved changes"))
            }
                Text(verificationText("仅放弃当前未保存修改；已保存版本会保留。", "Only unsaved changes are discarded; the saved version is kept."))
            }
        }
        item { Text(verificationText("目标：", "Target: ") + when (val target = record.target) {
            VerificationTarget.Overall -> verificationText("整体耗电", "Overall power")
            is VerificationTarget.AppGroup -> target.packageNames.joinToString()
        }) }
        item { Text(verificationText("统计窗口未知时无法计算调整效果；用户确认不能补齐系统证据。",
            "Unknown statistics windows prevent calculating adjustment effects; user confirmations cannot supply system evidence.")) }
        item { Text(verificationText("原设置 / 回退：", "Original setting / rollback: ") +
            (record.change?.rollback ?: verificationText("尚未记录", "Not recorded")), modifier = Modifier.testTag("verification-rollback-detail")) }
        record.change?.let { adjustment -> item {
            Text(verificationText("调整说明：", "Adjustment: ") + adjustment.description)
            Text(verificationText("手动确认时间：", "Manual confirmation time: ") + adjustment.confirmedAt)
            Text(verificationText("APA 未读取、验证或执行该设置。", "APA has not read, verified, or performed this setting change."))
        } }
        record.closeReason?.let { reason -> item { Text(when (reason) {
            VerificationCloseReason.COMPLETED -> verificationText("结束原因：完成", "Closed: completed")
            VerificationCloseReason.ROLLED_BACK -> verificationText("结束原因：用户声明已手动回退", "Closed: user declares manual rollback")
            VerificationCloseReason.ABANDONED -> verificationText("结束原因：放弃", "Closed: abandoned")
        }) } }
        item { Button(onClick = { change = true }, enabled = active,
            modifier = Modifier.fillMaxWidth().testTag("verification-change")) {
            Text(if (record.change == null) verificationText("记录一项手动调整", "Record one manual adjustment")
                else verificationText("修改调整记录", "Edit adjustment"))
        } }
        item { Button(onClick = { retest = true }, enabled = active && record.change != null && diagnostic?.let {
            verificationSummary(it, record.target, verificationInitialContext(it)) != null
        } == true, modifier = Modifier.fillMaxWidth().testTag("verification-retest")) {
            Text(if (record.retest == null) verificationText("选择当前诊断为复测", "Use current diagnostic as retest")
                else verificationText("替换复测（先预览）", "Replace retest (preview first)"))
        }
            Text(verificationText("需要新样本时，返回聊天导入 / 采集，再打开此记录。", "For a new sample, return to chat to import / collect, then reopen this record."))
        }
        paragraphs.forEachIndexed { index, paragraph -> item {
            Text(paragraph, modifier = Modifier.testTag("verification-result-$index"), style = MaterialTheme.typography.bodySmall)
        } }
        item(key = "explanation-${record.id}-${record.revision}") {
            VerificationExplanationPanel(record, explanation, onExplanationPreview, onExplanationDismiss,
                onExplanationNotes, onExplanationSend, onExplanationStop, enabled = enabled && !unsaved)
        }
        item { Text(verificationText("结束或回退仅记录您的声明，不会自动设置设备。", "Ending or rollback records your declaration; it does not change the device.")) }
        item { OutlinedButton(onClick = { onClose(record.id, record.revision, VerificationCloseReason.COMPLETED) }, enabled = active,
            modifier = Modifier.fillMaxWidth().testTag("verification-close-completed")) { Text(verificationText("结束：完成", "End: completed")) } }
        item { OutlinedButton(onClick = { onClose(record.id, record.revision, VerificationCloseReason.ROLLED_BACK) }, enabled = active,
            modifier = Modifier.fillMaxWidth()) { Text(verificationText("已手动回退并结束", "Manually rolled back; end")) } }
        item { OutlinedButton(onClick = { onClose(record.id, record.revision, VerificationCloseReason.ABANDONED) }, enabled = active,
            modifier = Modifier.fillMaxWidth()) { Text(verificationText("放弃并结束", "Abandon and end")) } }
        item { TextButton(onClick = { delete = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("verification-delete")) { Text(verificationText("删除此记录", "Delete this record")) } }
    }
    if (change) VerificationChangeForm(record, active, { change = false }) {
        onChange(record.id, record.revision, it); change = false
    }
    if (retest && diagnostic != null) VerificationSampleForm(diagnostic, record, active, { retest = false }) { _, context, _ ->
        onRetest(record.id, record.revision, context); retest = false
    }
    if (delete) VerificationDestructiveDialog(verificationText("删除此记录？", "Delete this record?"),
        verificationText("基线、调整、复测与所有备注将被删除，无法恢复。", "Baseline, adjustment, retest, and all notes will be deleted permanently."),
        enabled, { delete = false }, { onDelete(record.id); delete = false })
}
