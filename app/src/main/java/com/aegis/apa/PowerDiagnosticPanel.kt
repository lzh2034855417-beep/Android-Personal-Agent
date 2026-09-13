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
            if (snapshot != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleSelected) {
                        Text(if (selected) "● 交给 AI 解释" else "交给 AI 解释（默认关闭）")
                    }
                    TextButton(onClick = onRemove) { Text("移除") }
                }
            }
            snapshot?.let { report ->
                Text("采样：${report.sampledAtInstant} · ${report.collectionDurationMillis} ms", style = MaterialTheme.typography.labelSmall)
                val unavailable = report.sources.values.filter { it.status != DiagnosticSourceStatus.AVAILABLE }
                if (unavailable.isNotEmpty()) {
                    Text(
                        "未完整获取：" + unavailable.joinToString { "${it.source}(${it.status.name})" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (report.findings.isEmpty()) {
                    Text("当前证据不足，暂不建议限制或冻结任何应用。")
                } else {
                    report.findings.forEach { finding ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(finding.title, style = MaterialTheme.typography.titleSmall)
                                Text("证据：${finding.evidence.joinToString("；")}")
                                Text("解释：${finding.explanation}")
                                Text("置信度：${finding.confidence.label()} · 建议：${finding.adviceLevel.label()}")
                                Text("Scene：${finding.sceneAdvice}")
                                Text("注意：${finding.caveat}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                finding.packageNames.firstOrNull()?.let { packageName ->
                                    TextButton(onClick = { onCopyPackage(packageName) }) { Text("复制包名 $packageName") }
                                }
                            }
                        }
                    }
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
