package com.aegis.apa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import com.aegis.apa.localization.*

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AgentChatScreen(
    state: AgentPageState,
    messages: List<AgentConversationMessage>,
    onAnalyze: (String, String, String?) -> Unit,
    isOnlineAnalyzing: Boolean,
    onOnlineAnalyze: (String, String?, Boolean, Boolean, String, String?) -> Unit,
    onPreflightMessage: (String, String, String) -> Unit,
    onLocalEvidenceMessage: (String, String, String) -> Unit,
    onClearConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    powerDiagnosticState: PowerDiagnosticUiState,
    powerDiagnostic: PowerDiagnosticSnapshot?,
    batteryObservationStart: BatteryObservationPoint?,
    batteryObservationResult: BatteryObservationResult?,
    batteryObservationNotice: String?,
    rootAvailable: Boolean,
    shizukuAdviceAuthorized: Boolean = false,
    rootAdviceAuthorized: Boolean = false,
    onCollectPowerDiagnostic: () -> Unit,
    onImportBugReport: () -> Unit,
    onStartBatteryObservation: () -> Unit,
    onFinishBatteryObservation: (Boolean) -> Unit,
    onClearBatteryObservation: () -> Unit,
    onRemovePowerDiagnostic: () -> Unit,
    onCopyPackage: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    conversationLoading: Boolean = false,
    conversationStorageError: Boolean = false,
    conversationSaving: Boolean = false,
    conversationExpired: Boolean = false,
    onStopAnalysis: () -> Unit = {},
    onPrepareRetry: () -> Unit = {},
    onOpenVerification: () -> Unit = {}
) {
    val appLanguage = LocalAppLanguage.current
    var selectedLevel by state.selectedLevel
    var includeUsageReport by state.includeUsageReport
    var includeAppReport by state.includeAppReport
    var includePowerDiagnosticReport by state.includePowerDiagnosticReport
    var isReportPickerExpanded by state.reportPickerExpanded
    var userMessage by state.draft
    val chatScrollState = rememberLazyListState(state.firstVisibleIndex, state.firstVisibleOffset)
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val isOnlineMode = ApiSession.apiKey.isNotBlank()
    val usableBatteryObservation = batteryObservationResult?.takeIf { it.isUsableEvidence }
    val effectiveSelectedLevel = AdviceCapabilityPolicy.effectiveLevel(
        requestedLevel = selectedLevel,
        shizukuAuthorized = shizukuAdviceAuthorized,
        rootAuthorized = rootAdviceAuthorized
    )
    val attachedReportLabel = listOfNotNull(
        effectiveSelectedLevel?.replace("Level ", "L"),
        (if (appLanguage == AppLanguage.EN) "Apps" else "应用").takeIf { includeAppReport && ApiSession.apiKey.isNotBlank() },
        (if (appLanguage == AppLanguage.EN) "Usage" else "使用习惯").takeIf { includeUsageReport && effectiveSelectedLevel == "Level 0" && ApiSession.apiKey.isNotBlank() },
        (if (appLanguage == AppLanguage.EN) "Battery observation (local)" else "续航观察（本地）").takeIf { usableBatteryObservation != null },
        (if (appLanguage == AppLanguage.EN) "System power diagnostic" else "系统耗电诊断").takeIf { includePowerDiagnosticReport && powerDiagnostic != null }
    ).joinToString(" · ").ifBlank { if (appLanguage == AppLanguage.EN) "No report attached" else "未附带报告" }
    val providerLabel = CloudProviderCatalog.find(ApiSession.provider)?.shortLabel
        ?: if (appLanguage == AppLanguage.EN) "No model configured" else "未配置模型"

    fun submitQuestion(message: String) {
        if (message.isBlank() || isOnlineAnalyzing || conversationLoading) return
        state.followLatest = true
        userMessage = ""
        val blockingMessage = PowerAnalysisPreflight.blockingMessage(
            question = message,
            diagnosticAvailable = powerDiagnostic != null,
            diagnosticSelected = includePowerDiagnosticReport,
            observationAvailable = usableBatteryObservation != null,
            language = appLanguage
        )
        val diagnostic = powerDiagnostic?.let {
            PowerDiagnosticReportBuilder.build(
                it,
                includeAdvancedActions = effectiveSelectedLevel?.let { level -> level != "Level 0" } == true,
                language = appLanguage
            )
        }.takeIf { includePowerDiagnosticReport }
        when {
            blockingMessage != null -> onPreflightMessage(message, attachedReportLabel, blockingMessage)
            PowerAnalysisPreflight.classify(message) == PowerQuestionIntent.DRAIN_RATE && usableBatteryObservation != null ->
                onLocalEvidenceMessage(message, attachedReportLabel, BatteryObservationReportBuilder.build(usableBatteryObservation, appLanguage))
            CloudProviderCatalog.find(ApiSession.provider) != null && ApiSession.apiKey.isNotBlank() ->
                onOnlineAnalyze(message, effectiveSelectedLevel, includeAppReport,
                    includeUsageReport && effectiveSelectedLevel == "Level 0", attachedReportLabel, diagnostic)
            effectiveSelectedLevel == null && !includePowerDiagnosticReport ->
                onPreflightMessage(message, attachedReportLabel,
                    if (appLanguage == AppLanguage.EN) "Configure a cloud model in Settings to chat without reports."
                    else "未附带报告的自由聊天需要先在设置中配置云端模型。")
            else -> onAnalyze(message, attachedReportLabel, diagnostic)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.displayId, isOnlineAnalyzing) {
        if (state.followLatest) chatScrollState.scrollToItem(0)
    }
    LaunchedEffect(chatScrollState) {
        snapshotFlow {
            Triple(chatScrollState.firstVisibleItemIndex, chatScrollState.firstVisibleItemScrollOffset,
                chatScrollState.isScrollInProgress)
        }.collect { (index, offset, scrolling) ->
            state.firstVisibleIndex = index
            state.firstVisibleOffset = offset
            if (scrolling) state.followLatest = index == 0 && offset == 0
        }
    }
    LaunchedEffect(effectiveSelectedLevel) {
        if (selectedLevel != effectiveSelectedLevel) selectedLevel = effectiveSelectedLevel
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = colors.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "APA Agent", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$providerLabel · ${if (CloudProviderCatalog.find(ApiSession.provider) != null) {
                            if (appLanguage == AppLanguage.EN) "Configured" else "已配置"
                        } else if (appLanguage == AppLanguage.EN) "On-device analysis" else "本地分析"}",
                        color = colors.primary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onClearConversation,
                        enabled = (messages.isNotEmpty() || conversationStorageError) && !isOnlineAnalyzing && !conversationLoading && !conversationSaving
                    ) { Text(stringResource(R.string.agent_clear)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.common_settings)) }
                }
            }
        }

        Text(
            text = when {
                conversationLoading -> if (appLanguage == AppLanguage.EN) "Restoring conversation…" else "正在恢复对话…"
                conversationSaving -> if (appLanguage == AppLanguage.EN) "Saving conversation…" else "正在保存对话…"
                conversationStorageError -> if (appLanguage == AppLanguage.EN) "History could not be read or saved. Clear it to remove the local archive."
                    else "对话读取或保存失败。可清空对话以删除本机归档。"
                conversationExpired -> if (appLanguage == AppLanguage.EN) "The previous conversation expired after 7 days and was deleted."
                    else "上次对话已满 7 天，过期记录已删除。"
                else -> if (appLanguage == AppLanguage.EN) "Chat may contain device details. It is encrypted locally for 7 days; Clear deletes it."
                    else "对话可能含设备信息，在本机加密保留 7 天；清空后删除。"
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (conversationStorageError) colors.error else colors.onSurfaceVariant
        )
        TextButton(onClick = onOpenVerification) {
            Text(if (appLanguage == AppLanguage.EN) "Local verification records · clearing chat keeps records"
                else "本地验证记录 · 清空聊天会保留记录")
        }
        val retryQuestion = com.aegis.apa.agent.AgentRetryPolicy.question(messages)
        if (isOnlineAnalyzing || retryQuestion != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOnlineAnalyzing) {
                    TextButton(onClick = onStopAnalysis) {
                        Text(if (appLanguage == AppLanguage.EN) "Stop" else "停止")
                    }
                } else if (retryQuestion != null) {
                    TextButton(
                        enabled = !conversationLoading,
                        onClick = { onPrepareRetry(); submitQuestion(retryQuestion) }
                    ) { Text(if (appLanguage == AppLanguage.EN) "Retry" else "重试") }
                    Text(
                        if (appLanguage == AppLanguage.EN) "Uses current reports; sends a new request."
                        else "按当前报告重试，将重新发送请求。",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (!state.followLatest && messages.isNotEmpty()) {
            TextButton(onClick = { state.followLatest = true; state.firstVisibleIndex = 0; state.firstVisibleOffset = 0 }) {
                Text(if (appLanguage == AppLanguage.EN) "Latest messages" else "最新消息")
            }
        }
        LaunchedEffect(state.followLatest) {
            if (state.followLatest) chatScrollState.scrollToItem(0)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth().testTag("chat_timeline"),
            state = chatScrollState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isOnlineAnalyzing) item(key = "pending") {
                Card(modifier = Modifier.fillMaxWidth(0.9f)) {
                    Text(stringResource(R.string.reading_device_and_analyzing), Modifier.padding(12.dp))
                }
            }
            if (messages.isEmpty()) item(key = "empty") {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.agent_empty_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.agent_empty_description), color = colors.onSurfaceVariant)
                        Column {
                            listOf(
                                stringResource(R.string.question_replace_battery),
                                stringResource(R.string.question_phone_hot),
                                stringResource(R.string.question_battery_drain)
                            ).forEach { question ->
                                TextButton(onClick = { userMessage = question }) { Text(question) }
                            }
                        }
                    }
                }
            }

            items(messages.takeLast(state.visibleMessageCount).asReversed(), key = { it.displayId }) { message ->
                val copyText = AgentMessageCopyPolicy.copyText(message)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == MessageRole.USER) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (message.role == MessageRole.USER) colors.primaryContainer else colors.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = message.content)
                            message.attachedReportLabel?.let { label ->
                                Text(text = stringResource(R.string.analysis_with_report_format, label), color = colors.primary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            }
                            message.source?.let { source ->
                                Text(text = source, color = colors.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            }
                            copyText?.let { text ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onCopyMessage(text) }) {
                                        Text(stringResource(R.string.copy_full_text))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.visibleMessageCount < messages.size) item(key = "older") {
                TextButton(onClick = { state.followLatest = false; state.visibleMessageCount += 30 }) {
                    Text(if (appLanguage == AppLanguage.EN) "Load older messages" else "加载更早消息")
                }
            }

        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isReportPickerExpanded = !isReportPickerExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(R.string.current_analysis), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(text = attachedReportLabel, color = colors.primary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
                Text(text = if (isReportPickerExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.choose_reports), color = colors.primary)
            }
        }
        if (isReportPickerExpanded) {
            Column(
                modifier = Modifier.heightIn(max = if (WindowInsets.isImeVisible) 120.dp else 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Level 0", "Level 1", "Level 2").forEach { level ->
                        val isAllowed = AdviceCapabilityPolicy.allowsLevel(
                            level = level,
                            shizukuAuthorized = shizukuAdviceAuthorized,
                            rootAuthorized = rootAdviceAuthorized
                        )
                        Button(
                            onClick = { selectedLevel = if (selectedLevel == level) null else level },
                            enabled = isAllowed,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (effectiveSelectedLevel == level) "● $level" else level)
                        }
                    }
                }
                Text(
                    text = if (appLanguage == AppLanguage.EN) {
                        "L1 requires Shizuku authorization; L2 requires confirmed Root access on the Capabilities page."
                    } else {
                        "L1 需先授权 Shizuku；L2 需先在能力页确认 Root 权限。"
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(text = stringResource(R.string.attach_reports_multi), color = colors.onSurfaceVariant)
                if (ApiSession.apiKey.isNotBlank() && effectiveSelectedLevel == "Level 0") {
                    TextButton(onClick = { includeUsageReport = !includeUsageReport }) {
                        Text(if (appLanguage == AppLanguage.EN) {
                            if (includeUsageReport) "● Send usage ranking" else "Send usage ranking (off by default)"
                        } else if (includeUsageReport) "● 发送使用习惯排行" else "发送使用习惯排行（默认关闭）")
                    }
                }
                Text(
                    text = AgentAttachmentPolicy.disclosure(isOnlineMode, appLanguage),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (AgentAttachmentPolicy.showAppReport(isOnlineMode)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { includeAppReport = !includeAppReport },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (appLanguage == AppLanguage.EN) {
                                if (includeAppReport) "● App report" else "App report"
                            } else if (includeAppReport) "● 应用报告" else "应用报告")
                        }
                    }
                }
                PowerDiagnosticPanel(
                    state = powerDiagnosticState,
                    snapshot = powerDiagnostic,
                    observationStart = batteryObservationStart,
                    observationResult = batteryObservationResult,
                    observationNotice = batteryObservationNotice,
                    selected = includePowerDiagnosticReport,
                    rootAvailable = rootAvailable,
                    onCollect = onCollectPowerDiagnostic,
                    onImportBugReport = onImportBugReport,
                    onStartObservation = onStartBatteryObservation,
                    onFinishObservation = onFinishBatteryObservation,
                    onClearObservation = onClearBatteryObservation,
                    onToggleSelected = { includePowerDiagnosticReport = !includePowerDiagnosticReport },
                    onRemove = onRemovePowerDiagnostic,
                    showDiagnosticDetails = false,
                    onCopyPackage = onCopyPackage,
                    onOpenVerification = onOpenVerification
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userMessage,
                    onValueChange = { userMessage = it },
                    label = { Text(text = stringResource(R.string.ask_your_device)) },
                    modifier = Modifier.weight(1f),
                    enabled = !isOnlineAnalyzing && !conversationLoading,
                    maxLines = 4
                )
                Button(
                    onClick = { submitQuestion(userMessage.trim()) },
                    enabled = userMessage.isNotBlank() && !isOnlineAnalyzing && !conversationLoading
                ) {
                    Text(text = if (isOnlineAnalyzing) stringResource(R.string.analyzing) else stringResource(R.string.send))
                }
            }
        }
    }
}
