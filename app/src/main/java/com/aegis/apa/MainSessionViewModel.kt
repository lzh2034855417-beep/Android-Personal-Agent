package com.aegis.apa

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.model.AppDetails
import com.aegis.apa.model.DeviceSnapshot
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.tool.RootBatteryInfo

/** In-memory session only. No credentials, Activity references or large saved-state Bundles. */
class MainSessionViewModel : ViewModel() {
    val agent = AgentPageState()
    val snapshot = mutableStateOf<DeviceSnapshot?>(null)
    val selectedAppDetails = mutableStateOf<AppDetails?>(null)
    val rootBatteryInfo = mutableStateOf<RootBatteryInfo?>(null)
    val rootBatteryReading = mutableStateOf(false)
    val deviceProfile = mutableStateOf<DeviceProfileSnapshot?>(null)
    val deviceProfileReading = mutableStateOf(false)
    val messages = mutableStateOf<List<AgentConversationMessage>>(emptyList())
    val analyzing = mutableStateOf(false)
    val sceneReport = mutableStateOf<String?>(null)
    val sceneImportStatus = mutableStateOf<String?>(null)
    val sceneImportError = mutableStateOf<String?>(null)
    private var analysisGeneration = 0L
    private var onlineAnalysis = false

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
        ++analysisGeneration // Ignore completion from the destroyed Activity's request.
        analyzing.value = false
        messages.value = messages.value + AgentConversationMessage(
            role = MessageRole.ERROR,
            content = if (onlineAnalysis)
                "本次分析显示已中断。在线请求可能仍在处理，重试会再次发送。"
            else "本次分析已中断，请重新发送。",
            source = "LOCAL · INTERRUPTED"
        )
    }
}

class AgentPageState {
    val selectedLevel = mutableStateOf("Level 0")
    val includeUsageReport = mutableStateOf(false)
    val includeAppReport = mutableStateOf(false)
    val includeSceneReport = mutableStateOf(false)
    val reportPickerExpanded = mutableStateOf(false)
    val draft = mutableStateOf("")
    var lastAutoScrollMessageCount = -1
}
