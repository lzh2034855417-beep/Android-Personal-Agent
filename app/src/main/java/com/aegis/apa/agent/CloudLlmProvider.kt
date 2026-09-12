package com.aegis.apa.agent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CloudProviderConfig(
    val name: String,
    val shortLabel: String,
    val model: String,
    val endpoint: String,
    val protocol: String
)

object CloudProviderCatalog {
    val providers = listOf(
        CloudProviderConfig(
            name = "DeepSeek",
            shortLabel = "DS",
            model = "deepseek-v4-flash",
            endpoint = "https://api.deepseek.com/chat/completions",
            protocol = "openai"
        ),
        CloudProviderConfig(
            name = "OpenAI · GPT",
            shortLabel = "GPT",
            model = "gpt-5.6-terra",
            endpoint = "https://api.openai.com/v1/chat/completions",
            protocol = "openai"
        ),
        CloudProviderConfig(
            name = "Anthropic · Claude",
            shortLabel = "Claude",
            model = "claude-sonnet-5",
            endpoint = "https://api.anthropic.com/v1/messages",
            protocol = "anthropic"
        ),
        CloudProviderConfig(
            name = "Xiaomi · MiMo",
            shortLabel = "MiMo",
            model = "mimo-v2.5",
            endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
            protocol = "openai"
        ),
        CloudProviderConfig(
            name = "Moonshot · Kimi",
            shortLabel = "Kimi",
            model = "kimi-k3",
            endpoint = "https://api.moonshot.cn/v1/chat/completions",
            protocol = "openai"
        )
    )

    fun find(name: String): CloudProviderConfig? = providers.firstOrNull { it.name == name }
}

internal fun buildCloudAnalysisPrompt(
    context: DeviceContext,
    userQuestion: String,
    selectedLevel: String,
    levelReport: String,
    appReport: String?,
    powerDiagnosticReport: String?
): String = buildString {
    appendLine("【用户问题】")
    appendLine(userQuestion)
    appendLine()
    if (powerDiagnosticReport != null) {
        appendLine("【回答任务：耗电诊断】")
        appendLine("先用一句话给出第一嫌疑；然后按证据强弱列出最多 3 个嫌疑应用。")
        appendLine("每个嫌疑必须写出应用名或包名、报告中的原始数值、原因和置信度。")
        appendLine("每个嫌疑只给一项 Scene 手动操作，并说明预期作用、副作用和回退方法；不得声称已经执行。")
        appendLine("如果证据仍不足，不要复述所有缺失栏目，只给出一个最有价值的下一步采样动作。")
        appendLine()
    } else {
        appendLine("【回答任务】")
        appendLine("直接回答当前问题；只引用下方实际附带的数据，不要讨论未附带的报告。")
        appendLine("按结论、依据、一个可执行建议组织；证据不足时只指出最关键缺口。")
        appendLine()
    }
    appendLine("【本次选择】")
    appendLine(selectedLevel)
    appendLine()
    appendLine("【基础设备快照】")
    appendLine("设备：${context.deviceModel}")
    appendLine("系统：${context.androidVersion}")
    appendLine("电量：${context.batteryLevel?.let { "$it%" } ?: "未获取到"}")
    appendLine("RAM：可用 ${context.availableRamBytes} B / 总计 ${context.totalRamBytes} B")
    appendLine("存储：可用 ${context.availableStorageBytes} B / 总计 ${context.totalStorageBytes} B")
    appendLine("可启动应用数量：${context.launchableAppCount}")
    appendLine()
    appendLine("【Level 报告】")
    appendLine(levelReport)
    appReport?.let {
        appendLine()
        appendLine("【应用报告】")
        appendLine(it)
    }
    powerDiagnosticReport?.let {
        appendLine()
        appendLine("【系统耗电诊断】")
        appendLine(it)
    }
}

object CloudLlmProvider {
    fun analyze(
        context: DeviceContext,
        userQuestion: String,
        selectedLevel: String,
        levelReport: String,
        appReport: String?,
        powerDiagnosticReport: String? = null,
        conversationHistory: List<AgentConversationMessage>,
        credentials: StoredApiKey
    ): AgentReport {
        val config = CloudProviderCatalog.find(credentials.provider)
            ?: throw AgentFailureException(AgentFailure.UNKNOWN_PROVIDER)
        if (credentials.apiKey.isBlank()) throw AgentFailureException(AgentFailure.MISSING_KEY)
        if (userQuestion.isBlank()) throw AgentFailureException(AgentFailure.EMPTY_QUESTION)

        val systemPrompt = AgentPromptPolicy.systemPrompt()

        val prompt = buildCloudAnalysisPrompt(
            context = context,
            userQuestion = userQuestion,
            selectedLevel = selectedLevel,
            levelReport = levelReport,
            appReport = appReport,
            powerDiagnosticReport = powerDiagnosticReport
        )

        val historyMessages = JSONArray()
        CloudHistoryPolicy.select(
            messages = conversationHistory,
            provider = credentials.provider,
            freshDiagnostic = powerDiagnosticReport != null
        )
            .forEach { message ->
                historyMessages.put(
                    JSONObject()
                        .put("role", message.role.wireName)
                        .put("content", message.content)
                )
            }
        historyMessages.put(JSONObject().put("role", "user").put("content", prompt))

        val body = if (config.protocol == "anthropic") {
            JSONObject()
                .put("model", config.model)
                .put("max_tokens", 4096)
                .put("system", systemPrompt)
                .put("messages", historyMessages)
        } else {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
            for (index in 0 until historyMessages.length()) {
                messages.put(historyMessages.getJSONObject(index))
            }
            JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .apply {
                    if (config.name == "DeepSeek") {
                        put("thinking", JSONObject().put("type", "disabled"))
                    }
                }
        }

        if (credentials.expiresAt <= System.currentTimeMillis()) {
            throw AgentFailureException(AgentFailure.EXPIRED_KEY)
        }
        val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            if (config.protocol == "anthropic") {
                setRequestProperty("x-api-key", credentials.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                setRequestProperty("Authorization", "Bearer ${credentials.apiKey}")
            }
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                // Do not read or display untrusted error bodies: they may echo request data.
                runCatching { connection.errorStream?.close() }
                throw ModelHttpException(statusCode)
            }
            val response = CloudResponseReader.read(connection.inputStream)

            val json = JSONObject(response)
            val content = if (config.protocol == "anthropic") {
                val blocks = json.optJSONArray("content") ?: JSONArray()
                buildString {
                    for (index in 0 until blocks.length()) {
                        val block = blocks.optJSONObject(index) ?: continue
                        if (block.optString("type") == "text") {
                            if (isNotEmpty()) appendLine()
                            append(block.optString("text"))
                        }
                    }
                }
            } else {
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
            }
            if (content.isBlank()) throw AgentFailureException(AgentFailure.EMPTY_RESPONSE)
            return AgentReport(
                summary = content,
                findings = emptyList(),
                source = "${config.shortLabel} · ${config.model}"
            )
        } catch (_: org.json.JSONException) {
            throw AgentFailureException(AgentFailure.INVALID_RESPONSE)
        } finally {
            connection.disconnect()
        }
    }
}
