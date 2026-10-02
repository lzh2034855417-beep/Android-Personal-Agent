package com.aegis.apa

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.agent.*
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.model.VerificationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

/** Disposable memory-only explanation. Credentials are requested only after scope confirmation. */
@Composable
internal fun VerificationExplanationPanel(record: VerificationRecord, selectedLevel: String?, credentials: () -> StoredApiKey?,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    explainPayload: (VerificationPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> AgentReport =
        { payload, key, cancellation -> VerificationExplanationProvider().explain(payload, key, cancellation) },
    executor: Executor = Dispatchers.IO.asExecutor(),
    publishExecutor: Executor = remember { val main = Handler(Looper.getMainLooper()); Executor { main.post(it) } }) {
    val language = LocalAppLanguage.current
    val latestRecord by rememberUpdatedState(record)
    val latestLevel by rememberUpdatedState(selectedLevel)
    val latestLanguage by rememberUpdatedState(language)
    var state by remember(record.id, record.revision, selectedLevel, language) { mutableStateOf(VerificationExplanationState()) }
    var includeNotes by remember(record.id, record.revision) { mutableStateOf(false) }
    var preview by remember(record.id, record.revision, selectedLevel, language) { mutableStateOf(false) }
    var credentialError by remember(record.id, record.revision, selectedLevel, language) { mutableStateOf<Exception?>(null) }
    val controller = remember(record.id, record.revision, selectedLevel, language) {
        VerificationExplanationController({ latestRecord }, { latestLevel }, { latestLanguage }, executor, publishExecutor,
            { state = it }, explainPayload)
    }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    val comparison = remember(record) { VerificationComparisonEngine.compare(record) }
    val payload = remember(record, comparison, includeNotes, selectedLevel, language) {
        VerificationExplanationPayload.build(record, comparison, VerificationExplanationOptions(includeNotes), selectedLevel, language)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(verificationText("可选模型解释", "Optional model explanation"), style = MaterialTheme.typography.titleMedium)
        Text(verificationText("模型解释不能替代本地门控。词法过滤不能保证语义安全或准确性。",
            "Model explanations cannot replace the local gate. Lexical filtering cannot guarantee semantic safety or accuracy."))
        Text(verificationText("本地门控：", "Local gate: ") +
            (comparison.commonReasons.map { it.wireId } + comparison.metrics.flatMap { m -> listOf(m.status.wireId) + m.reasons.map { it.wireId } })
                .distinct().joinToString(", "), modifier = Modifier.testTag("verification-explain-local-gate"))
        Text(verificationText("仅本次记录的结构化指标与本地结果；不加入聊天或归档。包名与时间仍可能敏感。",
            "Only this record's structured metrics and local results; never added to chat or archive. Package names and times can still be sensitive."))
        Row {
            Checkbox(includeNotes, { includeNotes = it }, enabled = enabled && !state.loading,
                modifier = Modifier.testTag("verification-explain-notes"))
            Text(verificationText("另外发送标题、场景、调整、回退及副作用备注（默认关闭）",
                "Also send title, scenario, adjustment, rollback and side-effect notes (off by default)"))
        }
        if (payload is VerificationPayloadResult.TooLarge || state.tooLarge) Text(verificationText(
            "验证附件超过 16 KiB，无法发送；不会截断。可关闭备注或减少记录范围。",
            "Verification attachment exceeds 16 KiB; sending blocked without truncation. Omit notes or reduce the record scope."), color = MaterialTheme.colorScheme.error)
        Button(onClick = { preview = true; credentialError = null }, enabled = enabled && !state.loading && payload is VerificationPayloadResult.Ready,
            modifier = Modifier.fillMaxWidth().testTag("verification-explain-preview")) { Text(verificationText("解释本次对比（先预览）", "Explain this comparison (preview first)")) }
        if (state.loading) {
            Text(verificationText("正在请求模型解释…", "Requesting model explanation…"))
            TextButton(onClick = controller::cancel, modifier = Modifier.testTag("verification-explain-cancel")) { Text(verificationText("取消解释", "Cancel explanation")) }
        }
        (credentialError ?: state.error)?.let { Text(AgentErrorMessage.from(it, language), color = MaterialTheme.colorScheme.error) }
        state.report?.let { report ->
            Text(verificationText("模型解释（非验证结论）：", "Model explanation (not a verification conclusion): ") + report.source)
            Text(report.summary, modifier = Modifier.testTag("verification-explain-result"))
        }
    }
    if (preview && payload is VerificationPayloadResult.Ready) AlertDialog(onDismissRequest = { preview = false },
        title = { Text(verificationText("确认发送范围", "Confirm sending scope")) },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Text(verificationText("仅下列不可信数据发送给所选模型服务。附件 UTF-8 字节数：", "Only the untrusted data below is sent to the selected model service. Attachment UTF-8 bytes: ") + payload.attachmentBytes)
            Text(payload.userPrompt, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = {
            preview = false
            try { controller.explain(record.id, record.revision, VerificationExplanationOptions(includeNotes), credentials()) }
            catch (error: Exception) { credentialError = error }
        }, enabled = enabled, modifier = Modifier.testTag("verification-explain-send")) { Text(verificationText("确认并请求解释", "Confirm and request explanation")) } },
        dismissButton = { TextButton(onClick = { preview = false }) { Text(verificationText("取消", "Cancel")) } })
}
