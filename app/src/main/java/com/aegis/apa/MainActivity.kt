package com.aegis.apa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.aegis.apa.agent.AgentReport
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.CloudLlmProvider
import com.aegis.apa.agent.CloudProviderCatalog
import com.aegis.apa.agent.ApiKeyStore
import com.aegis.apa.agent.ApiSession
import com.aegis.apa.agent.DeviceContext
import com.aegis.apa.agent.LocalDeviceAnalyzer
import com.aegis.apa.tool.AppTool
import com.aegis.apa.tool.AppDetails
import com.aegis.apa.tool.AppCategory
import com.aegis.apa.tool.DetectedApp
import com.aegis.apa.tool.BatteryInfo
import com.aegis.apa.tool.BatteryTool
import com.aegis.apa.tool.DeviceInfo
import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileCollector
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.HardwareExperienceEvaluator
import com.aegis.apa.tool.DeviceInfoTool
import com.aegis.apa.tool.toChipSchedulingDetails
import com.aegis.apa.tool.InstalledApp
import com.aegis.apa.tool.RamInfo
import com.aegis.apa.tool.RamTool
import com.aegis.apa.tool.RootStatus
import com.aegis.apa.tool.RootTool
import com.aegis.apa.tool.RootBatteryInfo
import com.aegis.apa.tool.RootBatteryTool
import com.aegis.apa.tool.StorageInfo
import com.aegis.apa.tool.StorageTool
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import java.util.Locale
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay

