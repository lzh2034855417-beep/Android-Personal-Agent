package com.aegis.apa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aegis.apa.localization.*
import com.aegis.apa.model.*
import com.aegis.apa.agent.GuidedExplanationOptions
import com.aegis.apa.agent.GuidedExplanationState

sealed interface GuidedUiEvent {
    data class Start(val scenario: GuidedScenario) : GuidedUiEvent
    data class UpdateAnswers(val key: GuidedSessionKey, val answers: GuidedAnswers) : GuidedUiEvent
    data class PreviewSample(val key: GuidedSessionKey, val sampleId: String) : GuidedUiEvent
    data class Restart(val key: GuidedSessionKey, val scenario: GuidedScenario) : GuidedUiEvent
    data class Exit(val key: GuidedSessionKey) : GuidedUiEvent
    data class PauseForEvidence(val key: GuidedSessionKey) : GuidedUiEvent
    data class RequestAction(val actionId: String, val key: GuidedSessionKey) : GuidedUiEvent
    data class RetryAction(val actionId: String, val key: GuidedSessionKey) : GuidedUiEvent
    data class RefreshHeatSnapshot(val key: GuidedSessionKey) : GuidedUiEvent
    data class RequestExplanation(val key: GuidedSessionKey) : GuidedUiEvent
}

@Composable
fun GuidedDiagnosticScreen(state: GuidedUiState, onEvent: (GuidedUiEvent) -> Unit) {
    GuidedDiagnosticScreen(state, GuidedExplanationState(), {}, {}, {}, {}, onEvent)
}

