package com.aegis.apa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.*
import com.aegis.apa.localization.*
import com.aegis.apa.model.GuidedSessionKey

/** Display/callbacks only. Requests and all displayed AI state belong to the page ViewModel. */
@Composable
fun GuidedExplanationPanel(state: GuidedExplanationState, key: GuidedSessionKey, localReady: Boolean,
    onPreview: () -> Unit, onOptions: (GuidedExplanationOptions) -> Unit, onDismiss: () -> Unit,
    onSend: (GuidedSessionKey) -> Unit, onCancel: () -> Unit) {
    val language = LocalAppLanguage.current
    fun copy(id: String) = GuidedExplanationPanelCopy.text(id, language)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(copy("title"), style = MaterialTheme.typography.titleMedium)
        Text(copy("limits"))
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
            .toggleable(value = state.options.includeNotes, role = Role.Checkbox,
                onValueChange = { onOptions(GuidedExplanationOptions(it)) })
            .testTag("guided-explanation-notes")) {
            Checkbox(checked = state.options.includeNotes, onCheckedChange = null)
            Text(copy("notes"), modifier = Modifier.padding(top = 12.dp))
        }
        OutlinedButton(onClick = onPreview, enabled = localReady && !state.loading,
            modifier = Modifier.fillMaxWidth().testTag("guided-explanation-preview")) { Text(copy("preview")) }
        if (state.loading) {
            Text(copy("sending"))
            OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("guided-explanation-stop")) { Text(copy("stop")) }
        }
        state.errorId?.let { Text(GuidedExplanationCopy.reason(it, language), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("guided-explanation-error")) }
        state.text?.let { text -> Column(Modifier.testTag("guided-explanation-result"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy("optional"), style = MaterialTheme.typography.titleSmall)
            text.factNotes.forEach { Text(it.ids.joinToString() + ": " + it.text) }
            text.candidateNotes.forEach { Text(it.ids.joinToString() + ": " + it.text) }
            text.gapNotes.forEach { Text(it.ids.joinToString() + ": " + it.text) }
            Text(text.actionNote)
        } }
    }
    if (state.previewVisible) {
        val ready = state.payload as? GuidedPayloadResult.Ready
        val expected = ready?.sessionKey
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(copy("previewTitle")) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy("scope"))
                Text(copy("sensitive"))
                Text(if (state.options.includeNotes) copy("notesOn") else copy("notesOff"))
                if (ready != null) {
                    Text(copy("bytes") + ready.utf8Bytes + " / 16384")
                    Text(copy("byteScope"))
                    // Exact built strings; never reconstruct descriptions from the full local fact renderer.
                    Text(ready.systemPrompt + "\n\n" + ready.userPrompt, modifier = Modifier.testTag("guided-explanation-payload"))
                }
                (state.payload as? GuidedPayloadResult.Rejected)?.let {
                    Text(GuidedExplanationCopy.reason(it.reasonId, language), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("guided-explanation-preview-error"))
                }
            } },
            confirmButton = { TextButton(onClick = { expected?.let(onSend) },
                enabled = ready != null && expected == key && !state.loading,
                modifier = Modifier.testTag("guided-explanation-send")) { Text(copy("send")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(copy("cancel")) } })
    }
}

object GuidedExplanationPanelCopy {
    private val copy = mapOf(
        "title" to ("解释本次排查" to "Explain this diagnosis"),
        "limits" to ("可选在线解释。事实和下一步由本地规则确定；格式合格不证明文字准确。失败不影响本地结果。停止或更换输入时，远端可能已收到请求。" to "Optional online explanation. Facts and the next step come from local rules; valid format does not prove accuracy. Failure preserves local results. When stopping or changing inputs, the remote service may have received the request."),
        "notes" to ("发送自由备注（默认关闭）" to "Include free notes (off by default)"),
        "preview" to ("预览发送范围" to "Preview sending scope"),
        "sending" to ("正在请求可选解释…" to "Requesting optional explanation…"),
        "stop" to ("停止请求" to "Stop request"),
        "optional" to ("可选解释：请对照本地事实核查" to "Optional explanation: check against local facts"),
        "previewTitle" to ("确认发送本次排查？" to "Send this diagnosis?"),
        "scope" to ("仅发送当前场景、已确认白名单证据、规则结果和唯一主动作。不发送普通聊天、原始报告或唯一设备标识。下方为完整提示内容。" to "Sends the current scene, confirmed whitelisted evidence, rule results and one local action. Ordinary chat, raw reports and unique device identifiers are excluded. Complete prompts are shown below."),
        "sensitive" to ("包名可能暴露使用的应用，时间可能暴露活动安排。远端可能已接收停止前发送的请求。" to "Package names may reveal installed apps; timestamps may reveal activity schedules. The remote service may already have received a request before it is stopped."),
        "notesOn" to ("自由备注已开启，包括当前回答及已确认摘要中的备注；请检查下方内容。" to "Free notes are enabled, including current answers and confirmed summaries; review the content below."),
        "notesOff" to ("自由备注关闭：当前回答及已确认摘要中的备注均不发送。" to "Free notes are off: notes in current answers and confirmed summaries are excluded."),
        "bytes" to ("UTF-8 提示字节数：" to "UTF-8 prompt bytes: "),
        "byteScope" to ("上限包含系统与用户提示全文，不包含服务商协议外壳或 HTTP 头；不静默截断。" to "The limit counts complete system and user prompts, excluding provider protocol wrappers and HTTP headers; nothing is silently truncated."),
        "send" to ("确认发送此预览" to "Confirm and send this preview"),
        "cancel" to ("取消" to "Cancel")
    )
    fun text(id: String, language: AppLanguage): String = copy[id]?.let { if (language == AppLanguage.EN) it.second else it.first }
        ?: if (language == AppLanguage.EN) "Optional explanation" else "可选解释"
}
