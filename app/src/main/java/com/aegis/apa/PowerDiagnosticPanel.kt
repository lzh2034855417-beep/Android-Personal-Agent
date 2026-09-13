package com.aegis.apa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.RankedPowerCandidate

@Composable
fun PowerDiagnosticPanel(
    state: PowerDiagnosticUiState,
    snapshot: PowerDiagnosticSnapshot?,
    selected: Boolean,
    rootAvailable: Boolean,
    onCollect: () -> Unit,
    onImportBugReport: () -> Unit,
    onToggleSelected: () -> Unit,
    onRemove: () -> Unit,
    onCopyPackage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("系统耗电诊断", style = MaterialTheme.typography.titleMedium)
            Text(
                "读取系统耗电、唤醒、任务与温控统计并翻译成建议。只诊断，不会自动冻结、限频或修改设置。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            when (state) {
                PowerDiagnosticUiState.Idle -> Text("尚未导入或采集；普通用户可导入系统 Bug Report。")
                PowerDiagnosticUiState.Importing -> Text("正在安全读取系统报告…")
                is PowerDiagnosticUiState.Collecting -> Text("正在采集 ${state.completed}/${state.total}：${state.source}")
                PowerDiagnosticUiState.Ready -> Text("本地读取完成；交给 AI 解释默认关闭。")
                is PowerDiagnosticUiState.Error -> Text("读取失败：${state.message}")
                PowerDiagnosticUiState.Interrupted -> Text("上次读取已中断，请手动重试。")
            }
            val busy = state is PowerDiagnosticUiState.Collecting || state == PowerDiagnosticUiState.Importing
            PowerDiagnosticEntryPoints.forRootAvailability(rootAvailable).forEach { entryPoint ->
                when (entryPoint) {
                    PowerDiagnosticEntryPoint.IMPORT_BUGREPORT -> Button(
                        onClick = onImportBugReport,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (snapshot == null) "导入系统报告" else "重新导入系统报告")
                    }
                    PowerDiagnosticEntryPoint.ROOT_READ_ONLY -> Button(
                        onClick = onCollect,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (snapshot == null) "开始只读诊断（Root）" else "重新采集（Root）")
                    }
                }
            }
            snapshot?.let { report ->
                val display = PowerDiagnosticDisplayModel.from(report)
                Text("采样：${report.sampledAtInstant} · ${report.collectionDurationMillis} ms", style = MaterialTheme.typography.labelSmall)
                Text("来源：${display.sourceLabel}", style = MaterialTheme.typography.labelMedium)
                val unavailable = report.sources.values.filter { it.status != DiagnosticSourceStatus.AVAILABLE }
                if (unavailable.isNotEmpty()) {
                    Text(
                        "未完整获取：" + unavailable.joinToString { "${it.source}(${it.status.name})" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("本地结论（不联网也可用）", style = MaterialTheme.typography.titleSmall)
                display.nextStep?.let { Text("证据不足：$it") }
                Text("耗电总量排行", style = MaterialTheme.typography.titleSmall)
                if (display.totalConsumption.isEmpty()) {
                    Text("没有可比较的应用耗电量数据。")
                } else {
                    display.totalConsumption.forEach { candidate ->
                        PowerCandidateCard(candidate, showAction = false, onCopyPackage)
                    }
                }
                Text("后台异常嫌疑", style = MaterialTheme.typography.titleSmall)
                if (display.backgroundSuspects.isEmpty()) {
                    Text("没有达到后台异常阈值的应用；耗电多不等于后台异常。")
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleSelected) {
                        Text(if (selected) "● 交给 AI 解释" else "交给 AI 解释（默认关闭）")
                    }
                    TextButton(onClick = onRemove) { Text("移除") }
                }
            }
        }
    }
}

@Composable
private fun PowerCandidateCard(
    candidate: RankedPowerCandidate,
    showAction: Boolean,
    onCopyPackage: (String) -> Unit
) {
    val name = candidate.packageNames.firstOrNull() ?: candidate.uid?.let { "UID $it" } ?: "未知应用"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text("事实：${candidate.facts.joinToString("；")}")
            Text("解释：${candidate.reason}")
            Text("置信度：${candidate.confidence.label()} · 最高建议：${candidate.maxAdviceLevel.label()}")
            if (showAction) {
                candidate.sceneAction?.let { Text("Scene：$it") }
                candidate.risk?.let { Text("风险：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.rollback?.let { Text("回退：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                candidate.retest?.let { Text("复测：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            candidate.packageNames.firstOrNull()?.let { packageName ->
                TextButton(onClick = { onCopyPackage(packageName) }) { Text("复制包名 $packageName") }
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
