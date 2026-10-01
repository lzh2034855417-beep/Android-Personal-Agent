package com.aegis.apa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import androidx.compose.ui.res.stringResource
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.agent.PowerAnalysisPreflight
import com.aegis.apa.agent.PowerQuestionIntent
import com.aegis.apa.agent.AgentReport
import com.aegis.apa.agent.AgentPlainTextFormatter
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.AgentFailure
import com.aegis.apa.agent.AgentFailureException
import com.aegis.apa.agent.AgentErrorMessage
import com.aegis.apa.agent.CloudLlmProvider
import com.aegis.apa.agent.CloudProviderCatalog
import com.aegis.apa.agent.ApiKeyStore
import com.aegis.apa.agent.ApiSession
import com.aegis.apa.agent.AgentMessageCopyPolicy
import com.aegis.apa.agent.AppReportBuilder
import com.aegis.apa.agent.DeviceContext
import com.aegis.apa.agent.AdvancedLevelReportBuilder
import com.aegis.apa.agent.Level0ReportBuilder
import com.aegis.apa.agent.LocalDeviceAnalyzer
import com.aegis.apa.agent.PowerDiagnosticReportBuilder
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.AppLanguageStore
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.localization.DeviceUiCopy
import com.aegis.apa.localization.RuntimeNotice
import com.aegis.apa.localization.RuntimeUiCopy
import com.aegis.apa.localization.withAppLanguage
import com.aegis.apa.navigation.ShizukuDestination
import com.aegis.apa.navigation.ShizukuNavigationPolicy
import com.aegis.apa.tool.AppTool
import com.aegis.apa.model.AppDetails
import com.aegis.apa.model.AppCategory
import com.aegis.apa.model.DetectedApp
import com.aegis.apa.model.BatteryInfo
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.model.BatteryObservationReportBuilder
import com.aegis.apa.model.BatteryObservationResult
import com.aegis.apa.tool.BatteryTool
import com.aegis.apa.tool.BatteryObservationStore
import com.aegis.apa.tool.ChargingEvidenceWriteResult
import com.aegis.apa.model.DeviceInfo
import com.aegis.apa.model.CapabilityAccessFeedback
import com.aegis.apa.model.ShizukuAccessState
import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileCollector
import com.aegis.apa.tool.DeviceProfileParser
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplierRecognition
import com.aegis.apa.tool.DeviceInfoTool
import com.aegis.apa.model.DisplayInfo
import com.aegis.apa.tool.DisplayInfoTool
import com.aegis.apa.tool.DisplayReportText
import com.aegis.apa.tool.toChipSchedulingDetails
import com.aegis.apa.model.InstalledApp
import com.aegis.apa.model.RamInfo
import com.aegis.apa.tool.RamTool
import com.aegis.apa.model.RootStatus
import com.aegis.apa.tool.RootTool
import com.aegis.apa.tool.RootBatteryInfo
import com.aegis.apa.tool.RootBatteryTool
import com.aegis.apa.tool.ShizukuCapabilityTool
import com.aegis.apa.tool.SystemPowerDiagnosticsCollector
import com.aegis.apa.tool.BugReportImporter
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.PowerDiagnosticPipeline
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.StorageInfo
import com.aegis.apa.tool.StorageTool
import com.aegis.apa.tool.UsageStatsTool
import com.aegis.apa.model.UsageSummary
import com.aegis.apa.agent.AdviceCapabilityPolicy
import com.aegis.apa.agent.AgentAttachmentPolicy
import com.aegis.apa.agent.AgentCapabilityAccess
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import java.util.Locale
import com.aegis.apa.model.DeviceSnapshot
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku


@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {
    private val session by lazy { androidx.lifecycle.ViewModelProvider(this)[MainSessionViewModel::class.java] }
    private val batteryObservationStore by lazy { BatteryObservationStore(this) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage(AppLanguageStore.load(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiSession.update(ApiKeyStore.load(this))
        val appLanguage = AppLanguageStore.load(this)
        session.setLanguage(appLanguage)
        val persistedObservation = batteryObservationStore.load()
        session.reconcileBatteryObservation(
            persistedObservation?.start,
            persistedObservation?.chargingObserved ?: false
        )
        val apaApplication = application as ApaApplication
        if (apaApplication.chargingEvidenceWriteFailed && session.batteryObservationStart.value != null) {
            session.markBatteryObservationChargingObserved()
            session.batteryObservationNotice.value =
                RuntimeUiCopy.text(RuntimeNotice.CHARGING_EVIDENCE_WRITE_FAILED, appLanguage)
        }
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                AndroidPersonalAgentTheme {
                var selectedPage by rememberSaveable { mutableIntStateOf(0) }
                var snapshot by session.snapshot
                val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                var isSnapshotRefreshing by remember { mutableStateOf(false) }
                var shizukuRevision by remember { mutableIntStateOf(0) }
                var snapshotError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val snapshotMutex = remember { Mutex() }
                suspend fun refreshSnapshot(): DeviceSnapshot = snapshotMutex.withLock {
                    isSnapshotRefreshing = true
                    try {
                        withContext(Dispatchers.IO) { readDeviceSnapshot() }.also {
                            snapshot = it
                            snapshotError = null
                        }
                    } finally {
                        isSnapshotRefreshing = false
                    }
                }
                val onRefresh: () -> Unit = {
                    scope.launch {
                        try { refreshSnapshot() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { snapshotError = RuntimeUiCopy.text(RuntimeNotice.SNAPSHOT_READ_FAILED, appLanguage) }
                    }
                }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            shizukuRevision += 1
                            onRefresh()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                var selectedAppDetails by session.selectedAppDetails
                var rootBatteryInfo by session.rootBatteryInfo
                var isRootBatteryReading by session.rootBatteryReading
                var deviceProfile by session.deviceProfile
                var isDeviceProfileReading by session.deviceProfileReading
                var importedHardwareSupplyInfo by session.hardwareSupplyInfo
                var chatMessages by session.messages
                var isOnlineAnalyzing by session.analyzing
                var powerDiagnostic by session.powerDiagnostic
                var powerDiagnosticState by session.powerDiagnosticState
                var batteryObservationStart by session.batteryObservationStart
                var batteryObservationResult by session.batteryObservationResult
                var batteryObservationNotice by session.batteryObservationNotice
                DisposableEffect(Unit) {
                    val binderReceivedListener = Shizuku.OnBinderReceivedListener {
                        shizukuRevision += 1
                    }
                    val binderDeadListener = Shizuku.OnBinderDeadListener {
                        shizukuRevision += 1
                    }
                    val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                        if (requestCode == ShizukuCapabilityTool.PERMISSION_REQUEST_CODE) {
                            shizukuRevision += 1
                        }
                    }
                    Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                    Shizuku.addBinderDeadListener(binderDeadListener)
                    Shizuku.addRequestPermissionResultListener(permissionResultListener)
                    onDispose {
                        Shizuku.removeBinderReceivedListener(binderReceivedListener)
                        Shizuku.removeBinderDeadListener(binderDeadListener)
                        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
                    }
                }
                DisposableEffect(session) {
                    val powerReceiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context?, intent: Intent?) {
                            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                                session.markBatteryObservationChargingObserved()
                                if (apaApplication.persistChargingEvidence() == ChargingEvidenceWriteResult.WRITE_FAILED) {
                                    batteryObservationNotice =
                                        RuntimeUiCopy.text(RuntimeNotice.CHARGING_EVIDENCE_WRITE_FAILED, appLanguage)
                                }
                            }
                        }
                    }
                    val powerFilter = android.content.IntentFilter(Intent.ACTION_POWER_CONNECTED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(powerReceiver, powerFilter, RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("DEPRECATION")
                        registerReceiver(powerReceiver, powerFilter)
                    }
                    onDispose { runCatching { unregisterReceiver(powerReceiver) } }
                }
                val importBugReportUri: (Uri) -> Unit = { uri ->
                    if (
                        powerDiagnosticState !is PowerDiagnosticUiState.Collecting &&
                        powerDiagnosticState != PowerDiagnosticUiState.Importing
                    ) {
                        session.agent.includePowerDiagnosticReport.value = false
                        powerDiagnosticState = PowerDiagnosticUiState.Importing
                        scope.launch {
                            try {
                                val startedNanos = System.nanoTime()
                                val imported = withContext(Dispatchers.IO) {
                                    val job = currentCoroutineContext()[Job]
                                    BugReportImporter(contentResolver).import(uri) {
                                        job?.isActive == false
                                    }
                                }
                                when (imported) {
                                    is BugReportReadResult.Success -> {
                                        val importedHardware = imported.sections
                                            .firstOrNull { it.source == "hardware" }
                                            ?.let { section ->
                                                DeviceProfileParser.parse(
                                                    raw = section.output,
                                                    access = DeviceProfileAccess.STANDARD
                                                ).hardwareSupplyInfo
                                            }
                                        session.replaceImportedHardwareSupplyInfo(importedHardware)
                                        importedHardwareSupplyInfo = importedHardware
                                        val analyzed = PowerDiagnosticPipeline.analyze(
                                            sections = imported.sections,
                                            inputSource = DiagnosticInputSource.BUGREPORT,
                                            sampledAt = Instant.now(),
                                            collectionDurationMillis = (System.nanoTime() - startedNanos) / 1_000_000,
                                            packageLabelResolver = ::resolveInstalledAppLabel
                                        )
                                        session.completePowerDiagnostic(analyzed)
                                    }
                                    is BugReportReadResult.Rejected -> {
                                        powerDiagnosticState = PowerDiagnosticUiState.Error(
                                            RuntimeUiCopy.bugReportReject(imported.reason, appLanguage)
                                        )
                                    }
                                    BugReportReadResult.Cancelled -> {
                                        powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                throw cancelled
                            } catch (_: Exception) {
                                powerDiagnosticState = PowerDiagnosticUiState.Error(
                                    RuntimeUiCopy.text(RuntimeNotice.BUG_REPORT_READ_FAILED, appLanguage)
                                )
                            }
                        }
                    }
                }
                val bugReportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(importBugReportUri)
                }
                val incomingSharedBugReportUri = remember { sharedBugReportUri(intent) }
                LaunchedEffect(incomingSharedBugReportUri) {
                    incomingSharedBugReportUri?.let { uri ->
                        if (session.consumeSharedBugReport(uri.toString())) {
                            importBugReportUri(uri)
                        }
                    }
                }
                DisposableEffect(session) {
                    onDispose {
                        session.interruptAnalysis()
                        if (
                            session.powerDiagnosticState.value is PowerDiagnosticUiState.Collecting ||
                            session.powerDiagnosticState.value == PowerDiagnosticUiState.Importing
                        ) {
                            session.powerDiagnosticState.value = PowerDiagnosticUiState.Interrupted
                        }
                    }
                }
                val currentSnapshot = snapshot
                if (currentSnapshot == null) {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(text = if (appLanguage == AppLanguage.EN) {
                            if (snapshotError == null) "Reading device information…" else "Could not read device information. Try again."
                        } else if (snapshotError == null) "正在读取设备信息…" else "设备信息读取失败，请重试。")
                        if (snapshotError != null) Button(onClick = onRefresh) { Text(if (appLanguage == AppLanguage.EN) "Retry" else "重试") }
                    }
                    return@AndroidPersonalAgentTheme
                }
                val shizukuAccessState = remember(
                    currentSnapshot.rootStatus.isShizukuInstalled,
                    shizukuRevision
                ) {
                    ShizukuCapabilityTool.read(currentSnapshot.rootStatus.isShizukuInstalled)
                }
                val rootAuthorized = CapabilityAccessFeedback.hasRootEvidence(
                    rootBatteryAttempted = rootBatteryInfo != null,
                    rootBatteryError = rootBatteryInfo?.error,
                    profileUsedRoot = deviceProfile?.access == DeviceProfileAccess.ROOT,
                    diagnosticHasSuccessfulCommand =
                        powerDiagnostic?.inputSource == DiagnosticInputSource.ROOT &&
                            powerDiagnostic?.sources?.values?.any { source ->
                                source.status == com.aegis.apa.model.DiagnosticSourceStatus.AVAILABLE ||
                                    source.status == com.aegis.apa.model.DiagnosticSourceStatus.TRUNCATED
                            } == true
                )
                val agentCapabilityAccess = AgentCapabilityAccess.from(
                    shizukuAccessState = shizukuAccessState,
                    rootAuthorized = rootAuthorized
                )
                val onCollectPowerDiagnostic: () -> Unit = {
                    if (powerDiagnosticState !is PowerDiagnosticUiState.Collecting) {
                        session.agent.includePowerDiagnosticReport.value = false
                        powerDiagnosticState = PowerDiagnosticUiState.Collecting(
                            0,
                            8,
                            if (appLanguage == AppLanguage.EN) "Preparing Root authorization" else "准备 Root 授权"
                        )
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val collectionJob = currentCoroutineContext()[Job]
                                    SystemPowerDiagnosticsCollector(
                                        packageLabelResolver = ::resolveInstalledAppLabel
                                    ).collect(
                                        onProgress = { completed, total, source ->
                                            scope.launch {
                                                powerDiagnosticState = PowerDiagnosticUiState.Collecting(completed, total, source)
                                            }
                                        },
                                        cancellationRequested = { collectionJob?.isActive == false }
                                    )
                                }
                                session.completePowerDiagnostic(result)
                            } catch (cancelled: CancellationException) {
                                powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                throw cancelled
                            } catch (_: Exception) {
                                powerDiagnosticState = PowerDiagnosticUiState.Error(
                                    RuntimeUiCopy.text(RuntimeNotice.ROOT_DIAGNOSTIC_FAILED, appLanguage)
                                )
                            }
                        }
                    }
                }
                val onStartBatteryObservation: () -> Unit = {
                    runCatching {
                        val battery = BatteryTool.read(this@MainActivity)
                        val point = BatteryObservationPoint.from(
                            battery = battery,
                            sampledAtInstant = Instant.now(),
                            elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                        )
                        if (session.startBatteryObservation(point)) {
                            if (!batteryObservationStore.saveStart(point)) {
                                session.clearBatteryObservation()
                                batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_START_SAVE_FAILED, appLanguage)
                            } else {
                                apaApplication.clearChargingEvidenceWriteFailure()
                            }
                        }
                    }.onFailure { batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_START_READ_FAILED, appLanguage) }
                }
                val onFinishBatteryObservation: (Boolean) -> Unit = { userReportedCharging ->
                    if (userReportedCharging) {
                        // Mark contaminated before clearing so a storage failure can never make this interval valid again.
                        session.markBatteryObservationChargingObserved()
                        val chargingWriteResult = apaApplication.persistChargingEvidence()
                        if (batteryObservationStore.clear()) {
                            session.clearBatteryObservation()
                            apaApplication.clearChargingEvidenceWriteFailure()
                            batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_DISCARDED, appLanguage)
                        } else {
                            batteryObservationNotice = if (chargingWriteResult == ChargingEvidenceWriteResult.WRITE_FAILED) {
                                RuntimeUiCopy.text(RuntimeNotice.BATTERY_DISCARD_WRITE_AND_CLEAR_FAILED, appLanguage)
                            } else {
                                RuntimeUiCopy.text(RuntimeNotice.BATTERY_DISCARD_CLEAR_FAILED, appLanguage)
                            }
                        }
                    } else {
                        runCatching {
                            val battery = BatteryTool.read(this@MainActivity)
                            session.finishBatteryObservation(
                                BatteryObservationPoint.from(
                                    battery = battery,
                                    sampledAtInstant = Instant.now(),
                                    elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                                ),
                                userReportedCharging = false
                            )
                            if (session.batteryObservationStart.value == null) {
                                if (!batteryObservationStore.clear()) {
                                    batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_RESULT_CLEAR_FAILED, appLanguage)
                                } else {
                                    apaApplication.clearChargingEvidenceWriteFailure()
                                }
                            }
                        }.onFailure { batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_END_READ_FAILED, appLanguage) }
                    }
                }
                val onClearBatteryObservation: () -> Unit = {
                    if (batteryObservationStore.clear()) {
                        session.clearBatteryObservation()
                        apaApplication.clearChargingEvidenceWriteFailure()
                    } else {
                        batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_CLEAR_FAILED, appLanguage)
                    }
                }
                val onReadDeviceProfile = {
                    if (!isDeviceProfileReading) {
                        isDeviceProfileReading = true
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    DeviceProfileCollector.read(
                                        preferRoot = currentSnapshot.rootStatus.hasSuBinary
                                    )
                                }
                                deviceProfile = result
                            } finally {
                                isDeviceProfileReading = false
                            }
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    bottomBar = {
                        if (!WindowInsets.isImeVisible) {
                        androidx.compose.material3.NavigationBar {
                            listOf(
                                0 to stringResource(R.string.nav_device),
                                3 to stringResource(R.string.nav_agent),
                                2 to stringResource(R.string.nav_capabilities),
                                1 to stringResource(R.string.nav_apps),
                                4 to stringResource(R.string.nav_settings)
                            ).forEach { (page, title) ->
                                NavigationBarItem(
                                    selected = selectedPage == page,
                                    onClick = { selectedPage = page },
                                    icon = { Text(listOf("◉", "▦", "◇", "AI", "⚙")[page]) },
                                    label = { Text(title) }
                                )
                            }
                        }
                        }
                    }
                ) { innerPadding ->
                    Row(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            pageStates.SaveableStateProvider(selectedPage) {
                            when (selectedPage) {
                            0 -> DeviceReportScreen(
                                deviceInfo = currentSnapshot.deviceInfo,
                                batteryInfo = currentSnapshot.batteryInfo,
                                displayInfo = currentSnapshot.displayInfo,
                                ramInfo = currentSnapshot.ramInfo,
                                storageInfo = currentSnapshot.storageInfo,
                                usageSummary = currentSnapshot.usageSummary,
                                rootBatteryInfo = rootBatteryInfo,
                                sampledAt = currentSnapshot.sampledAt,
                                deviceProfile = deviceProfile,
                                importedHardwareSupplyInfo = importedHardwareSupplyInfo,
                                isDeviceProfileReading = isDeviceProfileReading,
                                onReadDeviceProfile = onReadDeviceProfile,
                                onImportHardwareReport = {
                                    bugReportPicker.launch(
                                        arrayOf("application/zip", "text/plain", "application/octet-stream")
                                    )
                                },
                                isHardwareReportBusy =
                                    powerDiagnosticState is PowerDiagnosticUiState.Collecting ||
                                        powerDiagnosticState == PowerDiagnosticUiState.Importing,
                                onCopyHardwareFeedback = { feedback ->
                                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("APA 硬件识别反馈", feedback)
                                    )
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        RuntimeUiCopy.text(RuntimeNotice.SUPPLIER_FEEDBACK_COPIED, appLanguage),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onOpenUsageAccessSettings = ::openUsageAccessSettings,
                                onRefresh = onRefresh,
                                isRefreshing = isSnapshotRefreshing,
                                refreshError = snapshotError,
                                modifier = Modifier.padding(24.dp)
                            )
                            1 -> {
                                val appDetails = selectedAppDetails
                                if (appDetails == null) {
                                    AppPerceptionScreen(
                                        installedApps = currentSnapshot.installedApps,
                                        detectedApps = currentSnapshot.detectedApps,
                                        onRefresh = onRefresh,
                                        onAppSelected = { app ->
                                            scope.launch {
                                                selectedAppDetails = withContext(Dispatchers.IO) {
                                                    AppTool.readDetails(this@MainActivity, app.packageName)
                                                }
                                            }
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
                                launchableAppCount = currentSnapshot.installedApps.size,
                                usageAccessGranted = currentSnapshot.usageSummary.accessGranted,
                                onOpenUsageAccessSettings = { openUsageAccessSettings() },
                                rootStatus = currentSnapshot.rootStatus,
                                shizukuAccessState = shizukuAccessState,
                                rootAuthorized = rootAuthorized,
                                rootBatteryInfo = rootBatteryInfo,
                                isRootBatteryReading = isRootBatteryReading,
                                deviceProfile = deviceProfile,
                                isDeviceProfileReading = isDeviceProfileReading,
                                onShizukuAction = {
                                    when (shizukuAccessState) {
                                        ShizukuAccessState.PERMISSION_REQUIRED -> {
                                            if (!ShizukuCapabilityTool.requestPermission()) openShizuku()
                                        }
                                        ShizukuAccessState.AUTHORIZED -> Unit
                                        else -> openShizuku()
                                    }
                                },
                                onOpenRootManager = {
                                    val rootManagerPackage =
                                        currentSnapshot.rootStatus.kernelSuManagerPackage
                                            ?: "com.topjohnwu.magisk"
                                    openRootManager(rootManagerPackage)
                                },
                                onReadRootBattery = {
                                    if (!isRootBatteryReading) {
                                        isRootBatteryReading = true
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) { RootBatteryTool.read() }
                                            rootBatteryInfo = result
                                            } finally {
                                            isRootBatteryReading = false
                                        }
                                        }
                                    }
                                },
                                onReadDeviceProfile = onReadDeviceProfile,
                                modifier = Modifier.padding(24.dp)
                            )
                            3 -> AgentChatScreen(
                                state = session.agent,
                                messages = chatMessages,
                                shizukuAdviceAuthorized = agentCapabilityAccess.shizukuAuthorized,
                                rootAdviceAuthorized = agentCapabilityAccess.rootAuthorized,
                                onAnalyze = { question, attachedReportLabel, attachedPowerDiagnosticReport ->
                                    chatMessages = chatMessages + AgentConversationMessage(
                                        role = MessageRole.USER, content = question, attachedReportLabel = attachedReportLabel
                                    )
                                    val generation = session.beginAnalysis()
                                    scope.launch {
                                        try {
                                            val fresh = refreshSnapshot()
                                            val report = LocalDeviceAnalyzer.analyze(fresh.toDeviceContext(), appLanguage)
                                            session.appendAnalysisMessage(generation, AgentConversationMessage(
                                                role = MessageRole.ASSISTANT,
                                                content = buildString {
                                                    appendLine(if (appLanguage == AppLanguage.EN) "Sample time: ${fresh.sampledAt}" else "采样时间：${fresh.sampledAt}")
                                                    append(report.toChatContent())
                                                    attachedPowerDiagnosticReport?.let {
                                                        appendLine()
                                                        appendLine()
                                                        appendLine(it)
                                                    }
                                                },
                                                source = report.source
                                            ))
                                        } catch (cancelled: CancellationException) {
                                            session.interruptAnalysis(generation)
                                            throw cancelled
                                        } catch (_: Exception) {
                                            session.appendAnalysisMessage(generation, AgentConversationMessage(
                                                role = MessageRole.ERROR,
                                                content = if (appLanguage == AppLanguage.EN) "Could not refresh device data. Try again." else "设备数据刷新失败，请重试。",
                                                source = "LOCAL · ERROR"
                                            ))
                                        } finally { session.finishAnalysis(generation) }
                                    }
                                },
                                isOnlineAnalyzing = isOnlineAnalyzing,
                                onOnlineAnalyze = { question, selectedLevel, includeAppReport, includeUsageReport, attachedReportLabel, attachedPowerDiagnosticReport ->
                                    val previousMessages = chatMessages
                                    val requestedProvider = ApiSession.provider
                                    val attachedRootBattery = rootBatteryInfo
                                    val attachedDeviceProfile = deviceProfile
                                    val effectiveLevel = AdviceCapabilityPolicy.effectiveLevel(
                                        requestedLevel = selectedLevel,
                                        shizukuAuthorized = agentCapabilityAccess.shizukuAuthorized,
                                        rootAuthorized = agentCapabilityAccess.rootAuthorized
                                    )
                                    val cloudAdviceScope = AdviceCapabilityPolicy.cloudAdviceScope(
                                        selectedLevel = effectiveLevel,
                                        hasDeviceEvidence = includeAppReport || includeUsageReport ||
                                            attachedPowerDiagnosticReport != null
                                    )
                                    chatMessages = chatMessages + AgentConversationMessage(
                                        role = MessageRole.USER, content = question, attachedReportLabel = attachedReportLabel,
                                        cloudProvider = requestedProvider, cloudAdviceScope = cloudAdviceScope
                                    )
                                    val generation = session.beginAnalysis(online = true)
                                    scope.launch {
                                        try {
                                            val needsFreshSnapshot = AdviceCapabilityPolicy.needsFreshSnapshot(
                                                selectedLevel = effectiveLevel,
                                                includeAppReport = includeAppReport,
                                                includeUsageReport = includeUsageReport,
                                                includePowerDiagnosticReport = attachedPowerDiagnosticReport != null
                                            )
                                            val requested = ApiSession.requireValid()
                                            if (requested.provider != requestedProvider) throw AgentFailureException(AgentFailure.PROVIDER_CHANGED)
                                            val fresh = if (needsFreshSnapshot) refreshSnapshot() else null
                                            val result = withContext(Dispatchers.IO) {
                                                val credentials = ApiKeyStore.load(this@MainActivity, requested.provider)
                                                    ?: throw AgentFailureException(AgentFailure.EXPIRED_KEY)
                                                CloudLlmProvider.analyze(
                                                    context = fresh?.toDeviceContext().takeIf { effectiveLevel != null },
                                                    userQuestion = question,
                                                    selectedLevel = effectiveLevel,
                                                    levelReport = effectiveLevel?.let { level ->
                                                        checkNotNull(fresh).buildLevelReport(
                                                            level,
                                                            attachedRootBattery,
                                                            attachedDeviceProfile,
                                                            includeUsageReport,
                                                            appLanguage,
                                                            shizukuAccessState
                                                        )
                                                    },
                                                    appReport = fresh?.buildAppReport(appLanguage).takeIf { includeAppReport },
                                                    powerDiagnosticReport = attachedPowerDiagnosticReport,
                                                    conversationHistory = previousMessages,
                                                    credentials = credentials,
                                                    language = appLanguage
                                                )
                                            }
                                            session.appendAnalysisMessage(generation, AgentConversationMessage(
                                                role = MessageRole.ASSISTANT,
                                                content = result.toChatContent(),
                                                attachedReportLabel = attachedReportLabel,
                                                source = result.source,
                                                cloudProvider = requestedProvider,
                                                cloudAdviceScope = cloudAdviceScope
                                            ))
                                        } catch (cancelled: CancellationException) {
                                            session.interruptAnalysis(generation)
                                            throw cancelled
                                        } catch (error: Exception) {
                                            session.appendAnalysisMessage(generation, AgentConversationMessage(
                                                role = MessageRole.ERROR, content = AgentErrorMessage.from(error, appLanguage), source = "MODEL · ERROR"
                                            ))
                                        } finally { session.finishAnalysis(generation) }
                                    }
                                },
                                onPreflightMessage = { question, attachedReportLabel, message ->
                                    chatMessages = chatMessages + listOf(
                                        AgentConversationMessage(
                                            role = MessageRole.USER,
                                            content = question,
                                            attachedReportLabel = attachedReportLabel
                                        ),
                                        AgentConversationMessage(
                                            role = MessageRole.ASSISTANT,
                                            content = message,
                                            source = "LOCAL · INPUT CHECK"
                                        )
                                    )
                                },
                                onLocalEvidenceMessage = { question, attachedReportLabel, message ->
                                    chatMessages = chatMessages + listOf(
                                        AgentConversationMessage(
                                            role = MessageRole.USER,
                                            content = question,
                                            attachedReportLabel = attachedReportLabel
                                        ),
                                        AgentConversationMessage(
                                            role = MessageRole.ASSISTANT,
                                            content = message,
                                            source = "LOCAL · BATTERY OBSERVATION"
                                        )
                                    )
                                },
                                onClearConversation = { chatMessages = emptyList() },
                                onOpenSettings = { selectedPage = 4 },
                                powerDiagnosticState = powerDiagnosticState,
                                powerDiagnostic = powerDiagnostic,
                                batteryObservationStart = batteryObservationStart,
                                batteryObservationResult = batteryObservationResult,
                                batteryObservationNotice = batteryObservationNotice,
                                rootAvailable = currentSnapshot.rootStatus.hasSuBinary,
                                onCollectPowerDiagnostic = onCollectPowerDiagnostic,
                                onImportBugReport = {
                                    bugReportPicker.launch(
                                        arrayOf("application/zip", "text/plain", "application/octet-stream")
                                    )
                                },
                                onStartBatteryObservation = onStartBatteryObservation,
                                onFinishBatteryObservation = onFinishBatteryObservation,
                                onClearBatteryObservation = onClearBatteryObservation,
                                onRemovePowerDiagnostic = {
                                    powerDiagnostic = null
                                    powerDiagnosticState = PowerDiagnosticUiState.Idle
                                    session.agent.includePowerDiagnosticReport.value = false
                                },
                                onCopyPackage = { packageName ->
                                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("package", packageName))
                                },
                                onCopyMessage = { messageText ->
                                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            if (appLanguage == AppLanguage.EN) "APA reply" else "APA 回复",
                                            messageText
                                        )
                                    )
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        if (appLanguage == AppLanguage.EN) "Reply copied" else "回复已复制",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                            )
                                else -> SettingsPrivacyScreen(
                                    currentLanguage = appLanguage,
                                    onLanguageSelected = { language ->
                                        if (language != appLanguage) {
                                            AppLanguageStore.save(this@MainActivity, language)
                                            this@MainActivity.recreate()
                                        }
                                    },
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                            }

                        }
                    }
                }
            }
            }
        }
    }

    private fun sharedBugReportUri(sourceIntent: Intent?): Uri? {
        if (sourceIntent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun readDeviceSnapshot(): DeviceSnapshot {
        return DeviceSnapshot(
            deviceInfo = DeviceInfoTool.read(),
            batteryInfo = BatteryTool.read(this),
            displayInfo = DisplayInfoTool.read(this),
            ramInfo = RamTool.read(this),
            storageInfo = StorageTool.read(),
            usageSummary = UsageStatsTool.read(this),
            installedApps = AppTool.readLaunchableApps(this),
            detectedApps = AppTool.detectKnownApps(this),
            rootStatus = RootTool.read(this),
            sampledAtInstant = Instant.now()
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
        when (val destination = ShizukuNavigationPolicy.destination(launchIntent != null)) {
            ShizukuDestination.InstalledApp -> launchIntent?.let(::startActivity)
            is ShizukuDestination.Browser -> {
                val browserIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(destination.url)
                ).addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                runCatching { startActivity(browserIntent) }.onFailure {
                    android.widget.Toast.makeText(
                        this,
                        if (AppLanguageStore.load(this) == AppLanguage.EN) {
                            "No browser is available to open the download page"
                        } else {
                            "未找到可打开下载页面的浏览器"
                        },
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun resolveInstalledAppLabel(packageName: String): String? = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        appInfo.loadLabel(packageManager).toString().trim().takeIf(String::isNotEmpty)
    }.getOrNull()
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
    append(AgentPlainTextFormatter.format(summary))
    findings.forEach { finding ->
        appendLine()
        append("• ${AgentPlainTextFormatter.format(finding)}")
    }
}

private fun DeviceSnapshot.buildLevelReport(
    selectedLevel: String,
    rootBatteryInfo: RootBatteryInfo?,
    deviceProfile: DeviceProfileSnapshot?,
    includeUsageReport: Boolean = false,
    language: AppLanguage = AppLanguage.ZH_CN,
    shizukuAccessState: ShizukuAccessState = ShizukuAccessState.NOT_INSTALLED
): String = when (selectedLevel) {
    "Level 0" -> Level0ReportBuilder.build(
        sampledAt = sampledAt,
        deviceInfo = deviceInfo,
        batteryInfo = batteryInfo,
        displayInfo = displayInfo,
        ramInfo = ramInfo,
        storageInfo = storageInfo,
        usageSummary = usageSummary,
        includeUsageReport = includeUsageReport,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        socName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() }.joinToString(" ")
        } else null,
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        language = language
    )

    "Level 1" -> AdvancedLevelReportBuilder.buildLevel1(
        sampledAt = sampledAt,
        rootStatus = rootStatus,
        shizukuAccessState = shizukuAccessState,
        language = language
    )

    "Level 2" -> AdvancedLevelReportBuilder.buildLevel2(
        sampledAt = sampledAt,
        rootStatus = rootStatus,
        rootBatteryInfo = rootBatteryInfo,
        deviceProfile = deviceProfile,
        language = language
    )

    else -> if (language == AppLanguage.EN) {
        "Unknown report level: $selectedLevel"
    } else {
        "未知报告等级：$selectedLevel"
    }
}

private fun DeviceSnapshot.buildAppReport(language: AppLanguage): String = AppReportBuilder.build(
    launchableAppCount = installedApps.size,
    detectedApps = detectedApps,
    language = language
)

@Composable
fun DeviceReportScreen(
    deviceInfo: DeviceInfo,
    batteryInfo: BatteryInfo,
    displayInfo: DisplayInfo,
    ramInfo: RamInfo,
    storageInfo: StorageInfo,
    usageSummary: UsageSummary,
    rootBatteryInfo: RootBatteryInfo?,
    sampledAt: String,
    deviceProfile: DeviceProfileSnapshot?,
    importedHardwareSupplyInfo: HardwareSupplyInfo?,
    isDeviceProfileReading: Boolean,
    onReadDeviceProfile: () -> Unit,
    onImportHardwareReport: () -> Unit,
    isHardwareReportBusy: Boolean,
    onCopyHardwareFeedback: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    refreshError: String? = null,
    modifier: Modifier = Modifier
) {
    val appLanguage = LocalAppLanguage.current
    var isChipDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var showHardwareImportHelp by rememberSaveable { mutableStateOf(false) }
    val hardwareSupplyInfo = importedHardwareSupplyInfo
        ?.mergeMissingFrom(deviceProfile?.hardwareSupplyInfo)
        ?: deviceProfile?.hardwareSupplyInfo
    val hardwareSummary = HardwareSupplySummary.format(
        hardware = hardwareSupplyInfo,
        totalRamBytes = ramInfo.totalBytes,
        totalStorageBytes = storageInfo.totalBytes,
        language = appLanguage
    )
    val hardwareNeedsReview = HardwareSupplierRecognition.needsReview(hardwareSupplyInfo)
    val hardwareFeedback = HardwareSupplierFeedback.format(
        deviceName = deviceInfo.model,
        androidVersion = deviceInfo.androidVersion,
        hardware = hardwareSupplyInfo,
        appVersion = BuildConfig.VERSION_NAME,
        language = appLanguage
    )
    val chipDetails = deviceProfile?.toChipSchedulingDetails()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(R.string.device_overview), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        QuickReportPanel(
            model = deviceInfo.model,
            sampledAt = sampledAt,
            battery = batteryInfo,
            deviceSummary = if (appLanguage == AppLanguage.EN) {
                "${deviceInfo.androidVersion}\n" +
                    "Current refresh rate: ${displayInfo.currentRefreshRate?.let { "${it.toInt()} Hz" } ?: "Unavailable"}\n" +
                    "Available RAM: ${formatSize(ramInfo.availableBytes)} / ${formatSize(ramInfo.totalBytes)}\n" +
                    "Available storage: ${formatSize(storageInfo.availableBytes)} / ${formatSize(storageInfo.totalBytes)}\n\n" +
                    "RAM and storage are current snapshots and cannot identify the cause of lag on their own."
            } else {
                "${deviceInfo.androidVersion}\n" +
                    "当前刷新率：${displayInfo.currentRefreshRate?.let { "${it.toInt()} Hz" } ?: "未获取到"}\n" +
                    "可用内存：${formatSize(ramInfo.availableBytes)} / ${formatSize(ramInfo.totalBytes)}\n" +
                    "可用存储：${formatSize(storageInfo.availableBytes)} / ${formatSize(storageInfo.totalBytes)}\n\n" +
                    "内存和存储是当前快照，不能单独用来确定卡顿原因。"
            },
            hardwareSummary = hardwareSummary
        ) {
            DetailLine(
                "",
                if (appLanguage == AppLanguage.EN) "System model" else "系统型号",
                deviceInfo.identity.identifiers.modelCode ?: if (appLanguage == AppLanguage.EN) "Unavailable" else "未获取到"
            )
            Text(
                text = deviceInfo.androidVersion,
                maxLines = 1
            )
            hardwareSummary?.let { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.lineSequence().forEach { line ->
                        Text(
                            text = line,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            if (hardwareNeedsReview) {
                HardwareReportImportAction(isHardwareReportBusy, onImportHardwareReport)
                hardwareFeedback?.let { feedback ->
                    OutlinedButton(onClick = { onCopyHardwareFeedback(feedback) }) {
                        Text(stringResource(R.string.copy_identification))
                    }
                }
                TextButton(onClick = { showHardwareImportHelp = !showHardwareImportHelp }) {
                    Text(if (showHardwareImportHelp) stringResource(R.string.collapse_generation_steps) else stringResource(R.string.how_generate_system_report))
                }
                if (showHardwareImportHelp) {
                    BugReportImportHelpContent()
                }
            }
            Text(text = if (appLanguage == AppLanguage.EN) "Chip: ${chipDetails?.chipset ?: "Not read"}" else "芯片：${chipDetails?.chipset ?: "未读取"}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.EN) "Scheduler: ${chipDetails?.scheduler ?: "Not read"}" else "调度：${chipDetails?.scheduler ?: "未读取"}",
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
                            isDeviceProfileReading -> if (appLanguage == AppLanguage.EN) "Reading…" else "读取中…"
                            chipDetails == null -> if (appLanguage == AppLanguage.EN) "Read⌄" else "读取⌄"
                            isChipDetailsExpanded -> if (appLanguage == AppLanguage.EN) "Collapse⌃" else "收起⌃"
                            else -> if (appLanguage == AppLanguage.EN) "Details⌄" else "详细⌄"
                        }
                    )
                }
            }
            if (isChipDetailsExpanded && chipDetails != null) {
                InfoLine("", if (appLanguage == AppLanguage.EN) "Access" else "读取方式", chipDetails.access)
                InfoLine("", if (appLanguage == AppLanguage.EN) "Cores" else "核心", chipDetails.cpuTopology)
                chipDetails.policySummaries.forEach { Text(text = it) }
                InfoLine("", if (appLanguage == AppLanguage.EN) "Temperature" else "温度", chipDetails.thermalSummary)
                InfoLine("", if (appLanguage == AppLanguage.EN) "System" else "系统", chipDetails.kernelSummary)
                deviceProfile.error?.let { InfoLine("⚠", if (appLanguage == AppLanguage.EN) "Collection note" else "采集提示", it) }
            }
        }
        Text(text = stringResource(R.string.last_sample_format, sampledAt))
        refreshError?.let { Text(text = it) }
        Button(onClick = onRefresh, enabled = !isRefreshing) {
            Text(text = if (isRefreshing) {
                if (appLanguage == AppLanguage.EN) "Refreshing…" else "正在刷新…"
            } else if (appLanguage == AppLanguage.EN) "REFRESH" else "REFRESH · 刷新")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.live_status), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                val unavailable = if (appLanguage == AppLanguage.EN) "Not reported" else "设备未上报"
                InfoLine("🔋", if (appLanguage == AppLanguage.EN) "Battery" else "电池", "${batteryInfo.levelText} (${DeviceUiCopy.batteryStatus(batteryInfo.status, appLanguage)})")
                InfoLine("⚡", if (appLanguage == AppLanguage.EN) "Current" else "电流", batteryInfo.currentMilliAmp?.let { "$it mA" } ?: unavailable)
                InfoLine("🔋", if (appLanguage == AppLanguage.EN) "Remaining charge" else "剩余电量", batteryInfo.remainingMilliAmpHour?.let { "$it mAh" } ?: unavailable)
                InfoLine("⚙", if (appLanguage == AppLanguage.EN) "Remaining energy" else "剩余能量", batteryInfo.remainingMilliWattHour?.let { "$it mWh" } ?: unavailable)
                InfoLine("🌡", if (appLanguage == AppLanguage.EN) "Battery temperature" else "电池温度", batteryInfo.temperatureCelsius?.let { "$it°C" } ?: unavailable)
                InfoLine("⚙", if (appLanguage == AppLanguage.EN) "Battery voltage" else "电池电压", batteryInfo.voltageMilliVolt?.let { "$it mV" } ?: unavailable)
                InfoLine("", if (appLanguage == AppLanguage.EN) "Battery health" else "电池健康", batteryInfo.health?.let { DeviceUiCopy.batteryHealth(it, appLanguage) } ?: unavailable)
                InfoLine("", if (appLanguage == AppLanguage.EN) "Power source" else "充电方式", batteryInfo.plugged?.let { DeviceUiCopy.plugged(it, appLanguage) } ?: unavailable)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.screen_experience), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                DisplayReportText.format(displayInfo, appLanguage).trim().lines().forEach { line -> Text(text = line) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.usage_habits_optional), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                if (!usageSummary.accessGranted) {
                    Text(text = stringResource(R.string.usage_not_authorized))
                    Text(text = stringResource(R.string.usage_authorization_explanation))
                    Text(text = stringResource(R.string.usage_return_refresh))
                    Button(onClick = onOpenUsageAccessSettings) { Text(text = stringResource(R.string.authorize_usage_access)) }
                } else {
                    val total = usageSummary.foregroundTimeMillis
                    InfoLine(
                        "⏱",
                        if (appLanguage == AppLanguage.EN) "Foreground app use today (estimated)" else "当天应用前台使用（估算）",
                        total?.let { DeviceUiCopy.duration(it, appLanguage) }
                            ?: if (appLanguage == AppLanguage.EN) "Unavailable" else "未获取到"
                    )
                    usageSummary.rangeText(appLanguage)?.let { Text(text = stringResource(R.string.usage_range_format, it)) }
                    Text(text = stringResource(R.string.usage_estimate_notice))
                    if (usageSummary.isPartial) Text(text = stringResource(R.string.usage_partial_notice))
                    usageSummary.topApps.forEach { app ->
                        InfoLine("", app.label, if (appLanguage == AppLanguage.EN) "${app.foregroundTimeMillis / 60_000} min" else "${app.foregroundTimeMillis / 60_000} 分")
                    }
                    if (usageSummary.topApps.isEmpty()) Text(text = stringResource(R.string.usage_no_data))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.advanced_battery), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                when {
                    rootBatteryInfo == null -> Text(text = stringResource(R.string.root_battery_not_read))
                    rootBatteryInfo.error != null -> InfoLine("⚠", if (appLanguage == AppLanguage.EN) "Read result" else "读取结果", rootBatteryInfo.error)
                    else -> {
                        InfoLine("🔋", if (appLanguage == AppLanguage.EN) "Design capacity" else "设计容量", "${rootBatteryInfo.designCapacityMah ?: "N/A"} mAh")
                        InfoLine("", if (appLanguage == AppLanguage.EN) "Full-charge capacity" else "满充容量", "${rootBatteryInfo.fullChargeCapacityMah ?: "N/A"} mAh")
                        InfoLine("", if (appLanguage == AppLanguage.EN) "Cycle count" else "循环次数", rootBatteryInfo.cycleCount?.toString() ?: "N/A")
                        InfoLine("⚡", if (appLanguage == AppLanguage.EN) "Battery current" else "电池电流", "${rootBatteryInfo.currentMilliAmp ?: "N/A"} mA")
                        InfoLine("⚙", if (appLanguage == AppLanguage.EN) "Battery voltage" else "电池电压", "${rootBatteryInfo.voltageMilliVolt ?: "N/A"} mV")
                        InfoLine("🌡", if (appLanguage == AppLanguage.EN) "Battery temperature" else "电池温度", "${rootBatteryInfo.temperatureCelsius ?: "N/A"} °C")
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareReportImportAction(
    busy: Boolean,
    onImportHardwareReport: () -> Unit
) {
    val language = LocalAppLanguage.current
    OutlinedButton(
        onClick = onImportHardwareReport,
        enabled = !busy
    ) {
        Text(
            text = if (language == AppLanguage.EN) {
                if (busy) "Processing system report…" else "Import system report to read vendors"
            } else if (busy) "系统报告处理中…" else "导入系统报告读取厂商"
        )
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
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.app_detection), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.app_detection_description))
        Button(onClick = onRefresh) {
            Text(text = stringResource(R.string.rescan_apps))
        }
        detectedApps.groupBy { it.category }.forEach { (category, apps) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = category.displayName(language))
                    apps.forEach { detectedApp ->
                        val status = DeviceUiCopy.installedState(detectedApp.isInstalled, language)
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
        Text(text = stringResource(R.string.all_launchable_apps), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(text = stringResource(R.string.app_overview))
        Text(text = stringResource(R.string.identified_apps_format, installedApps.size))
        Text(text = stringResource(R.string.tap_app_details))
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
        Text(text = stringResource(R.string.app_details), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.app_name_format, appDetails.name))
        Text(text = stringResource(R.string.package_name_format, appDetails.packageName))
        Text(text = stringResource(R.string.app_version_format, appDetails.versionName, appDetails.versionCode))
        Text(text = stringResource(R.string.first_installed_format, dateFormat.format(appDetails.firstInstallTime)))
        Text(text = stringResource(R.string.last_updated_format, dateFormat.format(appDetails.lastUpdateTime)))
        Text(text = stringResource(R.string.app_type_format, stringResource(if (appDetails.isSystemApp) R.string.system_app else R.string.user_app)))
        Button(onClick = onBack) {
            Text(text = stringResource(R.string.common_back))
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
    usageAccessGranted: Boolean,
    rootStatus: RootStatus,
    shizukuAccessState: ShizukuAccessState,
    rootAuthorized: Boolean,
    rootBatteryInfo: RootBatteryInfo?,
    isRootBatteryReading: Boolean,
    deviceProfile: DeviceProfileSnapshot?,
    isDeviceProfileReading: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onShizukuAction: () -> Unit,
    onOpenRootManager: () -> Unit,
    onReadRootBattery: () -> Unit,
    onReadDeviceProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val english = language == AppLanguage.EN
    val accessFeedback = CapabilityAccessFeedback(
        usageAccessGranted = usageAccessGranted,
        shizukuAccessState = shizukuAccessState,
        rootAuthorized = rootAuthorized
    )
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.capability_center), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.level0_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.level0_description))
                Text(text = stringResource(R.string.launchable_apps_read_format, launchableAppCount))
                Text(
                    text = if (english) {
                        if (usageAccessGranted) "Usage access is granted. APA can read screen-time and recent usage data."
                        else "Screen-time and recent usage data require Usage Access in system settings."
                    } else if (usageAccessGranted) {
                        "使用情况访问已确认，可读取亮屏时间与近期使用数据。"
                    } else "亮屏时间、近期使用等数据需要你在系统设置中授予“使用情况访问”。"
                )
                Button(onClick = onOpenUsageAccessSettings, enabled = accessFeedback.usageActionEnabled) {
                    Text(text = accessFeedback.usageActionLabel(language))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.level1_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    text = if (english) when (shizukuAccessState) {
                        ShizukuAccessState.NOT_INSTALLED -> "Shizuku is not installed. Open the official download page in a browser."
                        ShizukuAccessState.SERVICE_UNAVAILABLE -> "Shizuku is installed, but its service is not connected. Open Shizuku and start the service."
                        ShizukuAccessState.PERMISSION_REQUIRED -> "The Shizuku service is connected and waiting for APA authorization."
                        ShizukuAccessState.AUTHORIZED -> "The Shizuku service is connected and APA has access."
                    } else when (shizukuAccessState) {
                        ShizukuAccessState.NOT_INSTALLED -> "未安装 Shizuku。点击按钮用浏览器打开官方下载页面。"
                        ShizukuAccessState.SERVICE_UNAVAILABLE -> "已安装 Shizuku，但服务尚未连接。请打开 Shizuku 并启动服务。"
                        ShizukuAccessState.PERMISSION_REQUIRED -> "Shizuku 服务已连接，等待授权 APA。"
                        ShizukuAccessState.AUTHORIZED -> "Shizuku 服务已连接，APA 已获得访问。"
                    }
                )
                Button(
                    onClick = onShizukuAction,
                    enabled = accessFeedback.shizukuActionEnabled
                ) {
                    Text(text = accessFeedback.shizukuActionLabel(language))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.level2_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    text = if (english) {
                        if (rootStatus.isKernelSuManagerInstalled) "Root manager: KernelSU"
                        else if (rootStatus.isMagiskManagerInstalled) "Root manager: Magisk"
                        else "KernelSU or Magisk manager not detected"
                    } else if (rootStatus.isKernelSuManagerInstalled) "Root 管理器：KernelSU"
                    else if (rootStatus.isMagiskManagerInstalled) "Root 管理器：Magisk"
                    else "未检测到 KernelSU 或 Magisk 管理器"
                )
                Text(
                    text = if (english) {
                        if (rootAuthorized) "A Root command completed successfully; APA has access."
                        else if (rootStatus.hasSuBinary) "A Root interface was detected, but access has not been confirmed by a successful command."
                        else "Root is not available yet. Authorize APA in KernelSU or Magisk."
                    } else if (rootAuthorized) "Root 命令已实际执行成功，APA 已获得访问。"
                    else if (rootStatus.hasSuBinary) "已检测到 Root 接口，但尚未通过成功命令确认授权。"
                    else "Root 接口尚未可用；请在 KernelSU 或 Magisk 中授权 APA。"
                )
                Button(
                    onClick = onOpenRootManager,
                    enabled = accessFeedback.rootActionEnabled &&
                        (rootStatus.isKernelSuManagerInstalled || rootStatus.isMagiskManagerInstalled)
                ) {
                    Text(
                        text = if (rootAuthorized) accessFeedback.rootActionLabel(language)
                        else if (english) {
                            if (rootStatus.isKernelSuManagerInstalled) "Open KernelSU" else "Open Magisk"
                        } else if (rootStatus.isKernelSuManagerInstalled) "打开 KernelSU" else "打开 Magisk"
                    )
                }
                Button(
                    onClick = onReadRootBattery,
                    enabled = rootStatus.hasSuBinary && !isRootBatteryReading
                ) {
                    Text(
                        text = if (isRootBatteryReading) {
                            if (english) "Requesting Root access…" else "正在请求 Root 授权…"
                        } else {
                            if (english) "Read advanced battery information" else "读取高级电池信息"
                        }
                    )
                }
                rootBatteryInfo?.let { info ->
                    Text(text = info.error ?: if (english) {
                        "Cycle count: ${info.cycleCount ?: "Unknown"} · Full-charge capacity: ${info.fullChargeCapacityMah ?: "Unknown"} mAh"
                    } else "循环次数：${info.cycleCount ?: "未知"} · 满充容量：${info.fullChargeCapacityMah ?: "未知"} mAh")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.scheduler_profile))
                Text(text = stringResource(R.string.scheduler_unchanged))
                Text(text = stringResource(R.string.scheduler_read_only_notice))
                Button(
                    onClick = onReadDeviceProfile,
                    enabled = !isDeviceProfileReading
                ) {
                    Text(
                        text = if (isDeviceProfileReading) {
                            if (english) "Reading scheduling profile…" else "正在读取调度档案…"
                        } else {
                            if (english) "Read scheduling profile" else "读取调度档案"
                        }
                    )
                }
                deviceProfile?.let { profile ->
                    val accessLabel = if (profile.access == DeviceProfileAccess.ROOT) {
                        if (english) "Root read-only" else "Root 只读"
                    } else {
                        if (english) "Standard access" else "标准权限"
                    }
                    Text(text = if (english) "Device: ${profile.model ?: "Unknown"} · ${profile.soc ?: "Unknown SoC"}" else "设备：${profile.model ?: "未知"} · ${profile.soc ?: "SoC未知"}")
                    Text(text = if (english) "Access: $accessLabel · CPU: ${profile.cpuPresent ?: "Unknown"}" else "读取方式：$accessLabel · CPU：${profile.cpuPresent ?: "未知"}")
                    profile.cpuPolicies.forEach { policy ->
                        Text(
                            text = if (english) {
                                "${policy.name}: CPU ${policy.cpus.joinToString(",")} · Maximum ${policy.maxFrequencyKhz?.div(1_000) ?: "Unknown"} MHz"
                            } else "${policy.name}：CPU ${policy.cpus.joinToString(",")} · 最高 ${policy.maxFrequencyKhz?.div(1_000) ?: "未知"} MHz"
                        )
                    }
                    Text(text = if (english) "Thermal nodes: ${profile.thermalSensors.size}" else "温度节点：${profile.thermalSensors.size} 个")
                    profile.error?.let { Text(text = if (english) "Note: $it" else "提示：$it") }
                }
            }
        }
    }
}
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
    modifier: Modifier = Modifier
) {
    val appLanguage = LocalAppLanguage.current
    var selectedLevel by state.selectedLevel
    var includeUsageReport by state.includeUsageReport
    var includeAppReport by state.includeAppReport
    var includePowerDiagnosticReport by state.includePowerDiagnosticReport
    var isReportPickerExpanded by state.reportPickerExpanded
    var userMessage by state.draft
    val chatScrollState = rememberScrollState()
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

    LaunchedEffect(messages.size) {
        if (state.lastAutoScrollMessageCount != messages.size) {
            state.lastAutoScrollMessageCount = messages.size
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
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
                        enabled = messages.isNotEmpty() && !isOnlineAnalyzing
                    ) { Text(stringResource(R.string.agent_clear)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.common_settings)) }
                }
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

            messages.forEach { message ->
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

            if (isOnlineAnalyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f)) {
                        Text(
                            text = if (effectiveSelectedLevel == null && !includePowerDiagnosticReport && !includeAppReport && usableBatteryObservation == null) {
                                stringResource(R.string.requesting_cloud_model)
                            } else {
                                stringResource(R.string.reading_device_and_analyzing)
                            },
                            modifier = Modifier.padding(12.dp)
                        )
                    }
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
                    onCopyPackage = onCopyPackage
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
                    enabled = !isOnlineAnalyzing,
                    maxLines = 4
                )
                Button(
                    onClick = {
                        val message = userMessage.trim()
                        if (message.isNotEmpty()) {
                            userMessage = ""
                            val blockingMessage = PowerAnalysisPreflight.blockingMessage(
                                question = message,
                                diagnosticAvailable = powerDiagnostic != null,
                                diagnosticSelected = includePowerDiagnosticReport,
                                observationAvailable = usableBatteryObservation != null,
                                language = appLanguage
                            )
                            if (blockingMessage != null) {
                                onPreflightMessage(message, attachedReportLabel, blockingMessage)
                            } else if (
                                PowerAnalysisPreflight.classify(message) == PowerQuestionIntent.DRAIN_RATE &&
                                usableBatteryObservation != null
                            ) {
                                onLocalEvidenceMessage(
                                    message,
                                    attachedReportLabel,
                                    BatteryObservationReportBuilder.build(usableBatteryObservation, appLanguage)
                                )
                            } else if (CloudProviderCatalog.find(ApiSession.provider) != null && ApiSession.apiKey.isNotBlank()) {
                                onOnlineAnalyze(
                                    message,
                                    effectiveSelectedLevel,
                                    includeAppReport,
                                    includeUsageReport && effectiveSelectedLevel == "Level 0",
                                    attachedReportLabel,
                                    powerDiagnostic?.let {
                                        PowerDiagnosticReportBuilder.build(
                                            it,
                                            includeAdvancedActions = effectiveSelectedLevel?.let { level -> level != "Level 0" } == true,
                                            language = appLanguage
                                        )
                                    }
                                        .takeIf { includePowerDiagnosticReport }
                                )
                            } else {
                                if (effectiveSelectedLevel == null && !includePowerDiagnosticReport) {
                                    onPreflightMessage(
                                        message,
                                        attachedReportLabel,
                                        "未附带报告的自由聊天需要先在设置中配置云端模型。"
                                    )
                                } else {
                                    onAnalyze(
                                        message,
                                        attachedReportLabel,
                                        powerDiagnostic?.let {
                                            PowerDiagnosticReportBuilder.build(
                                                it,
                                                includeAdvancedActions = effectiveSelectedLevel?.let { level -> level != "Level 0" } == true,
                                                language = appLanguage
                                            )
                                        }.takeIf { includePowerDiagnosticReport }
                                    )
                                }
                            }
                        }
                    },
                    enabled = userMessage.isNotBlank() && !isOnlineAnalyzing
                ) {
                    Text(text = if (isOnlineAnalyzing) stringResource(R.string.analyzing) else stringResource(R.string.send))
                }
            }
        }
    }
}

