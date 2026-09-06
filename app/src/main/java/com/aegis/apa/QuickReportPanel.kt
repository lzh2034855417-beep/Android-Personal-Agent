package com.aegis.apa

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aegis.apa.agent.QuickReport
import com.aegis.apa.tool.BatteryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun QuickReportPanel(model: String, sampledAt: String, battery: BatteryInfo, deviceSummary: String = "") {
    var topic by remember { mutableStateOf("电池") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val report = if (topic == "设备") deviceSummary else QuickReport.explain(topic, battery)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("读懂你的手机", style = MaterialTheme.typography.headlineSmall)
            Text("免 Key · 本地生成", color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("电池", "发热", "设备").forEach {
                    FilterChip(selected = topic == it, onClick = { topic = it }, label = { Text(it) })
                }
            }
            Text(model, style = MaterialTheme.typography.titleLarge)
            Text(report)
            Text("采样：$sampledAt", style = MaterialTheme.typography.labelSmall)
            Button(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        preview = withContext(Dispatchers.Default) {
                            renderReportCard(model, sampledAt, topic, report)
                        }
                    } catch (_: Exception) { error = "生成失败，请重试。" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "正在生成…" else "预览分享卡片") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    preview?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text("分享预览") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Image(bitmap.asImageBitmap(), "设备报告分享卡片", Modifier.fillMaxWidth())
                    Text("包含机型、采样时间及以上读数，不包含应用列表或密钥。", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("返回") } },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "report-cards").apply { mkdirs() }
                                File(directory, "APA-${System.currentTimeMillis()}.png").also { target ->
                                    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("APA report", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享设备报告"))
                        } catch (_: Exception) { error = "暂时无法分享，请重试。" }
                        finally { busy = false }
                    }
                }) { Text("选择分享应用") }
            }
        )
    }
}

internal fun renderReportCard(model: String, sampledAt: String, topic: String, report: String): Bitmap {
    val text = "$model\n\n$topic · 本地报告\n采样：$sampledAt\n\n$report\n\nAPA · Android Personal Agent\n项目：github.com/lzh2034855417-beep/Android-Personal-Agent"
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(225, 233, 240)
        textSize = 32f
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 800)
        .setLineSpacing(10f, 1.1f).setIncludePad(false).build()
    return Bitmap.createBitmap(920, layout.height + 200, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.drawColor(android.graphics.Color.rgb(16, 20, 24))
        val accent = Paint().apply { color = android.graphics.Color.rgb(86, 213, 255) }
        canvas.drawRoundRect(60f, 42f, 160f, 50f, 4f, 4f, accent)
        canvas.save()
        canvas.translate(60f, 100f)
        layout.draw(canvas)
        canvas.restore()
    }
}