@Composable
fun GuidedDiagnosticScreen(state: GuidedUiState, explanation: GuidedExplanationState,
    onExplanationOptions: (GuidedExplanationOptions) -> Unit, onExplanationDismiss: () -> Unit,
    onExplanationSend: (GuidedSessionKey) -> Unit, onExplanationCancel: () -> Unit,
    onEvent: (GuidedUiEvent) -> Unit) {
    val language = LocalAppLanguage.current
    val session = state.session
    var restart by remember { mutableStateOf<GuidedUiEvent.Restart?>(null) }
    var exit by remember { mutableStateOf<GuidedUiEvent.Exit?>(null) }
    BackHandler(session != null) { exit = GuidedUiEvent.Exit(session!!.key) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).testTag("guided-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(guidedText("按问题排查", "Guided diagnosis"), style = MaterialTheme.typography.titleLarge)
        Text(guidedText("无需 Key，完全本地。会话仅在页面内存中保留；旋转保留，进程结束后重新开始。最多两份主动确认摘要，不自动采样。", "No Key needed; fully local. The session lives in page memory: retained across rotation, restarted after process death. At most two explicitly confirmed summaries; no automatic sampling."))
        if (session == null || session.stage == GuidedStage.ENDED) {
            GuidedScenario.entries.forEach { scenario -> Button(onClick = { onEvent(GuidedUiEvent.Start(scenario)) },
                modifier = Modifier.fillMaxWidth().testTag("guided-start-${scenario.name}")) { Text(guidedScenarioLabel(scenario)) } }
            if (session == null) return@Column
        }
        Text(guidedScenarioLabel(session!!.scenario))
        if (session.stage != GuidedStage.ENDED) {
            GuidedAnswersForm(session, onEvent)
            state.availableSampleId?.let { id ->
                Text(guidedText("有新样本可选择；不会自动确认或替换。", "A new sample is available; it is not confirmed or replaced automatically."))
                OutlinedButton(onClick = { onEvent(GuidedUiEvent.PreviewSample(session.key, id)) },
                    modifier = Modifier.fillMaxWidth().testTag("guided-preview-$id")) { Text(guidedText("预览新样本", "Preview new sample")) }
            }
            session.samples.forEach { sample -> OutlinedButton(onClick = { onEvent(GuidedUiEvent.PreviewSample(session.key, sample.id)) },
                modifier = Modifier.fillMaxWidth().testTag("guided-preview-${sample.id}")) { Text(guidedText("查看 / 重新确认样本 ", "View / reconfirm sample ") + sample.id) } }
        }
        state.errorId?.let { Text(GuidedDiagnosticCopy.text(it, language), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("guided-error")) }
        Text(guidedText("已知事实", "Known facts"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("guided-section-facts"))
        Text(guidedText("用户声明与系统证据分开保留：", "User declarations are separate from system evidence: ") + guidedAnswersDescription(session.answers, language))
        if (state.assessment?.facts.isNullOrEmpty()) Text(guidedText("尚无可用的系统事实；缺失不是零。", "No usable system facts yet; missing is not zero."))
        state.assessment?.facts?.forEach { fact -> Card(Modifier.fillMaxWidth()) {
            Text(GuidedDiagnosticCopy.fact(fact, language), modifier = Modifier.padding(10.dp).testTag("guided-fact-${fact.id}"))
        } }
        Text(guidedText("待验证解释", "Explanations to verify"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("guided-section-candidates"))
        if (state.assessment?.candidates.isNullOrEmpty()) Text(guidedText("尚无可靠候选解释。", "No reliable candidate explanation yet."))
        state.assessment?.candidates?.take(3)?.forEach { candidate ->
            Text(GuidedDiagnosticCopy.text(candidate.ruleId, language))
            Text(guidedText("支持证据编号：", "Supporting evidence IDs: ") + candidate.evidenceIds.joinToString().ifEmpty { guidedText("仅用户声明", "User declarations only") })
            candidate.gapIds.forEach { Text(GuidedDiagnosticCopy.text(it, language)) }
        }
        Text(guidedText("尚缺证据", "Missing evidence"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("guided-section-gaps"))
        if (state.assessment?.gaps.isNullOrEmpty()) Text(guidedText("尚未评估或没有额外缺口；不代表原因已证实。", "Not yet assessed or no additional gaps; this does not prove a cause."))
        state.assessment?.gaps?.forEach { gap ->
            Text(GuidedDiagnosticCopy.text(gap.id, language))
            if (gap.evidenceIds.isNotEmpty()) Text(guidedText("关联证据编号：", "Related evidence IDs: ") + gap.evidenceIds.joinToString())
        }
        Text(guidedText("下一步", "Next step"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("guided-section-next"))
        state.assessment?.primaryAction?.let { action ->
            Text(GuidedDiagnosticCopy.action(action.id, language))
            Button(onClick = { onEvent(GuidedUiEvent.RequestAction(action.id, session.key)) },
                enabled = session.stage !in setOf(GuidedStage.ENDED, GuidedStage.AWAITING_USER_ACTION) && GuidedDiagnosticCopy.hasCopy(action.id) &&
                    (action.kind != GuidedActionKind.OPEN_VERIFICATION || guidedVerificationTarget(state, session.key, action.id) != null),
                modifier = Modifier.fillMaxWidth().testTag("guided-primary-action")) { Text(GuidedDiagnosticCopy.text(action.id, language)) }
            if (session.stage == GuidedStage.AWAITING_USER_ACTION && state.availableSampleId == null && action.kind != GuidedActionKind.END) {
                OutlinedButton(onClick = { onEvent(GuidedUiEvent.RetryAction(action.id, session.key)) },
                    modifier = Modifier.fillMaxWidth().testTag("guided-retry-action")) {
                    Text(guidedText("重新执行这一步（手动重试）", "Retry this step manually"))
                }
            }
        } ?: Text(guidedText("先明确确认声明，可保留不确定。", "Explicitly review declarations first; unsure is allowed."))
        if (canRefreshGuidedHeatSnapshot(state)) {
            OutlinedButton(onClick = { onEvent(GuidedUiEvent.RefreshHeatSnapshot(session.key)) },
                modifier = Modifier.fillMaxWidth().testTag("guided-refresh-heat")) {
                Text(guidedText("主动刷新当前基础快照", "Refresh the current basic snapshot manually"))
            }
            Text(guidedText("任务结束后也可主动采样；新读数先预览确认，最多保留两份。不会自动采样或要求复现发热。", "You may sample after the task ends. Preview and confirm the new reading; keep at most two samples. Sampling is manual and does not ask you to recreate heat."))
        }
        Text(guidedText("电池温度不是 CPU / 表面温度；报告热状态的传感器 / 全局出处不可恢复。两时刻只作描述性并列，不计算散热百分比、危险温度线或健康评分。", "Battery temperature is not CPU / surface temperature; sensor / global provenance of the reported thermal state is unavailable. Readings are descriptive observations, with no cooling percentage, danger threshold or health score."))
        if (session.stage != GuidedStage.ENDED) GuidedExplanationPanel(explanation, session.key,
            state.assessment != null, { onEvent(GuidedUiEvent.RequestExplanation(session.key)) },
            onExplanationOptions, onExplanationDismiss, onExplanationSend, onExplanationCancel)
        if (session.stage != GuidedStage.ENDED) {
            TextButton(onClick = { onEvent(GuidedUiEvent.PauseForEvidence(session.key)) }, modifier = Modifier.testTag("guided-evidence-entries")) {
                Text(guidedText("暂停并返回导入 / 只读采集 / 粗略观察入口", "Pause and return to import / read-only collection / rough observation entries"))
            }
            GuidedScenario.entries.forEach { scenario -> TextButton(onClick = { restart = GuidedUiEvent.Restart(session.key, scenario) },
                modifier = Modifier.testTag("guided-restart-${scenario.name}")) { Text(guidedText("重新开始：", "Restart: ") + guidedScenarioLabel(scenario)) } }
            TextButton(onClick = { exit = GuidedUiEvent.Exit(session.key) }, modifier = Modifier.testTag("guided-exit")) { Text(guidedText("退出并结束本次会话", "Exit and end this session")) }
        }
    }
    restart?.let { event -> AlertDialog(onDismissRequest = { restart = null },
        title = { Text(guidedText("确认重新开始？", "Confirm restart?")) },
        text = { Text(guidedText("会清除本次回答、样本和结果，取消未完成任务；远端可能已收到解释请求。", "This clears answers, samples and results and cancels unfinished work; the remote service may have received an explanation request.")) },
        confirmButton = { TextButton(onClick = { onEvent(event); restart = null }, modifier = Modifier.testTag("guided-confirm-restart")) { Text(guidedText("确认重新开始", "Confirm restart")) } },
        dismissButton = { TextButton(onClick = { restart = null }) { Text(guidedText("取消", "Cancel")) } }) }
    exit?.let { event -> AlertDialog(onDismissRequest = { exit = null },
        title = { Text(guidedText("确认结束并退出？", "End and exit?")) },
        text = { Text(guidedText("取消本次未完成任务；远端可能已收到解释请求。返回采集入口仅暂停，不会结束会话。", "Cancel unfinished work; the remote service may have received an explanation request. Visiting collection entries only pauses the session.")) },
        confirmButton = { TextButton(onClick = { onEvent(event); exit = null }, modifier = Modifier.testTag("guided-confirm-exit")) { Text(guidedText("结束并退出", "End and exit")) } },
        dismissButton = { TextButton(onClick = { exit = null }) { Text(guidedText("取消", "Cancel")) } }) }
}

@Composable
private fun guidedScenarioLabel(scenario: GuidedScenario) = when (scenario) {
    GuidedScenario.IDLE_DRAIN -> guidedText("待机掉电排查", "Idle drain guidance")
    GuidedScenario.USAGE_HEAT -> guidedText("使用时发热排查", "Heat during use guidance")
}