data class DeviceSnapshot(
    val deviceInfo: DeviceInfo,
    val batteryInfo: BatteryInfo,
    val ramInfo: RamInfo,
    val storageInfo: StorageInfo,
    val installedApps: List<InstalledApp>,
    val detectedApps: List<DetectedApp>,
    val rootStatus: RootStatus,
    val sampledAt: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiKeyStore.load(this)?.let { savedApiKey ->
            ApiSession.provider = savedApiKey.provider
            ApiSession.apiKey = savedApiKey.apiKey
        }
        enableEdgeToEdge()
        setContent {
            AndroidPersonalAgentTheme {
                var selectedPage by rememberSaveable { mutableIntStateOf(0) }
                var snapshot by remember { mutableStateOf(readDeviceSnapshot()) }
                var selectedAppDetails by remember { mutableStateOf<AppDetails?>(null) }
                var rootBatteryInfo by remember { mutableStateOf<RootBatteryInfo?>(null) }
                var isRootBatteryReading by remember { mutableStateOf(false) }
                var deviceProfile by remember { mutableStateOf<DeviceProfileSnapshot?>(null) }
                var isDeviceProfileReading by remember { mutableStateOf(false) }
                var chatMessages by remember { mutableStateOf<List<AgentConversationMessage>>(emptyList()) }
                var isOnlineAnalyzing by remember { mutableStateOf(false) }
                val onReadDeviceProfile = {
                    if (!isDeviceProfileReading) {
                        isDeviceProfileReading = true
                        Thread {
                            val result = DeviceProfileCollector.read(
                                preferRoot = snapshot.rootStatus.hasSuBinary
                            )
                            this@MainActivity.runOnUiThread {
                                deviceProfile = result
                                isDeviceProfileReading = false
                            }
                        }.start()
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        NavigationRail {
                            NavigationRailItem(
                                selected = selectedPage == 0,
                                onClick = { selectedPage = 0 },
                                icon = { Text("◉") },
                                label = { Text("设备") }
                            )
                            NavigationRailItem(
                                selected = selectedPage == 3,
                                onClick = { selectedPage = 3 },
                                icon = { Text("AI") },
                                label = { Text("Agent") }
                            )
                            NavigationRailItem(
                                selected = selectedPage == 2,
                                onClick = { selectedPage = 2 },
                                icon = { Text("⚿") },
                                label = { Text("能力") }
                            )
                            NavigationRailItem(
                                selected = selectedPage == 1,
                                onClick = { selectedPage = 1 },
                                icon = { Text("▣") },
                                label = { Text("应用") }
                            )
                            NavigationRailItem(
                                selected = selectedPage == 4,
                                onClick = { selectedPage = 4 },
                                icon = { Text("⚙") },
                                label = { Text("设置") }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            when (selectedPage) {
                            0 -> DeviceReportScreen(
                                deviceInfo = snapshot.deviceInfo,
                                batteryInfo = snapshot.batteryInfo,
                                ramInfo = snapshot.ramInfo,
                                storageInfo = snapshot.storageInfo,
                                rootBatteryInfo = rootBatteryInfo,
                                sampledAt = snapshot.sampledAt,
                                deviceProfile = deviceProfile,
                                isDeviceProfileReading = isDeviceProfileReading,
                                onReadDeviceProfile = onReadDeviceProfile,
                                onRefresh = { snapshot = readDeviceSnapshot() },
                                modifier = Modifier.padding(24.dp)
                            )
                            1 -> {
                                val appDetails = selectedAppDetails
                                if (appDetails == null) {
                                    AppPerceptionScreen(
                                        installedApps = snapshot.installedApps,
                                        detectedApps = snapshot.detectedApps,
                                        onRefresh = { snapshot = readDeviceSnapshot() },
                                        onAppSelected = { app ->
                                            selectedAppDetails = AppTool.readDetails(this@MainActivity, app.packageName)
                                        },
                                        modifier = Modifier.padding(24.dp)
                                    )
                                } else {
                                    AppDetailScreen(
                                        appDetails = appDetails,
                                        onBack = { selectedAppDetails = null },
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                            2 -> CapabilitySectionsScreen(
                                launchableAppCount = snapshot.installedApps.size,
                                onOpenUsageAccessSettings = { openUsageAccessSettings() },
                                rootStatus = snapshot.rootStatus,
                                rootBatteryInfo = rootBatteryInfo,
                                isRootBatteryReading = isRootBatteryReading,
                                deviceProfile = deviceProfile,
                                isDeviceProfileReading = isDeviceProfileReading,
                                onOpenShizuku = { openShizuku() },
                                onOpenRootManager = {
                                    val rootManagerPackage = if (snapshot.rootStatus.isKernelSuManagerInstalled) {
                                        "me.weishu.kernelsu"
                                    } else {
                                        "com.topjohnwu.magisk"
                                    }
                                    openRootManager(rootManagerPackage)
                                },
                                onReadRootBattery = {
                                    isRootBatteryReading = true
                                    Thread {
                                        val result = RootBatteryTool.read()
                                        this@MainActivity.runOnUiThread {
                                            rootBatteryInfo = result
                                            isRootBatteryReading = false
                                        }
                                    }.start()
                                },
                                onReadDeviceProfile = onReadDeviceProfile,
                                modifier = Modifier.padding(24.dp)
                            )
                            3 -> AgentChatScreen(
                                messages = chatMessages,
                                onAnalyze = { question, attachedReportLabel ->
                                    val localReport = LocalDeviceAnalyzer.analyze(snapshot.toDeviceContext())
                                    chatMessages = chatMessages +
                                        AgentConversationMessage(
                                            role = "user",
                                            content = question,
                                            attachedReportLabel = attachedReportLabel
                                        ) +
                                        AgentConversationMessage(
                                            role = "assistant",
                                            content = localReport.toChatContent(),
                                            source = localReport.source
                                        )
                                },
                                isOnlineAnalyzing = isOnlineAnalyzing,
                                onOnlineAnalyze = { question, selectedLevel, includeAppReport, includeSceneReport, attachedReportLabel ->
                                    val previousMessages = chatMessages
                                    chatMessages = chatMessages + AgentConversationMessage(
                                        role = "user",
                                        content = question,
                                        attachedReportLabel = attachedReportLabel
                                    )
                                    isOnlineAnalyzing = true
                                    Thread {
                                        val result = runCatching {
                                            CloudLlmProvider.analyze(
                                                context = snapshot.toDeviceContext(),
                                                userQuestion = question,
                                                selectedLevel = selectedLevel,
                                                levelReport = snapshot.buildLevelReport(
                                                    selectedLevel = selectedLevel,
                                                    rootBatteryInfo = rootBatteryInfo,
                                                    deviceProfile = deviceProfile
                                                ),
                                                appReport = snapshot.buildAppReport().takeIf { includeAppReport },
                                                sceneReport = if (includeSceneReport) {
                                                    "用户选择了 Scene 一天续航报告，但当前尚未上传报告文件；不得推断其内容。"
                                                } else {
                                                    null
                                                },
                                                conversationHistory = previousMessages
                                            )
                                        }
                                        this@MainActivity.runOnUiThread {
                                            result.fold(
                                                onSuccess = { report ->
                                                    chatMessages = chatMessages + AgentConversationMessage(
                                                        role = "assistant",
                                                        content = report.toChatContent(),
                                                        source = report.source
                                                    )
                                                },
                                                onFailure = { error ->
                                                    chatMessages = chatMessages + AgentConversationMessage(
                                                        role = "error",
                                                        content = error.message ?: "在线分析失败",
                                                        source = "${CloudProviderCatalog.find(ApiSession.provider)?.shortLabel ?: "MODEL"} · ERROR"
                                                    )
                                                }
                                            )
                                            isOnlineAnalyzing = false
                                        }
                                    }.start()
                                },
                                onClearConversation = { chatMessages = emptyList() },
                                onOpenSettings = { selectedPage = 4 },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                            )
                                else -> SettingsPrivacyScreen(modifier = Modifier.padding(24.dp))
                            }
                            if (selectedPage != 3) {
                                Text(
                                    text = "writen by Mr.Lu with gpt in 2026.7",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readDeviceSnapshot(): DeviceSnapshot {
        return DeviceSnapshot(
            deviceInfo = DeviceInfoTool.read(),
            batteryInfo = BatteryTool.read(this),
            ramInfo = RamTool.read(this),
            storageInfo = StorageTool.read(),
            installedApps = AppTool.readLaunchableApps(this),
            detectedApps = AppTool.detectKnownApps(this),
            rootStatus = RootTool.read(this),
            sampledAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        )
    }

    private fun openRootManager(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
    }

    private fun openUsageAccessSettings() {
        startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openShizuku() {
        val packageName = "moe.shizuku.privileged.api"
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }

        val marketIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=$packageName")
        )
        runCatching { startActivity(marketIntent) }.getOrElse {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }
}

private fun DeviceSnapshot.toDeviceContext() = DeviceContext(
    deviceModel = deviceInfo.model,
    androidVersion = deviceInfo.androidVersion,
    batteryLevel = batteryInfo.level,
    availableRamBytes = ramInfo.availableBytes,
    totalRamBytes = ramInfo.totalBytes,
    availableStorageBytes = storageInfo.availableBytes,
    totalStorageBytes = storageInfo.totalBytes,
    launchableAppCount = installedApps.size
)

private fun AgentReport.toChatContent(): String = buildString {
    append(summary)
    findings.forEach { finding ->
        appendLine()
        append("• $finding")
    }
}

private fun DeviceSnapshot.buildLevelReport(
    selectedLevel: String,
    rootBatteryInfo: RootBatteryInfo?,
    deviceProfile: DeviceProfileSnapshot?
): String = when (selectedLevel) {
    "Level 0" -> buildString {
        appendLine("采样时间：$sampledAt")
        appendLine("设备：${deviceInfo.model}")
        appendLine("Android：${deviceInfo.androidVersion}")
        appendLine("电量：${batteryInfo.level}%")
        appendLine("充电状态：${batteryInfo.status}")
        appendLine("瞬时电流：${batteryInfo.currentMilliAmp?.let { "$it mA" } ?: "设备未提供"}")
        appendLine("剩余电量：${batteryInfo.remainingMilliAmpHour?.let { "$it mAh" } ?: "设备未提供"}")
        appendLine("剩余能量：${batteryInfo.remainingMilliWattHour?.let { "$it mWh" } ?: "设备未提供"}")
        appendLine("可用内存：${formatSize(ramInfo.availableBytes)} / ${formatSize(ramInfo.totalBytes)}")
        appendLine("系统低内存标记：${if (ramInfo.isLowMemory) "是" else "否"}")
        appendLine("可用存储：${formatSize(storageInfo.availableBytes)} / ${formatSize(storageInfo.totalBytes)}")
    }

    "Level 1" -> buildString {
        appendLine("采样时间：$sampledAt")
        appendLine("Shizuku 应用：${if (rootStatus.isShizukuInstalled) "已安装" else "未检测到"}")
        appendLine("Shizuku 服务连接状态：当前版本尚未读取")
        appendLine("Shizuku 授权状态：当前版本尚未读取")
        appendLine("说明：仅凭安装状态不能判断服务已启动或应用已获授权。")
    }

    "Level 2" -> buildString {
        appendLine("采样时间：$sampledAt")
        appendLine("su 接口：${if (rootStatus.hasSuBinary) "检测到" else "未检测到"}")
        appendLine("KernelSU 管理器：${if (rootStatus.isKernelSuManagerInstalled) "已安装" else "未检测到"}")
        appendLine("Magisk 管理器：${if (rootStatus.isMagiskManagerInstalled) "已安装" else "未检测到"}")
        when {
            rootBatteryInfo == null -> appendLine("Root 高级电池信息：本次尚未读取")
            rootBatteryInfo.error != null -> appendLine("Root 高级电池信息：${rootBatteryInfo.error}")
            else -> {
                appendLine("设计容量：${rootBatteryInfo.designCapacityMah?.let { "$it mAh" } ?: "设备未提供"}")
                appendLine("满充容量：${rootBatteryInfo.fullChargeCapacityMah?.let { "$it mAh" } ?: "设备未提供"}")
                appendLine("循环次数：${rootBatteryInfo.cycleCount ?: "设备未提供"}")
                appendLine("瞬时电流：${rootBatteryInfo.currentMilliAmp?.let { "$it mA" } ?: "设备未提供"}")
                appendLine("电压：${rootBatteryInfo.voltageMilliVolt?.let { "$it mV" } ?: "设备未提供"}")
                appendLine("温度：${rootBatteryInfo.temperatureCelsius?.let { "$it°C" } ?: "设备未提供"}")
            }
        }
        if (deviceProfile == null) {
            appendLine("调度档案：本次尚未读取；当前保持 V8 原厂调度")
        } else {
            appendLine()
            append(deviceProfile.toReportText())
        }
    }

    else -> "未知报告等级：$selectedLevel"
}

private fun DeviceSnapshot.buildAppReport(): String = buildString {
    appendLine("可启动应用数量：${installedApps.size}")
    detectedApps.forEach { app ->
        appendLine(
            if (app.isInstalled) {
                "${app.displayName}：已安装（${app.packageName ?: "包名未知"}）"
            } else {
                "${app.displayName}：未检测到"
            }
        )
    }
}

@Composable
fun DeviceReportScreen(
    deviceInfo: DeviceInfo,
    batteryInfo: BatteryInfo,
    ramInfo: RamInfo,
    storageInfo: StorageInfo,
    rootBatteryInfo: RootBatteryInfo?,
    sampledAt: String,
    deviceProfile: DeviceProfileSnapshot?,
    isDeviceProfileReading: Boolean,
    onReadDeviceProfile: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isChipDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "DEVICE STATUS · 设备状态")
        Text(text = "LAST SAMPLE · 最近采样：$sampledAt")
        Button(onClick = onRefresh) {
            Text(text = "REFRESH · 刷新")
        }
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "实时状态")
                InfoLine("🔋", "电池", "${batteryInfo.level}%（${batteryInfo.status}）")
                InfoLine("⚡", "电流", batteryInfo.currentMilliAmp?.let { "$it mA" } ?: "设备未上报")
                InfoLine("🔋", "剩余电量", batteryInfo.remainingMilliAmpHour?.let { "$it mAh" } ?: "设备未上报")
                InfoLine("⚙", "剩余能量", batteryInfo.remainingMilliWattHour?.let { "$it mWh" } ?: "设备未上报")
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "设备与系统")
                DetailLine("📱", "设备型号", deviceInfo.model)
                InfoLine("🤖", "Android", deviceInfo.androidVersion)
                val chipDetails = deviceProfile?.toChipSchedulingDetails()
                val hardwareGrade = HardwareExperienceEvaluator.evaluate(
                    totalRamBytes = ramInfo.totalBytes,
                    totalStorageBytes = storageInfo.totalBytes,
                    maxCpuFrequencyKhz = deviceProfile
                        ?.cpuPolicies
                        ?.mapNotNull { it.maxFrequencyKhz }
                        ?.maxOrNull()
                )
                InfoLine("🏅", "硬件等级", "${hardwareGrade.label} · ${hardwareGrade.summary}")
                hardwareGrade.reasons.forEach { reason ->
                    InfoLine("", "依据", reason)
                }
                Text(text = "芯片：${chipDetails?.chipset ?: "未读取"}")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "调度：${chipDetails?.scheduler ?: "V8 原厂调度"}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (chipDetails == null) onReadDeviceProfile() else {
                                isChipDetailsExpanded = !isChipDetailsExpanded
                            }
                        },
                        enabled = !isDeviceProfileReading
                    ) {
                        Text(
                            text = when {
                                isDeviceProfileReading -> "读取中…"
                                chipDetails == null -> "读取⌄"
                                isChipDetailsExpanded -> "收起⌃"
                                else -> "详细⌄"
                            }
                        )
                    }
                }
                if (isChipDetailsExpanded && chipDetails != null) {
                    InfoLine("", "读取方式", chipDetails.access)
                    InfoLine("", "核心", chipDetails.cpuTopology)
                    chipDetails.policySummaries.forEach { Text(text = it) }
                    InfoLine("", "温度", chipDetails.thermalSummary)
                    InfoLine("", "系统", chipDetails.kernelSummary)
                    deviceProfile.error?.let { InfoLine("⚠", "采集提示", it) }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "内存与存储")
                DetailLine("💾", "内存", "${formatSize(ramInfo.availableBytes)} 可用 / ${formatSize(ramInfo.totalBytes)} 总量")
                InfoLine("", "内存状态", if (ramInfo.isLowMemory) "内存不足" else "正常")
                DetailLine("🗄", "内置存储", "${formatSize(storageInfo.usedBytes)} 已用 / ${formatSize(storageInfo.totalBytes)} 总量")
                InfoLine("", "可用空间", formatSize(storageInfo.availableBytes))
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "进阶电池")
                when {
                    rootBatteryInfo == null -> Text(text = "尚未读取 · 需要 Root 授权")
                    rootBatteryInfo.error != null -> InfoLine("⚠", "读取结果", rootBatteryInfo.error)
                    else -> {
                        InfoLine("🔋", "设计容量", "${rootBatteryInfo.designCapacityMah ?: "N/A"} mAh")
                        InfoLine("", "满充容量", "${rootBatteryInfo.fullChargeCapacityMah ?: "N/A"} mAh")
                        InfoLine("", "循环次数", rootBatteryInfo.cycleCount ?: "N/A")
                        InfoLine("⚡", "电池电流", "${rootBatteryInfo.currentMilliAmp ?: "N/A"} mA")
                        InfoLine("⚙", "电池电压", "${rootBatteryInfo.voltageMilliVolt ?: "N/A"} mV")
                        InfoLine("🌡", "电池温度", "${rootBatteryInfo.temperatureCelsius ?: "N/A"} °C")
                    }
                }
            }
        }
    }
}

