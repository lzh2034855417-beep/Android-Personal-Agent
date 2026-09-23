package com.aegis.apa

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.model.AppDetails
import com.aegis.apa.model.BatteryObservationAnalyzer
import com.aegis.apa.model.BatteryObservationContinuity
import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.model.BatteryObservationResult
import com.aegis.apa.model.BatteryObservationValidity
import com.aegis.apa.model.DeviceSnapshot
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.RootBatteryInfo

sealed interface PowerDiagnosticUiState {
    data object Idle : PowerDiagnosticUiState
    data object Importing : PowerDiagnosticUiState
    data class Collecting(val completed: Int, val total: Int, val source: String) : PowerDiagnosticUiState
    data object Ready : PowerDiagnosticUiState
    data class Error(val message: String) : PowerDiagnosticUiState
    data object Interrupted : PowerDiagnosticUiState
}

/** Session state. Credentials, raw reports and the active battery checkpoint do not enter SavedState. */
class MainSessionViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    val agent = AgentPageState()
    val snapshot = mutableStateOf<DeviceSnapshot?>(null)
    val selectedAppDetails = mutableStateOf<AppDetails?>(null)
    val rootBatteryInfo = mutableStateOf<RootBatteryInfo?>(null)
    val rootBatteryReading = mutableStateOf(false)
    val deviceProfile = mutableStateOf<DeviceProfileSnapshot?>(null)
    val deviceProfileReading = mutableStateOf(false)
    val messages = mutableStateOf<List<AgentConversationMessage>>(emptyList())
    val analyzing = mutableStateOf(false)
    val powerDiagnostic = mutableStateOf<PowerDiagnosticSnapshot?>(null)
    val powerDiagnosticState = mutableStateOf<PowerDiagnosticUiState>(PowerDiagnosticUiState.Idle)
    private var chargingObserved = false
    private var observationContinuity = BatteryObservationContinuity.PROCESS_OBSERVED
    private var persistentCheckpointReconciled = false
    val batteryObservationStart = mutableStateOf<BatteryObservationPoint?>(null)
    val batteryObservationResult = mutableStateOf(restoredObservationResult())
    val batteryObservationNotice = mutableStateOf(
        when {
            batteryObservationResult.value != null -> BatteryObservationAnalyzer.explanation(
                batteryObservationResult.value!!.validity
            )
            else -> null
        }
    )
    private var analysisGeneration = 0L
    private var onlineAnalysis = false
    private var consumedSharedBugReportUri: String? = null

    fun consumeSharedBugReport(uri: String): Boolean {
        if (uri == consumedSharedBugReportUri) return false
        consumedSharedBugReportUri = uri
        return true
    }

    fun completePowerDiagnostic(snapshot: PowerDiagnosticSnapshot) {
        powerDiagnostic.value = snapshot
        powerDiagnosticState.value = PowerDiagnosticUiState.Ready
        agent.includePowerDiagnosticReport.value = true
    }

    fun startBatteryObservation(point: BatteryObservationPoint): Boolean {
        if (batteryObservationStart.value != null) {
            batteryObservationNotice.value = "续航观察已经在进行中。"
            return false
        }
        val error = BatteryObservationAnalyzer.startError(point)
        if (error != null) {
            batteryObservationNotice.value = error
            return false
        }
        batteryObservationStart.value = point
        batteryObservationResult.value = null
        chargingObserved = false
        observationContinuity = BatteryObservationContinuity.PROCESS_OBSERVED
        saveObservationResult(null)
        batteryObservationNotice.value = "观察已开始。保持设备不充电，建议正常使用至少 30 分钟。"
        return true
    }

    fun finishBatteryObservation(point: BatteryObservationPoint, userReportedCharging: Boolean) {
        val start = batteryObservationStart.value
        if (start == null) {
            batteryObservationNotice.value = "还没有开始续航观察。"
            return
        }
        val result = BatteryObservationAnalyzer.finish(
            start,
            point,
            chargingObserved = chargingObserved || userReportedCharging,
            continuity = observationContinuity
        )
        val canContinue = result.validity in setOf(
            BatteryObservationValidity.MISSING_BATTERY_LEVEL,
            BatteryObservationValidity.TOO_SHORT,
            BatteryObservationValidity.NO_MEASURABLE_DROP
        )
        if (!canContinue) {
            batteryObservationStart.value = null
            batteryObservationResult.value = result
            saveObservationResult(result)
        }
        batteryObservationNotice.value = BatteryObservationAnalyzer.explanation(result.validity)
    }

    fun markBatteryObservationChargingObserved() {
        if (batteryObservationStart.value == null) return
        chargingObserved = true
        batteryObservationNotice.value = "观察期间检测到连接电源；本次数据已作废，结束后请重新开始。"
    }

    fun reconcileBatteryObservation(point: BatteryObservationPoint?, chargingWasObserved: Boolean = false) {
        if (persistentCheckpointReconciled) {
            val current = batteryObservationStart.value
            if (
                current != null &&
                point?.sampledAtInstant == current.sampledAtInstant &&
                chargingWasObserved
            ) {
                markBatteryObservationChargingObserved()
            }
            return
        }
        persistentCheckpointReconciled = true
        if (point == null) {
            batteryObservationStart.value = null
            chargingObserved = false
            observationContinuity = BatteryObservationContinuity.PROCESS_OBSERVED
            return
        }
        batteryObservationStart.value = point.copy(elapsedRealtimeMillis = null)
        batteryObservationResult.value = null
        chargingObserved = chargingWasObserved
        observationContinuity = BatteryObservationContinuity.USER_CONFIRMED_AFTER_RESTORE
        saveObservationResult(null)
        batteryObservationNotice.value = if (chargingWasObserved) {
            "已恢复观察起点，但期间记录到连接电源；本次数据已作废。"
        } else {
            "已恢复观察起点。结束时请确认期间是否充过电；结果将标为用户确认的粗略测量。"
        }
    }

    fun clearBatteryObservation() {
        batteryObservationStart.value = null
        batteryObservationResult.value = null
        batteryObservationNotice.value = null
        chargingObserved = false
        observationContinuity = BatteryObservationContinuity.PROCESS_OBSERVED
        saveObservationResult(null)
    }

    fun beginAnalysis(online: Boolean = false): Long {
        onlineAnalysis = online
        analyzing.value = true
        return ++analysisGeneration
    }

    fun appendAnalysisMessage(generation: Long, message: AgentConversationMessage) {
        if (generation == analysisGeneration) messages.value = messages.value + message
    }

    fun finishAnalysis(generation: Long) {
        if (generation == analysisGeneration) analyzing.value = false
    }

    fun interruptAnalysis(generation: Long? = null) {
        if (generation != null && generation != analysisGeneration) return
        if (!analyzing.value) return
        ++analysisGeneration
        analyzing.value = false
        messages.value = messages.value + AgentConversationMessage(
            role = MessageRole.ERROR,
            content = if (onlineAnalysis)
                "本次分析显示已中断。在线请求可能仍在处理，重试会再次发送。"
            else "本次分析已中断，请重新发送。",
            source = "LOCAL · INTERRUPTED"
        )
    }

    private fun restoredObservationResult(): BatteryObservationResult? {
        val start = restoredObservationPoint(RESULT_START_PREFIX) ?: return null
        val end = restoredObservationPoint(RESULT_END_PREFIX) ?: return null
        val persistedContinuity = savedStateHandle.get<String>(RESULT_CONTINUITY_KEY)
            ?.let { stored -> BatteryObservationContinuity.entries.firstOrNull { it.name == stored } }
            ?: BatteryObservationContinuity.PROCESS_OBSERVED
        val analyzed = BatteryObservationAnalyzer.finish(start, end, continuity = persistedContinuity)
        val persistedValidity = savedStateHandle.get<String>(RESULT_VALIDITY_KEY)
            ?.let { stored -> BatteryObservationValidity.entries.firstOrNull { it.name == stored } }
            ?: analyzed.validity
        return analyzed.copy(
            validity = persistedValidity,
            drainPercentPerHour = analyzed.drainPercentPerHour.takeIf {
                persistedValidity == BatteryObservationValidity.VALID
            }
        )
    }

    private fun restoredObservationPoint(prefix: String): BatteryObservationPoint? {
        val epochMillis = savedStateHandle.get<Long>("${prefix}_time") ?: return null
        val storedLevel = savedStateHandle.get<Int>("${prefix}_level") ?: return null
        val charging = savedStateHandle.get<Boolean>("${prefix}_charging") ?: return null
        val powerStateKnown = savedStateHandle.get<Boolean>("${prefix}_power_known") ?: true
        return BatteryObservationPoint(
            sampledAtInstant = java.time.Instant.ofEpochMilli(epochMillis),
            levelPercent = storedLevel.takeIf { it >= 0 },
            charging = charging,
            powerStateKnown = powerStateKnown,
            elapsedRealtimeMillis = null
        )
    }

    private fun saveObservationPoint(prefix: String, point: BatteryObservationPoint?) {
        if (point == null) {
            savedStateHandle.remove<Long>("${prefix}_time")
            savedStateHandle.remove<Int>("${prefix}_level")
            savedStateHandle.remove<Boolean>("${prefix}_charging")
            savedStateHandle.remove<Boolean>("${prefix}_power_known")
            return
        }
        savedStateHandle["${prefix}_time"] = point.sampledAtInstant.toEpochMilli()
        savedStateHandle["${prefix}_level"] = point.levelPercent ?: -1
        savedStateHandle["${prefix}_charging"] = point.charging
        savedStateHandle["${prefix}_power_known"] = point.powerStateKnown
    }

    private fun saveObservationResult(result: BatteryObservationResult?) {
        saveObservationPoint(RESULT_START_PREFIX, result?.start)
        saveObservationPoint(RESULT_END_PREFIX, result?.end)
        if (result == null) savedStateHandle.remove<String>(RESULT_VALIDITY_KEY)
        else {
            savedStateHandle[RESULT_VALIDITY_KEY] = result.validity.name
            savedStateHandle[RESULT_CONTINUITY_KEY] = result.continuity.name
        }
        if (result == null) savedStateHandle.remove<String>(RESULT_CONTINUITY_KEY)
    }

    private companion object {
        const val RESULT_START_PREFIX = "battery_observation_result_start"
        const val RESULT_END_PREFIX = "battery_observation_result_end"
        const val RESULT_VALIDITY_KEY = "battery_observation_result_validity"
        const val RESULT_CONTINUITY_KEY = "battery_observation_result_continuity"
    }
}

class AgentPageState {
    val selectedLevel = mutableStateOf<String?>(null)
    val includeUsageReport = mutableStateOf(false)
    val includeAppReport = mutableStateOf(false)
    val includePowerDiagnosticReport = mutableStateOf(false)
    val reportPickerExpanded = mutableStateOf(false)
    val draft = mutableStateOf("")
    var lastAutoScrollMessageCount = -1
}
