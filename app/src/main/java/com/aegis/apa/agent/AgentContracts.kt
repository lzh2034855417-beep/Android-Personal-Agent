package com.aegis.apa.agent

data class DeviceContext(
    val deviceModel: String,
    val androidVersion: String,
    val batteryLevel: Int?,
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val availableStorageBytes: Long,
    val totalStorageBytes: Long,
    val launchableAppCount: Int
)

data class AgentReport(
    val summary: String,
    val findings: List<String>,
    val source: String
)

enum class MessageRole(val wireName: String) { USER("user"), ASSISTANT("assistant"), ERROR("error") }

data class AgentConversationMessage(
    val role: MessageRole,
    val content: String,
    val attachedReportLabel: String? = null,
    val source: String? = null,
    /** Null means local-only. Only the provider that produced a cloud turn may receive its history. */
    val cloudProvider: String? = null
)

interface LlmProvider {
    val id: String
    val displayName: String

    suspend fun analyzeDevice(context: DeviceContext): AgentReport
}