@Composable
fun AppPerceptionScreen(
    installedApps: List<InstalledApp>,
    detectedApps: List<DetectedApp>,
    onRefresh: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "应用检测")
        Text(text = "检测常见 Root、框架与应用生态工具")
        Button(onClick = onRefresh) {
            Text(text = "重新扫描应用")
        }
        detectedApps.groupBy { it.category }.forEach { (category, apps) ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = category.displayName)
                    apps.forEach { detectedApp ->
                        val status = if (detectedApp.isInstalled) "已安装" else "未检测到"
                        Text(
                            text = "${detectedApp.displayName} · $status" +
                                (detectedApp.packageName?.let { "\n$it" } ?: ""),
                            color = if (detectedApp.isInstalled) Color(0xFF2E7D32) else Color(0xFF757575),
                            modifier = if (detectedApp.packageName != null) {
                                Modifier.clickable {
                                    onAppSelected(
                                        InstalledApp(detectedApp.displayName, detectedApp.packageName)
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
        Text(text = "全部可启动应用")
        Text(text = "应用概览")
        Text(text = "已识别可启动应用：${installedApps.size}")
        Text(text = "点击应用查看详情")
        installedApps.take(5).forEach { app ->
            Text(
                text = "• ${app.name}\n  ${app.packageName}",
                modifier = Modifier.clickable { onAppSelected(app) }
            )
        }
    }
}

@Composable
fun AppDetailScreen(
    appDetails: AppDetails,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "应用详情")
        Text(text = "名称：${appDetails.name}")
        Text(text = "包名：${appDetails.packageName}")
        Text(text = "版本：${appDetails.versionName} (${appDetails.versionCode})")
        Text(text = "首次安装：${dateFormat.format(appDetails.firstInstallTime)}")
        Text(text = "最近更新：${dateFormat.format(appDetails.lastUpdateTime)}")
        Text(text = "类型：${if (appDetails.isSystemApp) "系统应用" else "用户应用"}")
        Button(onClick = onBack) {
            Text(text = "返回")
        }
    }
}

@Composable
fun CapabilityScreen(
    rootStatus: RootStatus,
    rootBatteryInfo: RootBatteryInfo?,
    isRootBatteryReading: Boolean,
    onOpenKernelSu: () -> Unit,
    onOpenMagisk: () -> Unit,
    onReadRootBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "能力矩阵")
        Text(text = "Level 0 · Android API：已启用")
        Text(text = "Level 1 · Shizuku / ADB：未连接")
        Text(text = "Level 2 · Root / KernelSU：${if (rootStatus.hasSuBinary) "已启用" else "未启用"}")
        Text(
            text = if (rootStatus.hasSuBinary) {
                "Root 命令权限：已启用"
            } else {
                "Root 命令权限：未启用"
            }
        )
        Text(
            text = if (rootStatus.isKernelSuManagerInstalled) {
                "KernelSU 管理器：已安装"
            } else {
                "KernelSU 管理器：未检测到"
            }
        )
        Text(
            text = if (rootStatus.isMagiskManagerInstalled) {
                "Magisk 管理器：已安装"
            } else {
                "Magisk 管理器：未检测到"
            }
        )
        Text(
            text = if (rootStatus.hasSuBinary) {
                "Level 2 高级能力：可用"
            } else {
                "Level 2 高级能力：需要 Root 授权"
            }
        )
        Text(text = "Root 授权")
        Button(
            onClick = onOpenKernelSu,
            enabled = rootStatus.isKernelSuManagerInstalled
        ) {
            Text(text = "打开 KernelSU")
        }
        Button(
            onClick = onOpenMagisk,
            enabled = rootStatus.isMagiskManagerInstalled
        ) {
            Text(text = "打开 Magisk")
        }
        if (rootStatus.hasSuBinary) {
            Button(onClick = onReadRootBattery, enabled = !isRootBatteryReading) {
                Text(text = if (isRootBatteryReading) "正在请求 Root 授权..." else "读取进阶电池信息")
            }
            rootBatteryInfo?.let { info ->
                if (info.error != null) {
                    Text(text = "ROOT BATTERY：${info.error}")
                } else {
                    Text(text = "Design capacity：${info.designCapacityMah ?: "N/A"} mAh")
                    Text(text = "Full charge capacity：${info.fullChargeCapacityMah ?: "N/A"} mAh")
                    Text(text = "Cycle count：${info.cycleCount ?: "N/A"}")
                    Text(text = "Root current：${info.currentMilliAmp ?: "N/A"} mA")
                    Text(text = "Root voltage：${info.voltageMilliVolt ?: "N/A"} mV")
                    Text(text = "Root temperature：${info.temperatureCelsius ?: "N/A"} °C")
                }
            }
        }
    }
}
@Composable
fun CapabilitySectionsScreen(
    launchableAppCount: Int,
    rootStatus: RootStatus,
    rootBatteryInfo: RootBatteryInfo?,
    isRootBatteryReading: Boolean,
    deviceProfile: DeviceProfileSnapshot?,
    isDeviceProfileReading: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenRootManager: () -> Unit,
    onReadRootBattery: () -> Unit,
    onReadDeviceProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "能力中心")

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Level 0 · Android API")
                Text(text = "本机基础感知：设备、电池、内存、存储与应用列表。")
                Text(text = "当前已读取可启动应用：$launchableAppCount 个")
                Text(text = "亮屏时间、近期使用等数据需要你在系统设置中授予“使用情况访问”。")
                Button(onClick = onOpenUsageAccessSettings) {
                    Text(text = "授权使用情况访问")
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Level 1 · Shizuku")
                Text(
                    text = if (rootStatus.isShizukuInstalled) {
                        "已检测到 Shizuku。打开后启动服务，并在 Shizuku 中授权 APA。"
                    } else {
                        "未安装 Shizuku。点击按钮前往安装页面。"
                    }
                )
                Button(onClick = onOpenShizuku) {
                    Text(text = if (rootStatus.isShizukuInstalled) "打开 Shizuku" else "安装 Shizuku")
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Level 2 · Root 授权接口")
                Text(
                    text = if (rootStatus.isKernelSuManagerInstalled) {
                        "Root 管理器：KernelSU"
                    } else if (rootStatus.isMagiskManagerInstalled) {
                        "Root 管理器：Magisk"
                    } else {
                        "未检测到 KernelSU 或 Magisk 管理器"
                    }
                )
                Text(
                    text = if (rootStatus.hasSuBinary) {
                        "Root 接口可用，可读取高级系统与电池信息。"
                    } else {
                        "Root 接口尚未可用；请在 KernelSU 或 Magisk 中授权 APA。"
                    }
                )
                Button(
                    onClick = onOpenRootManager,
                    enabled = rootStatus.isKernelSuManagerInstalled || rootStatus.isMagiskManagerInstalled
                ) {
                    Text(text = if (rootStatus.isKernelSuManagerInstalled) "打开 KernelSU" else "打开 Magisk")
                }
                Button(
                    onClick = onReadRootBattery,
                    enabled = rootStatus.hasSuBinary && !isRootBatteryReading
                ) {
                    Text(
                        text = if (isRootBatteryReading) {
                            "正在请求 Root 授权…"
                        } else {
                            "读取高级电池信息"
                        }
                    )
                }
                rootBatteryInfo?.let { info ->
                    Text(text = info.error ?: "循环次数：${info.cycleCount ?: "未知"} · 满充容量：${info.fullChargeCapacityMah ?: "未知"} mAh")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "调度实验 · 只读档案")
                Text(text = "当前模式：V8 原厂调度")
                Text(text = "只读取设备与CPU信息，不会修改频率、温控或线程亲和性。")
                Button(
                    onClick = onReadDeviceProfile,
                    enabled = !isDeviceProfileReading
                ) {
                    Text(
                        text = if (isDeviceProfileReading) {
                            "正在读取调度档案…"
                        } else {
                            "读取调度档案"
                        }
                    )
                }
                deviceProfile?.let { profile ->
                    val accessLabel = if (profile.access == DeviceProfileAccess.ROOT) {
                        "Root 只读"
                    } else {
                        "标准权限"
                    }
                    Text(text = "设备：${profile.model ?: "未知"} · ${profile.soc ?: "SoC未知"}")
                    Text(text = "读取方式：$accessLabel · CPU：${profile.cpuPresent ?: "未知"}")
                    profile.cpuPolicies.forEach { policy ->
                        Text(
                            text = "${policy.name}：CPU ${policy.cpus.joinToString(",")} · " +
                                "最高 ${policy.maxFrequencyKhz?.div(1_000) ?: "未知"} MHz"
                        )
                    }
                    Text(text = "温度节点：${profile.thermalSensors.size} 个")
                    profile.error?.let { Text(text = "提示：$it") }
                }
            }
        }
    }
}
@Composable
fun AgentChatScreen(
    messages: List<AgentConversationMessage>,
    onAnalyze: (String, String) -> Unit,
    isOnlineAnalyzing: Boolean,
    onOnlineAnalyze: (String, String, Boolean, Boolean, String) -> Unit,
    onClearConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLevel by remember { mutableStateOf("Level 0") }
    var includeAppReport by remember { mutableStateOf(false) }
    var includeSceneReport by remember { mutableStateOf(false) }
    var isReportPickerExpanded by remember { mutableStateOf(false) }
    var userMessage by remember { mutableStateOf("") }
    val chatScrollState = rememberScrollState()
    val attachedReportLabel = listOfNotNull(
        selectedLevel.replace("Level ", "L"),
        "应用".takeIf { includeAppReport },
        "Scene".takeIf { includeSceneReport }
    ).joinToString(" · ")
    val providerLabel = CloudProviderCatalog.find(ApiSession.provider)?.shortLabel ?: "未配置模型"

    LaunchedEffect(messages.size, isOnlineAnalyzing) {
        chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "APA Agent · $providerLabel")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "清空",
                    modifier = Modifier
                        .clickable(enabled = messages.isNotEmpty() && !isOnlineAnalyzing) {
                            onClearConversation()
                        }
                        .padding(8.dp)
                )
                Text(
                    text = "设置",
                    modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(chatScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f)) {
                        Text(
                            text = "选择报告并输入问题，我会只根据本次附带的数据回答。",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            messages.forEach { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "user") {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = message.content)
                            message.attachedReportLabel?.let { label ->
                                Text(text = "已附带：$label")
                            }
                            message.source?.let { source ->
                                Text(text = source)
                            }
                        }
                    }
                }
            }

            if (isOnlineAnalyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f)) {
                        Text(text = "$providerLabel 正在分析…", modifier = Modifier.padding(12.dp))
                    }
                }
            }

        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isReportPickerExpanded = !isReportPickerExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "报告  $attachedReportLabel")
                Text(text = if (isReportPickerExpanded) "▲" else "▼")
            }
        }
        if (isReportPickerExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Level 0", "Level 1", "Level 2").forEach { level ->
                        Button(
                            onClick = { selectedLevel = level },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (selectedLevel == level) "● $level" else level)
                        }
                    }
                }
                Text(text = "附加报告（可多选）")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { includeAppReport = !includeAppReport },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = if (includeAppReport) "● 应用报告" else "应用报告")
                    }
                    Button(
                        onClick = { includeSceneReport = !includeSceneReport },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (includeSceneReport) "● Scene 一天续航" else "Scene 一天续航"
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userMessage,
                onValueChange = { userMessage = it },
                label = { Text(text = "输入消息") },
                modifier = Modifier.weight(1f),
                enabled = !isOnlineAnalyzing,
                maxLines = 4
            )
            Button(
                onClick = {
                    val message = userMessage.trim()
                    if (message.isNotEmpty()) {
                        userMessage = ""
                        if (CloudProviderCatalog.find(ApiSession.provider) != null && ApiSession.apiKey.isNotBlank()) {
                            onOnlineAnalyze(
                                message,
                                selectedLevel,
                                includeAppReport,
                                includeSceneReport,
                                attachedReportLabel
                            )
                        } else {
                            onAnalyze(message, attachedReportLabel)
                        }
                    }
                },
                enabled = userMessage.isNotBlank() && !isOnlineAnalyzing
            ) {
                Text(text = "发送")
            }
        }
    }
}