@Composable
fun SettingsPrivacyScreen(
    currentLanguage: AppLanguage = LocalAppLanguage.current,
    onLanguageSelected: (AppLanguage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settingsScrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by rememberSaveable {
        mutableStateOf(ApiSession.provider.ifBlank { "OpenAI · GPT" })
    }
    var apiKey by remember { mutableStateOf("") }
    var storedApiKey by remember { mutableStateOf<com.aegis.apa.agent.StoredApiKey?>(null) }
    var savedProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isKeyOperationInProgress by remember { mutableStateOf(true) }
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

    LaunchedEffect(Unit) {
        savedProviders = withContext(Dispatchers.IO) {
            CloudProviderCatalog.providers
                .map { it.name }
                .filter { ApiKeyStore.load(context, it) != null }
                .toSet()
        }
    }

    LaunchedEffect(provider) {
        isKeyOperationInProgress = true
        val loaded = withContext(Dispatchers.IO) { ApiKeyStore.activate(context, provider) }
        storedApiKey = loaded
        apiKey = loaded?.apiKey.orEmpty()
        ApiSession.update(loaded)
        isKeyOperationInProgress = false
    }

    Column(
        modifier = modifier
            .verticalScroll(settingsScrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.settings_privacy), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(text = "APA ${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE}")
        Text(text = stringResource(R.string.language_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = currentLanguage == AppLanguage.ZH_CN,
                onClick = { onLanguageSelected(AppLanguage.ZH_CN) },
                label = { Text(stringResource(R.string.language_chinese)) }
            )
            FilterChip(
                selected = currentLanguage == AppLanguage.EN,
                onClick = { onLanguageSelected(AppLanguage.EN) },
                label = { Text(stringResource(R.string.language_english)) }
            )
        }
        Text(text = stringResource(R.string.model_service), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        CloudProviderCatalog.providers.forEach { option ->
            val optionHasKey = option.name in savedProviders
            Button(
                enabled = !isKeyOperationInProgress,
                onClick = {
                    provider = option.name
                }
            ) {
                val selectedMark = if (provider == option.name) "● " else ""
                val savedMark = if (optionHasKey) "  ✓" else ""
                Text(text = "$selectedMark${option.name}$savedMark")
            }
        }
        val selectedConfig = CloudProviderCatalog.find(provider)
        Text(text = stringResource(R.string.selected_provider_format, provider))
        Text(text = stringResource(R.string.model_format, selectedConfig?.model ?: stringResource(R.string.not_configured)))
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
            },
            label = { Text("API Key · Private") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !isKeyOperationInProgress,
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
            text = if (currentLanguage == AppLanguage.EN) {
                "Privacy: the API key is encrypted with Android Keystore and stored only on this device for 7 days. APA does not write it into code or upload it to an APA server. It is sent to the selected AI provider for authentication only when you start cloud analysis."
            } else "隐私声明：API Key 使用 Android Keystore 加密，仅保存在本机 7 天，不会写入代码或上传至 APA 服务器。只有你主动发起云端分析时，Key 才会发送给所选 AI 服务商用于鉴权。",
            color = privacyNoticeColor
        )
        Button(
            enabled = !isKeyOperationInProgress,
            onClick = {
                val providerToSave = provider
                val keyToSave = apiKey
                isKeyOperationInProgress = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        ApiKeyStore.save(context, providerToSave, keyToSave, validDays = 7)
                    }
                    if (provider == providerToSave) storedApiKey = saved
                    if (saved != null) {
                        savedProviders = savedProviders + providerToSave
                        ApiSession.update(saved)
                    }
                    isKeyOperationInProgress = false
                }
            }
        ) {
            Text(text = stringResource(R.string.save_key_seven_days))
        }
        Text(
            text = if (storedApiKey != null) {
                val expiresAtText = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(java.util.Date(requireNotNull(storedApiKey).expiresAt))
                if (currentLanguage == AppLanguage.EN) "API KEY: encrypted; expires $expiresAtText" else "API KEY：已加密保存，有效期至 $expiresAtText"
            } else {
                if (currentLanguage == AppLanguage.EN) "API KEY: not set" else "API KEY：未设置"
            }
        )
        Button(
            onClick = {
                val providerToDelete = provider
                isKeyOperationInProgress = true
                scope.launch {
                    withContext(Dispatchers.IO) { ApiKeyStore.clear(context, providerToDelete) }
                    if (provider == providerToDelete) {
                        apiKey = ""
                        storedApiKey = null
                    }
                    savedProviders = savedProviders - providerToDelete
                    if (ApiSession.provider == providerToDelete) ApiSession.update(null)
                    isKeyOperationInProgress = false
                }
            },
            enabled = storedApiKey != null && !isKeyOperationInProgress
        ) {
            Text(text = if (currentLanguage == AppLanguage.EN) "Delete $provider API key" else "删除 $provider 的 API Key")
        }
        Text(text = stringResource(R.string.device_data_notice))
        Text(
            text = if (currentLanguage == AppLanguage.EN) {
                if (storedApiKey != null) "Cloud analysis: ${CloudProviderCatalog.find(requireNotNull(storedApiKey).provider)?.shortLabel ?: "MODEL"} configured"
                else "Cloud analysis: not configured"
            } else if (storedApiKey != null) {
                "云端分析：${CloudProviderCatalog.find(requireNotNull(storedApiKey).provider)?.shortLabel ?: "MODEL"} 已配置"
            } else "云端分析：未配置"
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoLine(icon: String, label: String, value: String) {
    val prefix = listOf(icon, label).filter { it.isNotBlank() }.joinToString(" ")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, modifier = Modifier.weight(1f), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
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
                identity = com.aegis.apa.tool.DeviceNameResolver.resolve(com.aegis.apa.model.DeviceIdentifiers("Xiaomi", "示例设备")),
                androidRelease = "16", apiLevel = 36
            ),
            batteryInfo = BatteryInfo(level = 80, status = "正在充电"),
            displayInfo = DisplayInfo(1220, 2712, 480, 120f, 120f),
            ramInfo = RamInfo(
                totalBytes = 16L * 1024 * 1024 * 1024,
                availableBytes = 8L * 1024 * 1024 * 1024,
                isLowMemory = false
            ),
            storageInfo = StorageInfo(
                totalBytes = 512L * 1024 * 1024 * 1024,
                availableBytes = 256L * 1024 * 1024 * 1024
            ),
            usageSummary = UsageSummary(false, null, emptyList()),
            rootBatteryInfo = null,
            sampledAt = "12:48:03",
            deviceProfile = null,
            importedHardwareSupplyInfo = null,
            isDeviceProfileReading = false,
            onReadDeviceProfile = {},
            onImportHardwareReport = {},
            isHardwareReportBusy = false,
            onCopyHardwareFeedback = {},
            onOpenUsageAccessSettings = {},
            onRefresh = {}
        )
    }
}
