package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.model.*

@Composable
fun VerificationPanel(
    state: VerificationUiState,
    currentDiagnostic: PowerDiagnosticSnapshot?,
    onCreateBaseline: (VerificationTarget, VerificationSampleContext, String) -> Unit,
    onSelect: (String?) -> Unit,
    onChange: (String, Long, VerificationChange) -> Unit,
    onRetest: (String, Long, VerificationSampleContext) -> Unit,
    onClose: (String, Long, VerificationCloseReason) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    explanationCredentials: () -> StoredApiKey? = { null },
    capabilityAccess: AgentCapabilityAccess = AgentCapabilityAccess(false, false)
) {
    var create by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val unreadable = state.storageFailure in listOf(VerificationStorageFailure.DAMAGED,
        VerificationStorageFailure.UNSUPPORTED, VerificationStorageFailure.KEY_UNAVAILABLE)
    val enabled = !state.loading && !state.saving && !unreadable
    val selected = state.unsavedRecord?.takeIf { it.id == state.selectedId }
        ?: state.records.firstOrNull { it.id == state.selectedId }
    Column(Modifier.fillMaxSize().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(verificationText("本地验证记录", "Local verification records"), style = MaterialTheme.typography.titleLarge)
        Text(verificationText("无需 Key。清空聊天会保留这些记录。30 天保留期；最多 20 条。",
            "No Key needed. Clearing chat keeps these records. Retained for 30 days; up to 20 records."),
            style = MaterialTheme.typography.bodySmall)
        if (state.loading) Text(verificationText("正在读取…", "Loading…"))
        if (state.saving) Text(verificationText("正在保存，请等待完成后退出。", "Saving; wait for completion before leaving."))
        state.storageFailure?.let { failure ->
            Text(when (failure) {
                VerificationStorageFailure.DAMAGED -> verificationText("归档损坏，无法恢复。只有明确清空后才重建。", "Archive damaged; cannot restore. Rebuild only after explicit clearing.")
                VerificationStorageFailure.UNSUPPORTED -> verificationText("不支持的归档版本，无法恢复。", "Unsupported archive version; cannot restore.")
                VerificationStorageFailure.KEY_UNAVAILABLE -> verificationText("加密密钥不可用，无法读取或保存。", "Encryption key unavailable; cannot read or save.")
                VerificationStorageFailure.LIMIT -> verificationText("超过条目或大小限制。请删除旧记录后重试。", "Record or byte limit reached. Delete old records and retry.")
                VerificationStorageFailure.WRITE_FAILED -> verificationText("保存失败，旧归档保持原样。", "Save failed; the previous archive is preserved.")
            }, color = MaterialTheme.colorScheme.error)
        }
        if (state.unsavedRecord != null) Text(verificationText("未保存：当前修改尚未持久保存，退出后可能丢失。",
            "Not saved: current changes are not durable and may be lost after exit."), color = MaterialTheme.colorScheme.error)
        if (selected == null) {
            Button(onClick = { create = true }, enabled = enabled && currentDiagnostic?.let {
                verificationSummary(it, VerificationTarget.Overall, verificationInitialContext(it)) != null
            } == true, modifier = Modifier.fillMaxWidth().testTag("verification-create")) {
                Text(verificationText("保存当前诊断为基线", "Save current diagnostic as baseline"))
            }
            if (currentDiagnostic == null) Text(verificationText("先导入报告或完成 Root 只读诊断；已有记录仍可查看。",
                "Import a report or run a Root read-only diagnostic first; existing records remain available."))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("verification-record-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.unsavedRecord?.let { unsaved -> item(key = "unsaved-${unsaved.id}") {
                    OutlinedButton(onClick = { onSelect(unsaved.id) }, enabled = !state.loading && !state.saving,
                        modifier = Modifier.fillMaxWidth().testTag("verification-unsaved-record")) {
                        Column {
                            Text(verificationText("未保存：", "Not saved: ") + unsaved.title,
                                color = MaterialTheme.colorScheme.error)
                            Text(verificationText("重新打开未保存的修改", "Reopen unsaved changes"))
                        }
                    }
                } }
                items(state.records.filter { it.id != state.unsavedRecord?.id }, key = { it.id }) { record ->
                    OutlinedButton(onClick = { onSelect(record.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(record.title); Text(verificationStateLabel(record.state)) }
                    }
                }
                if (state.records.isEmpty() && state.unsavedRecord == null && !state.loading && !unreadable) item {
                    Text(verificationText("尚无已保存记录。", "No saved records yet."))
                }
            }
            TextButton(onClick = { clear = true }, enabled = !state.loading && !state.saving &&
                state.storageFailure != VerificationStorageFailure.KEY_UNAVAILABLE,
                modifier = Modifier.testTag("verification-clear")) {
                Text(verificationText("清空验证归档", "Clear verification archive"))
            }
        } else {
            TextButton(onClick = { onSelect(null) }) { Text(verificationText("返回记录列表", "Back to records")) }
            VerificationRecordDetail(selected, currentDiagnostic, enabled,
                onChange, onRetest, onClose, onDelete, Modifier.weight(1f), explanationCredentials,
                AdviceCapabilityPolicy.effectiveLevel(
                    if (capabilityAccess.rootAuthorized) "Level 2" else if (capabilityAccess.shizukuAuthorized) "Level 1" else "Level 0",
                    capabilityAccess.shizukuAuthorized, capabilityAccess.rootAuthorized))
        }
    }
    if (create && currentDiagnostic != null) VerificationSampleForm(currentDiagnostic, null, enabled,
        onDismiss = { create = false }, onSave = { target, context, title ->
            onCreateBaseline(target, context, title); create = false
        })
    if (clear) VerificationDestructiveDialog(
        verificationText("清空全部验证记录？", "Clear all verification records?"),
        verificationText("这会删除所有摘要与备注；损坏或不支持的归档也会被重建，无法恢复。清空聊天无需此操作。",
            "This deletes all summaries and notes and rebuilds damaged or unsupported archives. It cannot be undone. Clearing chat does not require this."),
        !state.loading && !state.saving, { clear = false }, { onClear(); clear = false })
}

@Composable
internal fun verificationText(chinese: String, english: String): String =
    if (LocalAppLanguage.current == AppLanguage.EN) english else chinese

@Composable
internal fun verificationStateLabel(state: VerificationState): String = when (state) {
    VerificationState.BASELINE_SAVED -> verificationText("基线已保存", "Baseline saved")
    VerificationState.CHANGE_RECORDED -> verificationText("手动调整已记录", "Manual adjustment recorded")
    VerificationState.RETEST_SAVED -> verificationText("复测已保存", "Retest saved")
    VerificationState.CLOSED -> verificationText("已结束", "Closed")
}

@Composable
internal fun VerificationDestructiveDialog(title: String, message: String, enabled: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled,
            modifier = Modifier.testTag("verification-confirm-destructive")) { Text(verificationText("确认删除", "Confirm deletion")) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("verification-cancel-destructive")) {
            Text(verificationText("取消", "Cancel")) } })
}