@Composable
fun SettingsPrivacyScreen(modifier: Modifier = Modifier) {
    val settingsScrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val initiallyStoredApiKey = remember { ApiKeyStore.load(context) }
    var provider by rememberSaveable { mutableStateOf(initiallyStoredApiKey?.provider ?: "OpenAI · GPT") }
    var apiKey by remember { mutableStateOf(initiallyStoredApiKey?.apiKey ?: "") }
    var storedApiKey by remember { mutableStateOf(initiallyStoredApiKey) }
    var isApiKeyFocused by remember { mutableStateOf(false) }
    val apiKeyBringIntoViewRequester = remember { BringIntoViewRequester() }
    val privacyNoticeColor by animateColorAsState(
        targetValue = if (isApiKeyFocused) Color(0xFFFF1744) else Color(0xFFB71C1C),
        label = "apiKeyPrivacyNotice"
    )

    LaunchedEffect(isApiKeyFocused) {
        if (isApiKeyFocused) {
            delay(250)
            apiKeyBringIntoViewRequester.bringIntoView()
            delay(180)
            settingsScrollState.animateScrollTo((settingsScrollState.maxValue * 0.55f).toInt())
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(settingsScrollState)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "设置与隐私")
        Text(text = "模型服务")
        CloudProviderCatalog.providers.forEach { option ->
            val optionHasKey = ApiKeyStore.load(context, option.name) != null
            Button(
                onClick = {
                    provider = option.name
                    storedApiKey = ApiKeyStore.load(context, option.name)
                    apiKey = storedApiKey?.apiKey.orEmpty()
                }
            ) {
                val selectedMark = if (provider == option.name) "● " else ""
                val savedMark = if (optionHasKey) "  ✓" else ""
                Text(text = "$selectedMark${option.name}$savedMark")
            }
        }
        val selectedConfig = CloudProviderCatalog.find(provider)
        Text(text = "已选择：$provider")
        Text(text = "模型：${selectedConfig?.model ?: "未配置"}")
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
            },
            label = { Text("API Key · Private") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .bringIntoViewRequester(apiKeyBringIntoViewRequester)
                .onFocusChanged { isApiKeyFocused = it.isFocused },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF1744),
                focusedLabelColor = Color(0xFFFF1744),
                cursorColor = Color(0xFFFF1744)
            )
        )
        Text(
            text = "隐私声明：API Key 使用 Android Keystore 加密，仅保存在本机 7 天，不会写入代码或上传至 APA 服务器。只有你主动发起云端分析时，Key 才会发送给所选 AI 服务商用于鉴权。",
            color = privacyNoticeColor
        )
        Button(onClick = {
            val saved = ApiKeyStore.save(context, provider, apiKey, validDays = 7)
            storedApiKey = saved
            if (saved != null) {
                ApiSession.provider = saved.provider
                ApiSession.apiKey = saved.apiKey
            }
        }) {
            Text(text = "加密保存 7 天")
        }
        Text(
            text = if (storedApiKey != null) {
                val expiresAtText = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(java.util.Date(requireNotNull(storedApiKey).expiresAt))
                "API KEY：已加密保存，有效期至 $expiresAtText"
            } else {
                "API KEY：未设置"
            }
        )
        Button(
            onClick = {
                ApiKeyStore.clear(context, provider)
                apiKey = ""
                storedApiKey = null
            },
            enabled = storedApiKey != null
        ) {
            Text(text = "删除 $provider 的 API Key")
        }
        Text(text = "Device data：LOCAL ONLY")
        Text(
            text = if (storedApiKey != null) {
                "云端分析：${CloudProviderCatalog.find(requireNotNull(storedApiKey).provider)?.shortLabel ?: "MODEL"} 已配置"
            } else {
                "云端分析：未配置"
            }
        )
        Spacer(modifier = Modifier.height(180.dp))
    }
}

