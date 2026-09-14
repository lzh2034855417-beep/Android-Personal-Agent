package com.aegis.apa

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.model.AppDetails
import com.aegis.apa.model.BatteryObservationAnalyzer
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

/** Session state; only the small battery-observation checkpoint enters SavedState. Never store credentials or raw reports here. */
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
    private val restoredActiveObservation = restoredObservationPoint(START_PREFIX)
    private var chargingObserved = savedStateHandle.get<Boolean>(CHARGING_OBSERVED_KEY) ?: false
    private var continuityLost = restoredActiveObservation != null
    val batteryObservationStart = mutableStateOf(restoredActiveObservation)
    val batteryObservationResult = mutableStateOf(restoredObservationResult())
    val batteryObservationNotice = mutableStateOf(
        when {
            batteryObservationStart.value != null -> "已恢复观察起点，但进程中断期间无法确认是否充电；请取消并重新开始。"
            batteryObservationResult.value != null -> BatteryObservationAnalyzer.explanation(
                batteryObservationResult.value!!.validity
            )
            else -> null
        }
    )
    private var analysisGeneration = 0L
    private var onlineAnalysis = false

    fun completePowerDiagnostic(snapshot: PowerDiagnosticSnapshot) {
        powerDiagnostic.value = snapshot
        powerDiagnosticState.value = PowerDiagnosticUiState.Ready
        agent.includePowerDiagnosticReport.value = true
    }

    fun startBatteryObservation(point: BatteryObservationPoint) {
        if (batteryObservationStart.value != null) {
            batteryObservationNotice.value = "续航观察已经在进行中。"
            return
        }
        val error = BatteryObservationAnalyzer.startError(point)
        if (error != null) {
            batteryObservationNotice.value = error
            return
        }
        batteryObservationStart.value = point
        batteryObservationResult.value = null
        chargingObserved = false
        continuityLost = false
        saveObservationPoint(START_PREFIX, point)
        savedStateHandle[CHARGING_OBSERVED_KEY] = false
        saveObservationResult(null)
        batteryObservationNotice.value = "观察已开始。保持设备不充电，建议正常使用至少 30 分钟。"
    }

    fun finishBatteryObservation(point: BatteryObservationPoint) {
        val start = batteryObservationStart.value
        if (start == null) {
            batteryObservationNotice.value = "还没有开始续航观察。"
            return
        }
        val result = BatteryObservationAnalyzer.finish(
            start,
            point,
            chargingObserved = chargingObserved,
            continuityLost = continuityLost
        )
        val canContinue = result.validity in setOf(
            BatteryObservationValidity.MISSING_BATTERY_LEVEL,
            BatteryObservationValidity.TOO_SHORT,
            BatteryObservationValidity.NO_MEASURABLE_DROP
        )
        if (!canContinue) {
            batteryObservationStart.value = null
            batteryObservationResult.value = result
            saveObservationPoint(START_PREFIX, null)
            saveObservationResult(result)
        }
        batteryObservationNotice.value = BatteryObservationAnalyzer.explanation(result.validity)
    }

    fun markBatteryObservationChargingObserved() {
        if (batteryObservationStart.value == null) return
        chargingObserved = true
        savedStateHandle[CHARGING_OBSERVED_KEY] = true
        batteryObservationNotice.value = "观察期间检测到连接电源；本次数据已作废，结束后请重新开始。"
    }

    fun clearBatteryObservation() {
        batteryObservationStart.value = null
        batteryObservationResult.value = null
        batteryObservationNotice.value = null
        chargingObserved = false
        continuityLost = false
        saveObservationPoint(START_PREFIX, null)
        saveObservationResult(null)
        savedStateHandle.remove<Boolean>(CHARGING_OBSERVED_KEY)
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
        val analyzed = BatteryObservationAnalyzer.finish(start, end)
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
        val elapsedRealtimeMillis = savedStateHandle.get<Long>("${prefix}_elapsed")
        return BatteryObservationPoint(
            sampledAtInstant = java.time.Instant.ofEpochMilli(epochMillis),
            levelPercent = storedLevel.takeIf { it >= 0 },
            charging = charging,
            powerStateKnown = powerStateKnown,
            elapsedRealtimeMillis = elapsedRealtimeMillis
        )
    }

    private fun saveObservationPoint(prefix: String, point: BatteryObservationPoint?) {
        if (point == null) {
            savedStateHandle.remove<Long>("${prefix}_time")
            savedStateHandle.remove<Int>("${prefix}_level")
            savedStateHandle.remove<Boolean>("${prefix}_charging")
            savedStateHandle.remove<Boolean>("${prefix}_power_known")
            savedStateHandle.remove<Long>("${prefix}_elapsed")
            return
        }
        savedStateHandle["${prefix}_time"] = point.sampledAtInstant.toEpochMilli()
        savedStateHandle["${prefix}_level"] = point.levelPercent ?: -1
        savedStateHandle["${prefix}_charging"] = point.charging
        savedStateHandle["${prefix}_power_known"] = point.powerStateKnown
        if (point.elapsedRealtimeMillis == null) savedStateHandle.remove<Long>("${prefix}_elapsed")
        else savedStateHandle["${prefix}_elapsed"] = point.elapsedRealtimeMillis
    }

    private fun saveObservationResult(result: BatteryObservationResult?) {
        saveObservationPoint(RESULT_START_PREFIX, result?.start)
        saveObservationPoint(RESULT_END_PREFIX, result?.end)
        if (result == null) savedStateHandle.remove<String>(RESULT_VALIDITY_KEY)
        else savedStateHandle[RESULT_VALIDITY_KEY] = result.validity.name
    }

    private companion object {
        const val START_PREFIX = "battery_observation_start"
        const val RESULT_START_PREFIX = "battery_observation_result_start"
        const val RESULT_END_PREFIX = "battery_observation_result_end"
        const val CHARGING_OBSERVED_KEY = "battery_observation_charging_observed"
        const val RESULT_VALIDITY_KEY = "battery_observation_result_validity"
    }
}

class AgentPageState {
    val selectedLevel = mutableStateOf("Level 0")
    val includeUsageReport = mutableStateOf(false)
    val includeAppReport = mutableStateOf(false)
    val includePowerDiagnosticReport = mutableStateOf(false)
    val reportPickerExpanded = mutableStateOf(false)
    val draft = mutableStateOf("")
    var lastAutoScrollMessageCount = -1
}
