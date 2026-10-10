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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.CoroutineStart
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
import androidx.compose.ui.platform.testTag
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
import com.aegis.apa.agent.GuidedExplanationSession
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
import com.aegis.apa.model.RootAccessState
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
import com.aegis.apa.agent.GuidedEvidenceAdapter
import com.aegis.apa.model.GuidedInput
import com.aegis.apa.model.GuidedSampleResult
import com.aegis.apa.model.GuidedSamplePreview
import com.aegis.apa.model.GuidedSessionKey
import com.aegis.apa.model.GuidedWorkToken
import com.aegis.apa.model.GuidedStage
import com.aegis.apa.model.GuidedActionKind
import com.aegis.apa.model.GuidedAction
import com.aegis.apa.localization.GuidedDiagnosticCopy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku


@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        guided.refreshExplanationContext()
    }
    private val session by lazy { androidx.lifecycle.ViewModelProvider(this)[MainSessionViewModel::class.java] }
    private val guided by lazy { androidx.lifecycle.ViewModelProvider(this)[GuidedDiagnosticViewModel::class.java] }
    private val batteryObservationStore by lazy { BatteryObservationStore(this) }
    private val verification by lazy {
        androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                VerificationViewModel(com.aegis.apa.agent.androidVerificationStore(applicationContext)) as T
        })[VerificationViewModel::class.java]
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage(AppLanguageStore.load(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiSession.initialize { ApiKeyStore.load(this) }
        val appLanguage = AppLanguageStore.load(this)
        guided.configureExplanation(
            credentials = GuidedExplanationSession::credentials,
            configuration = GuidedExplanationSession::configuration,
            executor = java.util.concurrent.Executor { Dispatchers.IO.dispatch(kotlin.coroutines.EmptyCoroutineContext, it) },
            publishExecutor = java.util.concurrent.Executor { android.os.Handler(android.os.Looper.getMainLooper()).post(it) })
        guided.updateExplanationLanguage(appLanguage)
        session.setLanguage(appLanguage)
        val persistedObservation = batteryObservationStore.load()
        session.reconcileBatteryObservation(
            persistedObservation?.start,
            persistedObservation?.chargingObserved ?: false
        )
        val apaApplication = application as ApaApplication
        session.initializeConversation(apaApplication.conversationPersistence)
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
                var showVerification by rememberSaveable { mutableStateOf(false) }
                var showGuided by rememberSaveable { mutableStateOf(false) }
                var guidedPreview by remember { mutableStateOf<GuidedSamplePreview?>(null) }
                var guidedVerification by remember { mutableStateOf<Triple<GuidedSessionKey, String, PowerDiagnosticSnapshot>?>(null) }
                var guidedNotice by remember { mutableStateOf<String?>(null) }
                var guidedExitKey by remember { mutableStateOf<GuidedSessionKey?>(null) }
                var guidedBasicJob by remember { mutableStateOf<Job?>(null) }
                val guidedState by guided.state
                // Only the picker authorization key is saveable, never guided answers or samples.
                var pickerGuidedId by rememberSaveable { mutableStateOf<String?>(null) }
                var pickerGuidedRevision by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
                var pickerSampleRevision by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
                var pickerAnswerRevision by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
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
                val refreshCapabilities: (Boolean) -> Unit = { allowRootRetry ->
                    scope.launch {
                        try {
                            val fresh = refreshSnapshot()
                            session.refreshRootAuthorization(
                                hasRootInterface = fresh.rootStatus.hasSuBinary ||
                                    fresh.rootStatus.isKernelSuManagerInstalled || fresh.rootStatus.isMagiskManagerInstalled,
                                allowRetry = allowRootRetry
                            )
                        }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { snapshotError = RuntimeUiCopy.text(RuntimeNotice.SNAPSHOT_READ_FAILED, appLanguage) }
                    }
                }
                val onRefresh: () -> Unit = { refreshCapabilities(true) }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            shizukuRevision += 1
                            refreshCapabilities(false)
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
                var rootBatteryReadJob by remember { mutableStateOf<Job?>(null) }
                var deviceProfileReadJob by remember { mutableStateOf<Job?>(null) }
                var hardwareReadsActive by remember { mutableStateOf(true) }
                var hardwareReadError by remember { mutableStateOf<String?>(null) }
                val stopHardwareReads: () -> Unit = {
                    val batteryJob = rootBatteryReadJob
                    val profileJob = deviceProfileReadJob
                    // Invalidate ownership before cancellation so an old finally cannot clear a new read.
                    rootBatteryReadJob = null
                    deviceProfileReadJob = null
                    isRootBatteryReading = false
                    isDeviceProfileReading = false
                    batteryJob?.cancel()
                    profileJob?.cancel()
                }
                DisposableEffect(session) {
                    hardwareReadsActive = true
                    onDispose {
                        hardwareReadsActive = false
                        stopHardwareReads()
                    }
                }
                var importedHardwareSupplyInfo by session.hardwareSupplyInfo
                var hardwareRefreshRevision by remember { mutableIntStateOf(0) }
                var hardwareSupplyReading by remember { mutableStateOf(false) }
                var hardwareSupplyReadGeneration by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                var hardwareSupplyNotice by remember { mutableStateOf<String?>(null) }
                var pendingHardwareReport by remember {
                    mutableStateOf<Pair<HardwareSupplyInfo, Map<String, String>>?>(null)
                }
                LaunchedEffect(apaApplication) {
                    val cached = withContext(Dispatchers.IO) { apaApplication.hardwareSupplyCache.load() }
                    if (cached != null) importedHardwareSupplyInfo =
                        importedHardwareSupplyInfo?.mergeMissingFrom(cached) ?: cached
                }
                pendingHardwareReport?.let { candidate ->
                    AlertDialog(
                        onDismissRequest = { pendingHardwareReport = null },
                        title = { Text(if (appLanguage == AppLanguage.EN) "Confirm report device" else "确认报告设备") },
                        text = { Text(if (appLanguage == AppLanguage.EN)
                            "The report model matches this phone. Is it this phone's report? Only derived hardware fields will be saved locally."
                            else "报告机型与本机一致。请确认这是这台手机的报告；只在本机保存厂商、规格和型号，不保存原始报告。") },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingHardwareReport = null
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        apaApplication.hardwareSupplyCache.saveReport(candidate.first, candidate.second, true)
                                    }
                                    if (saved) {
                                        val cached = withContext(Dispatchers.IO) { apaApplication.hardwareSupplyCache.load() }
                                        if (cached != null) importedHardwareSupplyInfo = cached
                                    } else hardwareSupplyNotice = if (appLanguage == AppLanguage.EN)
                                        "Hardware information could not be saved." else "硬件信息未能保存；已有信息仍保留。"
                                }
                            }) { Text(if (appLanguage == AppLanguage.EN) "This phone" else "是本机报告") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingHardwareReport = null }) {
                                Text(if (appLanguage == AppLanguage.EN) "Do not save" else "不保存到本机")
                            }
                        }
                    )
                }
                var chatMessages by session.messages
                var isOnlineAnalyzing by session.analyzing
                var analysisJob by remember { mutableStateOf<Job?>(null) }
                var activeRequest by remember { mutableStateOf<com.aegis.apa.agent.CloudRequestCancellation?>(null) }
                val stopAnalysis: () -> Unit = {
                    session.interruptAnalysis()
                    activeRequest?.cancel()
                    analysisJob?.cancel()
                }
                var powerDiagnostic by session.powerDiagnostic
                var powerDiagnosticState by session.powerDiagnosticState
                var batteryObservationStart by session.batteryObservationStart
                var batteryObservationResult by session.batteryObservationResult
                var batteryObservationNotice by session.batteryObservationNotice
                fun activeGuidedKey() = guided.state.value.session?.takeIf { it.stage != GuidedStage.ENDED }?.key
                fun offerGuidedInput(token: GuidedWorkToken?, input: GuidedInput) {
                    if (token == null) return
                    val answers = guided.state.value.session?.answers ?: return
                    when (val result = GuidedEvidenceAdapter.adapt(input, java.util.UUID.randomUUID().toString(), answers)) {
                        is GuidedSampleResult.Ready -> guided.completeWork(token) {
                            guidedPreview = null
                            guided.offerSample(result.sample)
                        }
                        is GuidedSampleResult.Rejected -> guided.failWork(token, result.reasonId)
                    }
                }
                fun collectGuidedBasicSnapshot() {
                    if (guidedBasicJob?.isActive == true) return
                    val newKey = activeGuidedKey()
                    var work: GuidedWorkToken? = null
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val fresh = refreshSnapshot()
                            work?.let { guided.offerBasicSnapshot(it, fresh, java.util.UUID.randomUUID().toString()) }
                        } catch (cancelled: CancellationException) {
                            work?.let { guided.failWork(it, "guided.collection_failed") }; throw cancelled
                        } catch (_: Exception) {
                            work?.let { guided.failWork(it, "guided.collection_failed") }
                        }
                    }
                    work = newKey?.let { guided.beginWork(it) { job.cancel() } }
                    if (work != null) { guidedBasicJob = job; job.start() } else job.cancel()
                }
                LaunchedEffect(guidedState.session?.key, guidedState.availableSampleId) {
                    guidedPreview = null
                    if (guidedVerification?.first != guidedState.session?.key) guidedVerification = null
                }
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
                val importBugReportUri: (Uri, GuidedSessionKey?) -> Unit = { uri, expectedGuidedKey ->
                    if (
                        powerDiagnosticState !is PowerDiagnosticUiState.Collecting &&
                        powerDiagnosticState != PowerDiagnosticUiState.Importing
                    ) {
                        session.agent.includePowerDiagnosticReport.value = false
                        powerDiagnosticState = PowerDiagnosticUiState.Importing
                        session.reportImportStatus.value = null
                        pendingHardwareReport = null
                        var importJob: Job? = null
                        val guidedToken = expectedGuidedKey?.takeIf { it == activeGuidedKey() }?.let { key -> guided.beginWork(key) { importJob?.cancel() } }
                        importJob = scope.launch(start = CoroutineStart.LAZY) {
                            val importTrace = com.aegis.apa.tool.BugReportImportTrace(BuildConfig.DEBUG)
                            var readingComplete = false
                            var lastProgressNanos = 0L
                            try {
                                val startedNanos = System.nanoTime()
                                val imported = withContext(Dispatchers.IO) {
                                    val job = currentCoroutineContext()[Job]
                                    BugReportImporter(contentResolver, importTrace::record).import(uri, onBytesRead = { bytes ->
                                        val now = System.nanoTime()
                                        if (now - lastProgressNanos >= 250_000_000L) {
                                            lastProgressNanos = now
                                            scope.launch(Dispatchers.Main) {
                                                if (!readingComplete && job?.isActive != false && powerDiagnosticState == PowerDiagnosticUiState.Importing) {
                                                    session.reportImportStatus.value = if (appLanguage == AppLanguage.EN)
                                                        "Reading file… ${bytes / (1024 * 1024)} MiB read"
                                                    else "正在读取文件… 已读取 ${bytes / (1024 * 1024)} MiB"
                                                }
                                            }
                                        }
                                    }) {
                                        job?.isActive == false
                                    }
                                }
                                readingComplete = true
                                when (imported) {
                                    is BugReportReadResult.Success -> {
                                        session.reportImportStatus.value = if (appLanguage == AppLanguage.EN) "Parsing diagnostic data…" else "正在解析诊断数据…"
                                        val importedHardware = imported.sections
                                            .firstOrNull { it.source == "hardware" }
                                            ?.let { section ->
                                                DeviceProfileParser.parse(
                                                    raw = section.output,
                                                    access = DeviceProfileAccess.STANDARD
                                                ).hardwareSupplyInfo
                                            }
                                        val parseStartedNanos = System.nanoTime()
                                        val analyzed = PowerDiagnosticPipeline.analyzeImported(
                                            imported = imported,
                                            sampledAt = Instant.now(),
                                            collectionDurationMillis = (System.nanoTime() - startedNanos) / 1_000_000,
                                            packageLabelResolver = ::resolveInstalledAppLabel
                                        )
                                        if (BuildConfig.DEBUG) android.util.Log.d("APA-ReportImport", "stage=parse elapsed_ms=${(System.nanoTime() - parseStartedNanos) / 1_000_000}")
                                        session.completePowerDiagnostic(analyzed)
                                        if (importedHardware != null) {
                                            if (apaApplication.hardwareSupplyCache.matchesReport(imported.verificationHeaderFields)) {
                                                pendingHardwareReport = importedHardware to imported.verificationHeaderFields
                                            } else {
                                                hardwareSupplyNotice = if (appLanguage == AppLanguage.EN)
                                                    "Report device could not be matched; hardware was not saved to this phone."
                                                    else "报告设备无法与本机确认匹配，未覆盖本机硬件信息。"
                                            }
                                        }
                                        offerGuidedInput(guidedToken, GuidedInput(analyzed, null, null))
                                    }
                                    is BugReportReadResult.Rejected -> {
                                        guidedToken?.let { guided.failWork(it, "guided.collection_failed") }
                                        powerDiagnosticState = PowerDiagnosticUiState.Error(
                                            importTrace.appendTo(RuntimeUiCopy.bugReportReject(imported.reason, appLanguage))
                                        )
                                    }
                                    BugReportReadResult.Cancelled -> {
                                        guidedToken?.let { guided.failWork(it, "guided.collection_failed") }
                                        powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                throw cancelled
                            } catch (failure: Exception) {
                                importTrace.record("stage=exception type=${failure.javaClass.simpleName.take(80)}")
                                powerDiagnosticState = PowerDiagnosticUiState.Error(
                                    importTrace.appendTo(RuntimeUiCopy.text(RuntimeNotice.BUG_REPORT_READ_FAILED, appLanguage))
                                )
                            } finally {
                                readingComplete = true
                                session.reportImportStatus.value = null
                                guidedToken?.let { guided.failWork(it, "guided.collection_failed") }
                            }
                        }
                        importJob.start()
                    }
                }
                val bugReportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    val key = pickerGuidedId?.let { GuidedSessionKey(it, pickerGuidedRevision, pickerSampleRevision, pickerAnswerRevision) }
                    pickerGuidedId = null
                    uri?.let { importBugReportUri(it, key) }
                }
                val launchBugReport: () -> Unit = {
                    val key = activeGuidedKey()
                    pickerGuidedId = key?.id
                    pickerGuidedRevision = key?.revision ?: 0
                    pickerSampleRevision = key?.sampleRevision ?: 0
                    pickerAnswerRevision = key?.answerRevision ?: 0
                    bugReportPicker.launch(arrayOf("application/zip", "text/plain", "application/octet-stream"))
                }
                val incomingSharedBugReportUri = remember { sharedBugReportUri(intent) }
                LaunchedEffect(incomingSharedBugReportUri) {
                    incomingSharedBugReportUri?.let { uri ->
                        if (session.consumeSharedBugReport(uri.toString())) {
                            importBugReportUri(uri, activeGuidedKey())
                        }
                    }
                }
                DisposableEffect(session) {
                    onDispose {
                        stopAnalysis()
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
                val rootAccessState by session.rootAccessState
                val rootAuthorized = rootAccessState == RootAccessState.AUTHORIZED
                LaunchedEffect(selectedPage == 0, rootAuthorized, hardwareRefreshRevision) {
                    val generation = ++hardwareSupplyReadGeneration
                    if (selectedPage != 0) {
                        hardwareSupplyReading = false
                        return@LaunchedEffect
                    }
                    hardwareSupplyReading = true
                    val job = currentCoroutineContext()[Job]
                    try {
                        val (read, saved) = withContext(Dispatchers.IO) {
                            val info = DeviceProfileCollector.readHardware(rootAuthorized,
                                cancellationRequested = { job?.isActive == false })
                            info to (info == null || apaApplication.hardwareSupplyCache.saveLocal(info))
                        }
                        if (read != null) importedHardwareSupplyInfo = read.mergeMissingFrom(importedHardwareSupplyInfo)
                        hardwareSupplyNotice = if (!saved) {
                            if (appLanguage == AppLanguage.EN) "Hardware read completed, but saving failed."
                            else "硬件读取完成，但保存失败；本次结果仍可查看。"
                        } else null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        hardwareSupplyNotice = if (appLanguage == AppLanguage.EN)
                            "Hardware read failed; previously confirmed information is kept."
                            else "硬件读取失败，已保留之前确认的信息。"
                    } finally {
                        if (generation == hardwareSupplyReadGeneration) hardwareSupplyReading = false
                    }
                }
                val agentCapabilityAccess = AgentCapabilityAccess.from(
                    shizukuAccessState = shizukuAccessState,
                    rootAuthorized = rootAuthorized
                )
                androidx.compose.runtime.SideEffect {
                    verification.updateExplanationContext(showVerification,
                        AdviceCapabilityPolicy.effectiveLevel(
                            if (agentCapabilityAccess.rootAuthorized) "Level 2" else if (agentCapabilityAccess.shizukuAuthorized) "Level 1" else "Level 0",
                            agentCapabilityAccess.shizukuAuthorized, agentCapabilityAccess.rootAuthorized), appLanguage)
                }
                val exitVerification = { verification.exitExplanation(); showVerification = false }
                val onCollectPowerDiagnostic: () -> Unit = {
                    var collectJob: Job? = null
                    val guidedToken = if (powerDiagnosticState !is PowerDiagnosticUiState.Collecting)
                        activeGuidedKey()?.let { key -> guided.beginWork(key) { collectJob?.cancel() } } else null
                    if (powerDiagnosticState !is PowerDiagnosticUiState.Collecting) {
                        session.agent.includePowerDiagnosticReport.value = false
                        powerDiagnosticState = PowerDiagnosticUiState.Collecting(
                            0,
                            8,
                            if (appLanguage == AppLanguage.EN) "Preparing Root authorization" else "准备 Root 授权"
                        )
                        collectJob = scope.launch(start = CoroutineStart.LAZY) {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val collectionJob = currentCoroutineContext()[Job]
                                    SystemPowerDiagnosticsCollector(
                                        packageLabelResolver = ::resolveInstalledAppLabel,
                                        currentDeviceContext = com.aegis.apa.model.VerificationDeviceContext(
                                            Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
                                            Build.VERSION.INCREMENTAL, com.aegis.apa.model.VerificationOwnership.LOCAL_COLLECTION
                                        )
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
                                offerGuidedInput(guidedToken, GuidedInput(result, null, null))
                            } catch (cancelled: CancellationException) {
                                powerDiagnosticState = PowerDiagnosticUiState.Interrupted
                                throw cancelled
                            } catch (_: Exception) {
                                powerDiagnosticState = PowerDiagnosticUiState.Error(
                                    RuntimeUiCopy.text(RuntimeNotice.ROOT_DIAGNOSTIC_FAILED, appLanguage)
                                )
                            } finally {
                                guidedToken?.let { guided.failWork(it, "guided.collection_failed") }
                            }
                        }
                        collectJob.start()
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
                val observationGuidedKey = activeGuidedKey()
                val onFinishBatteryObservation: (Boolean) -> Unit = { userReportedCharging ->
                    val guidedToken = observationGuidedKey?.takeIf { it == activeGuidedKey() }?.let { key -> guided.beginWork(key) {} }
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
                            offerGuidedInput(guidedToken, GuidedInput(null, null, batteryObservationResult))
                            if (session.batteryObservationStart.value == null) {
                                if (!batteryObservationStore.clear()) {
                                    batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_RESULT_CLEAR_FAILED, appLanguage)
                                } else {
                                    apaApplication.clearChargingEvidenceWriteFailure()
                                }
                            }
                        }.onFailure { batteryObservationNotice = RuntimeUiCopy.text(RuntimeNotice.BATTERY_END_READ_FAILED, appLanguage) }
                    }
                    guidedToken?.let { guided.failWork(it, "guided.collection_failed") }
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
                    if (hardwareReadsActive && !isDeviceProfileReading) {
                        isDeviceProfileReading = true
                        hardwareReadError = null
                        deviceProfileReadJob = scope.launch(start = CoroutineStart.LAZY) {
                            val job = checkNotNull(currentCoroutineContext()[Job])
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    DeviceProfileCollector.readScheduling(
                                        preferRoot = currentSnapshot.rootStatus.hasSuBinary,
                                        cancellationRequested = { !job.isActive }
                                    )
                                }
                                if (hardwareReadsActive && deviceProfileReadJob === job && job.isActive) {
                                    deviceProfile = result
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                if (hardwareReadsActive && deviceProfileReadJob === job && job.isActive) {
                                    hardwareReadError = if (appLanguage == AppLanguage.EN) {
                                        "Could not read the device profile. Try again."
                                    } else "设备档案读取失败，请重试。"
                                }
                            } finally {
                                if (hardwareReadsActive && deviceProfileReadJob === job) {
                                    isDeviceProfileReading = false
                                    deviceProfileReadJob = null
                                }
                            }
                        }
                        deviceProfileReadJob?.start()
                    }
                }
                if (showGuided) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = {
                        val key = activeGuidedKey()
                        if (key == null) showGuided = false else guidedExitKey = key
                    }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
                        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                                if (guidedState.session == null) TextButton(onClick = { showGuided = false }) {
                                    Text(if (appLanguage == AppLanguage.EN) "Return to diagnostics" else "返回诊断")
                                }
                                guidedNotice?.let { Text(it, modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()).padding(12.dp)) }
                                GuidedDiagnosticScreen(guidedState, guided.explanationState.value,
                                    guided::setExplanationOptions, guided::dismissExplanationPreview,
                                    guided::sendExplanation, guided::cancelExplanation) { event ->
                                    when (event) {
                                        is GuidedUiEvent.Start -> { guided.start(event.scenario); guidedNotice = null }
                                        is GuidedUiEvent.UpdateAnswers -> if (guided.updateAnswers(event.key, event.answers) && event.answers.reviewed) {
                                            activeGuidedKey()?.let(guided::evaluateLocal)
                                        }
                                        is GuidedUiEvent.PreviewSample -> if (activeGuidedKey() == event.key) {
                                            guidedPreview = guided.previewSample(event.sampleId)
                                        }
                                        is GuidedUiEvent.Restart -> if (guided.restart(event.key, event.scenario)) {
                                            guidedPreview = null; guidedVerification = null; guidedNotice = null
                                        }
                                        is GuidedUiEvent.Exit -> if (guided.end(event.key)) {
                                            guidedPreview = null; guidedVerification = null; showGuided = false
                                        }
                                        is GuidedUiEvent.RequestExplanation -> guided.previewExplanation(event.key)
                                        is GuidedUiEvent.PauseForEvidence -> if (activeGuidedKey() == event.key) { showGuided = false; selectedPage = 3 }
                                        is GuidedUiEvent.RequestAction, is GuidedUiEvent.RetryAction, is GuidedUiEvent.RefreshHeatSnapshot -> {
                                            val current = guided.state.value
                                            val key = when (event) {
                                                is GuidedUiEvent.RequestAction -> event.key
                                                is GuidedUiEvent.RetryAction -> event.key
                                                is GuidedUiEvent.RefreshHeatSnapshot -> event.key
                                                else -> null
                                            }
                                            val actionId = when (event) {
                                                is GuidedUiEvent.RequestAction -> event.actionId
                                                is GuidedUiEvent.RetryAction -> event.actionId
                                                else -> "heat.refresh_snapshot"
                                            }
                                            val action = if (event is GuidedUiEvent.RefreshHeatSnapshot)
                                                GuidedAction(actionId, GuidedActionKind.COLLECT, emptyList()) else current.assessment?.primaryAction
                                            if (current.session?.key == key && key != null && action?.id == actionId) {
                                                val verificationSource = if (action.kind == GuidedActionKind.OPEN_VERIFICATION)
                                                    guidedVerificationSource(current, key, actionId, powerDiagnostic) else null
                                                if (action.kind == GuidedActionKind.OPEN_VERIFICATION && verificationSource == null) {
                                                    guidedNotice = GuidedDiagnosticCopy.text("guided.verification_unavailable", appLanguage)
                                                } else guided.dispatchAction(event) { dispatchedAction ->
                                                    when (dispatchedAction.kind) {
                                                        GuidedActionKind.IMPORT_REPORT -> {
                                                            showGuided = false
                                                            launchBugReport()
                                                        }
                                                        GuidedActionKind.COLLECT -> collectGuidedBasicSnapshot()
                                                        GuidedActionKind.OPEN_OBSERVATION -> { showGuided = false; selectedPage = 3 }
                                                        GuidedActionKind.OPEN_VERIFICATION -> {
                                                            guidedVerification = Triple(checkNotNull(activeGuidedKey()), actionId, checkNotNull(verificationSource))
                                                        }
                                                        GuidedActionKind.END -> showGuided = false
                                                        GuidedActionKind.ANSWER -> Unit
                                                        GuidedActionKind.OBSERVE_CONDITION, GuidedActionKind.VIEW_SYSTEM_WARNING -> {
                                                            guidedNotice = GuidedDiagnosticCopy.action(actionId, appLanguage)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                guidedExitKey?.let { key -> androidx.compose.material3.AlertDialog(
                    onDismissRequest = { guidedExitKey = null },
                    title = { Text(if (appLanguage == AppLanguage.EN) "End and exit?" else "结束并退出？") },
                    text = { Text(if (appLanguage == AppLanguage.EN) "Cancel unfinished work; the remote service may have received an explanation request. Collection navigation only pauses the session." else "取消未完成任务；远端可能已收到解释请求。返回采集入口仅暂停会话。") },
                    confirmButton = { TextButton(onClick = { if (guided.end(key)) showGuided = false; guidedExitKey = null }) {
                        Text(if (appLanguage == AppLanguage.EN) "End and exit" else "结束并退出")
                    } }, dismissButton = { TextButton(onClick = { guidedExitKey = null }) { Text(if (appLanguage == AppLanguage.EN) "Cancel" else "取消") } }) }
                guidedPreview?.let { token -> GuidedSamplePreviewForm(token, guidedState.session?.samples.orEmpty(),
                    { guidedPreview = null }, { replacement -> guided.confirmSample(token, replacement); guidedPreview = null }) }
                guidedVerification?.let { receipt ->
                    val target = guidedVerificationTarget(guidedState, receipt.first, receipt.second)
                    val source = guidedVerificationSource(guidedState, receipt.first, receipt.second, powerDiagnostic)
                    if (target != null && source === receipt.third) VerificationSampleForm(source, null,
                        enabled = !verification.state.value.loading && !verification.state.value.saving && verification.state.value.storageFailure == null,
                        onDismiss = { guidedVerification = null }, fixedTarget = target,
                        onSave = { savedTarget, context, title ->
                            val latest = guided.state.value
                            if (guidedVerificationTarget(latest, receipt.first, receipt.second) == savedTarget &&
                                guidedVerificationSource(latest, receipt.first, receipt.second, powerDiagnostic) === receipt.third) {
                                verificationSummary(receipt.third, savedTarget, context)?.let { verification.createBaseline(title, savedTarget, it) }
                            }
                            guidedVerification = null
                        })
                }
                if (showVerification) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = exitVerification,
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 12.dp)) {
                                TextButton(onClick = exitVerification) {
                                    Text(if (appLanguage == AppLanguage.EN) "Return to chat / diagnostics" else "返回聊天 / 诊断")
                                }
                                VerificationPanel(verification.state.value, powerDiagnostic,
                                    onCreateBaseline = { target, context, title ->
                                        powerDiagnostic?.let { verificationSummary(it, target, context) }?.let {
                                            verification.createBaseline(title, target, it)
                                        }
                                    }, onSelect = verification::select, onChange = verification::recordChange,
                                    onRetest = { id, revision, context ->
                                        val record = verification.state.value.records.firstOrNull { it.id == id }
                                            ?: verification.state.value.unsavedRecord?.takeIf { it.id == id }
                                        if (record != null) powerDiagnostic?.let { verificationSummary(it, record.target, context) }?.let {
                                            verification.recordRetest(id, revision, it)
                                        }
                                    }, onClose = verification::close, onDelete = verification::delete, onClear = verification::clear,
                                    onRetrySave = verification::retrySave, onDiscardUnsaved = verification::discardUnsaved,
                                    explanation = verification.explanationState.value,
                                    onExplanationPreview = verification::previewExplanation,
                                    onExplanationDismiss = verification::dismissExplanationPreview,
                                    onExplanationNotes = verification::includeExplanationNotes,
                                    onExplanationSend = { verification.sendExplanation { ApiSession.requireValid() } },
                                    onExplanationStop = verification::stopExplanation)
                            }
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    topBar = {
                        if (isRootBatteryReading || isDeviceProfileReading || hardwareReadError != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hardwareReadError ?: if (appLanguage == AppLanguage.EN) {
                                        "Reading hardware information…"
                                    } else "正在读取硬件信息…",
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    stopHardwareReads()
                                    hardwareReadError = null
                                }) {
                                    Text(if (appLanguage == AppLanguage.EN) {
                                        if (isRootBatteryReading || isDeviceProfileReading) "Stop" else "Dismiss"
                                    } else if (isRootBatteryReading || isDeviceProfileReading) "停止" else "关闭")
                                }
                            }
                        }
                    },
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
                                    onClick = { if (selectedPage != page) exitVerification(); selectedPage = page },
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
                                onReadHardware = { hardwareRefreshRevision += 1 },
                                isHardwareReading = hardwareSupplyReading,
                                hardwareNotice = hardwareSupplyNotice,
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
                                rootAccessState = rootAccessState,
                                onCheckRootAuthorization = {
                                    session.refreshRootAuthorization(
                                        currentSnapshot.rootStatus.hasSuBinary ||
                                            currentSnapshot.rootStatus.isKernelSuManagerInstalled ||
                                            currentSnapshot.rootStatus.isMagiskManagerInstalled,
                                        allowRetry = true
                                    )
                                },
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
                                    session.requestRootAuthorizationRefresh()
                                    val rootManagerPackage =
                                        currentSnapshot.rootStatus.kernelSuManagerPackage
                                            ?: "com.topjohnwu.magisk"
                                    openRootManager(rootManagerPackage)
                                },
                                onReadRootBattery = {
                                    if (hardwareReadsActive && !isRootBatteryReading) {
                                        isRootBatteryReading = true
                                        hardwareReadError = null
                                        rootBatteryReadJob = scope.launch(start = CoroutineStart.LAZY) {
                                            val job = checkNotNull(currentCoroutineContext()[Job])
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    RootBatteryTool.read(cancellationRequested = { !job.isActive })
                                                }
                                                if (hardwareReadsActive && rootBatteryReadJob === job && job.isActive) {
                                                    rootBatteryInfo = result
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                if (hardwareReadsActive && rootBatteryReadJob === job && job.isActive) {
                                                    hardwareReadError = if (appLanguage == AppLanguage.EN) {
                                                        "Could not read Root battery information. Try again."
                                                    } else "Root 电池信息读取失败，请重试。"
                                                }
                                            } finally {
                                                if (hardwareReadsActive && rootBatteryReadJob === job) {
                                                    isRootBatteryReading = false
                                                    rootBatteryReadJob = null
                                                }
                                            }
                                        }
                                        rootBatteryReadJob?.start()
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
                                rootAuthorizationPending = rootAccessState == RootAccessState.CHECKING ||
                                    rootAccessState == RootAccessState.UNKNOWN,
                                onAnalyze = { question, attachedReportLabel, attachedPowerDiagnosticReport ->
                                    chatMessages = chatMessages + AgentConversationMessage(
                                        role = MessageRole.USER, content = question, attachedReportLabel = attachedReportLabel
                                    )
                                    val generation = session.beginAnalysis()
                                    activeRequest = null
                                    analysisJob = scope.launch {
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
                                conversationLoading = session.conversationLoading.value,
                                conversationStorageError = session.conversationStorageError.value,
                                conversationSaving = session.conversationSaving.value,
                                conversationExpired = session.conversationExpired.value,
                                onStopAnalysis = stopAnalysis,
                                onPrepareRetry = { chatMessages = com.aegis.apa.agent.AgentRetryPolicy.beforeRetry(chatMessages) },
                                onOnlineAnalyze = { question, selectedLevel, includeAppReport, includeUsageReport, attachedReportLabel, attachedPowerDiagnosticReport ->
                                    val previousMessages = chatMessages
                                    val requestedProvider = ApiSession.provider
                                    val attachedPowerRanking = powerDiagnostic
                                        ?.takeIf { attachedPowerDiagnosticReport != null &&
                                            PowerAnalysisPreflight.classify(question) != PowerQuestionIntent.BATTERY_HEALTH }
                                        ?.let { com.aegis.apa.agent.AppPowerRanking.build(it, appLanguage) }
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
                                    val cancellation = com.aegis.apa.agent.CloudRequestCancellation { close ->
                                        apaApplication.requestCleanupScope.launch { close() }
                                    }
                                    activeRequest = cancellation
                                    analysisJob = scope.launch {
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
                                                    language = appLanguage,
                                                    cancellation = cancellation
                                                )
                                            }
                                            session.appendAnalysisMessage(generation, AgentConversationMessage(
                                                role = MessageRole.ASSISTANT,
                                                content = result.toChatContent() +
                                                    (attachedPowerRanking?.let { "\n\n$it" } ?: ""),
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
                                onClearConversation = { session.clearConversation() },
                                onOpenVerification = { showVerification = true },
                                onOpenGuidedDiagnostic = { showGuided = true; guidedNotice = null },
                                onOpenSettings = { exitVerification(); selectedPage = 4 },
                                powerDiagnosticState = powerDiagnosticState,
                                reportImportStatus = session.reportImportStatus.value,
                                powerDiagnostic = powerDiagnostic,
                                batteryObservationStart = batteryObservationStart,
                                batteryObservationResult = batteryObservationResult,
                                batteryObservationNotice = batteryObservationNotice,
                                rootAvailable = currentSnapshot.rootStatus.hasSuBinary,
                                onCollectPowerDiagnostic = onCollectPowerDiagnostic,
                                onImportBugReport = launchBugReport,
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
                                    onCredentialsChanged = guided::refreshExplanationContext,
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
    modifier: Modifier = Modifier,
    onReadHardware: () -> Unit = {},
    isHardwareReading: Boolean = false,
    hardwareNotice: String? = null
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
            TextButton(onClick = onReadHardware, enabled = !isHardwareReading,
                modifier = Modifier.testTag("read_hardware_information")) {
                Text(if (isHardwareReading) {
                    if (appLanguage == AppLanguage.EN) "Reading hardware…" else "正在读取硬件信息…"
                } else if (appLanguage == AppLanguage.EN) "Refresh hardware information" else "刷新硬件信息")
            }
            hardwareNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                            chipDetails == null -> if (appLanguage == AppLanguage.EN) "Read scheduling⌄" else "读取调度⌄"
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
    modifier: Modifier = Modifier,
    rootAccessState: RootAccessState = RootAccessState.UNKNOWN,
    onCheckRootAuthorization: () -> Unit = {}
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
                        else when (rootAccessState) {
                            RootAccessState.CHECKING -> "Checking APA's Root access (read-only identity check)…"
                            RootAccessState.DENIED -> "APA did not receive Root access. Check its grant in KernelSU or Magisk, then retry."
                            RootAccessState.TIMED_OUT -> "The Root check timed out; access is not confirmed. You can retry manually."
                            RootAccessState.FAILED -> "The Root identity check failed; access is not confirmed. You can retry manually."
                            RootAccessState.UNAVAILABLE -> "A usable Root interface was not found. Check KernelSU or Magisk."
                            else -> "Root access has not been checked yet."
                        }
                    } else if (rootAuthorized) "Root 命令已实际执行成功，APA 已获得访问。"
                    else when (rootAccessState) {
                        RootAccessState.CHECKING -> "正在检测 APA 的 Root 授权（只读身份检查）…"
                        RootAccessState.DENIED -> "APA 未获得 Root 访问，请检查 KernelSU 或 Magisk 中的授权后重试。"
                        RootAccessState.TIMED_OUT -> "Root 检测超时，尚未确认授权；可手动重新检查。"
                        RootAccessState.FAILED -> "Root 身份检查失败，尚未确认授权；可手动重新检查。"
                        RootAccessState.UNAVAILABLE -> "未找到可用的 Root 接口，请检查 KernelSU 或 Magisk。"
                        else -> "尚未检测 Root 授权。"
                    }
                )
                Button(
                    onClick = onCheckRootAuthorization,
                    enabled = rootAccessState != RootAccessState.CHECKING &&
                        (rootStatus.hasSuBinary || rootStatus.isKernelSuManagerInstalled || rootStatus.isMagiskManagerInstalled)
                ) {
                    Text(if (english) "Recheck Root access" else "重新检查 Root 授权")
                }
                Button(
                    onClick = onOpenRootManager,
                    enabled = accessFeedback.rootActionEnabled &&
                        rootAccessState != RootAccessState.CHECKING &&
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
                    enabled = rootStatus.hasSuBinary && !isRootBatteryReading && rootAccessState != RootAccessState.CHECKING
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

@Composable
fun SettingsPrivacyScreen(
    currentLanguage: AppLanguage = LocalAppLanguage.current,
    onLanguageSelected: (AppLanguage) -> Unit = {},
    modifier: Modifier = Modifier,
    onCredentialsChanged: () -> Unit = {}
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
        onCredentialsChanged()
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
                        onCredentialsChanged()
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
                    onCredentialsChanged()
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