@Composable
private fun InfoLine(icon: String, label: String, value: String) {
    val prefix = listOf(icon, label).filter { it.isNotBlank() }.joinToString(" ")
    Text(text = "$prefix：$value")
}

@Composable
private fun DetailLine(icon: String, label: String, value: String) {
    val prefix = listOf(icon, label).filter { it.isNotBlank() }.joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = prefix)
        Text(
            text = value,
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

private fun formatSize(bytes: Long): String {
    val megabytes = bytes.toDouble() / 1024 / 1024
    return if (megabytes >= 1024) {
        String.format(Locale.US, "%.1f GB", megabytes / 1024)
    } else {
        String.format(Locale.US, "%.0f MB", megabytes)
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceReportPreview() {
    AndroidPersonalAgentTheme {
        DeviceReportScreen(
            deviceInfo = DeviceInfo(
                model = "Xiaomi 示例设备",
                androidVersion = "Android 16（API 36）"
            ),
            batteryInfo = BatteryInfo(level = 80, status = "正在充电"),
            ramInfo = RamInfo(
                totalBytes = 16L * 1024 * 1024 * 1024,
                availableBytes = 8L * 1024 * 1024 * 1024,
                isLowMemory = false
            ),
            storageInfo = StorageInfo(
                totalBytes = 512L * 1024 * 1024 * 1024,
                availableBytes = 256L * 1024 * 1024 * 1024
            ),
            rootBatteryInfo = null,
            sampledAt = "12:48:03",
            deviceProfile = null,
            isDeviceProfileReading = false,
            onReadDeviceProfile = {},
            onRefresh = {}
        )
    }
}
