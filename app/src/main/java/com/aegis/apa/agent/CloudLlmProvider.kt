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

object CloudLlmProvider {
    fun analyze(
        context: DeviceContext,
        userQuestion: String,
        selectedLevel: String,
        levelReport: String,
        appReport: String?,
        conversationHistory: List<AgentConversationMessage>,
        credentials: StoredApiKey
    ): AgentReport {
        val config = requireNotNull(CloudProviderCatalog.find(credentials.provider)) {
            "不支持的模型服务：${credentials.provider}"
        }
        require(credentials.apiKey.isNotBlank()) { "请先在设置中保存 ${config.shortLabel} API Key" }
        require(userQuestion.isNotBlank()) { "问题不能为空" }

        val systemPrompt = AgentPromptPolicy.systemPrompt()

        val prompt = buildString {
            appendLine("【用户问题】")
            appendLine(userQuestion)
            appendLine()
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
            appendLine()
            appendLine("【应用报告】")
            appendLine(appReport ?: "本次未附带")
            appendLine()
            appendLine("【Scene 一天续航报告】")
            appendLine("本次未附带；Scene 导入仅供本地查看")
        }

        val historyMessages = JSONArray()
        CloudHistoryPolicy.select(conversationHistory, credentials.provider)
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

        check(credentials.expiresAt > System.currentTimeMillis()) {
            "API Key 的本机保存期限已到，请在设置中重新保存"
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
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val providerMessage = runCatching {
                    JSONObject(response).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                error("${config.shortLabel} 请求失败（$statusCode）：${providerMessage.ifBlank { "请检查 API Key、余额和网络" }}")
            }

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
            require(content.isNotBlank()) { "${config.shortLabel} 返回了空内容" }
            return AgentReport(
                summary = content,
                findings = emptyList(),
                source = "${config.shortLabel} · ${config.model}"
            )
        } finally {
            connection.disconnect()
        }
    }
}
