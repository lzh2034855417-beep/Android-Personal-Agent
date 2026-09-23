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

private fun allowsAdvancedAdvice(selectedLevel: String?): Boolean =
    selectedLevel?.startsWith("Level 1") == true || selectedLevel?.startsWith("Level 2") == true

private val unsafeDeviceCommandPatterns = listOf(
    Regex("\\badb\\s+shell\\b", RegexOption.IGNORE_CASE),
    Regex("\\bsu\\s+-c\\b", RegexOption.IGNORE_CASE),
    Regex("\\bpm\\s+(?:disable(?:-user)?|suspend|hide|uninstall)\\b", RegexOption.IGNORE_CASE),
    Regex("\\bcmd\\s+package\\b", RegexOption.IGNORE_CASE),
    Regex("\\bam\\s+force-stop\\b", RegexOption.IGNORE_CASE),
    Regex("\\bsettings\\s+put\\b", RegexOption.IGNORE_CASE)
)

private fun containsUnsafeDeviceCommand(content: String): Boolean =
    unsafeDeviceCommandPatterns.any { pattern -> pattern.containsMatchIn(content) }

internal fun sanitizeCloudAnalysisResponse(
    content: String,
    selectedLevel: String?,
    powerIntent: PowerQuestionIntent = PowerQuestionIntent.OTHER,
    reportAttached: Boolean = false,
    deviceAdviceMode: Boolean = true
): String {
    val healthFiltered = if (powerIntent == PowerQuestionIntent.BATTERY_HEALTH) {
        val irrelevantPrefixes = listOf(
            "耗电窗口：", "高耗电应用：", "后台耗电排行：", "系统调度观察：",
            "scene 建议：", "普通设置建议：", "耗电总量排行："
        )
        content.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                irrelevantPrefixes.any { prefix -> trimmed.startsWith(prefix, ignoreCase = true) } ||
                    trimmed.contains("Bug Report", ignoreCase = true) ||
                    trimmed.contains("Scene", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    } else {
        content
    }
    val reportSafe = if (reportAttached) {
        val reportTerms = listOf("bug report", "报告", "系统报告", "系统耗电诊断", "报告包")
        val requestTerms = listOf("生成", "导入", "上传", "提供", "重新", "再次", "再做", "再抓")
        healthFiltered.lineSequence()
            .filterNot { line ->
                reportTerms.any { term -> line.contains(term, ignoreCase = true) } &&
                    requestTerms.any { term -> line.contains(term, ignoreCase = true) }
            }
            .joinToString("\n")
            .trim()
    } else {
        healthFiltered
    }
    if (!deviceAdviceMode) return sanitizeGeneralChatResponse(reportSafe)
    if (allowsAdvancedAdvice(selectedLevel)) return reportSafe
    val advancedTerms = listOf(
        "scene", "adb", "shizuku", "root", "kernelsu", "ksu", "magisk",
        "lsposed", "xposed", "zygisk", "shell", "冻结", "限频", "命令", "刷入"
    )
    return reportSafe.split(Regex("(?<=[。！？!?；;])|\\R+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { segment ->
            containsUnsafeDeviceCommand(segment) ||
                advancedTerms.any { term -> segment.contains(term, ignoreCase = true) }
        }
        .joinToString("\n")
        .trim()
}

private fun sanitizeGeneralChatResponse(content: String): String {
    val advancedTerms = listOf(
        "adb", "shizuku", "root", "scene", "shell", "magisk", "kernelsu", "ksu",
        "冻结", "限频", "刷入", "停用", "禁用", "pm disable", "su -c"
    )
    val actionTerms = listOf(
        "执行", "运行", "输入", "复制以下", "使用以下", "可以用", "冻结", "限频", "刷入", "停用", "禁用",
        "卸载", "删除", "修改", "强停", "run ", "execute ", "use ", "enter ", "paste ", "disable ",
        "freeze ", "flash ", "uninstall ", "delete ", "modify "
    )
    val safe = content.split(Regex("(?<=[。！？!?；;])|\\R+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { segment ->
            containsUnsafeDeviceCommand(segment) ||
                advancedTerms.any { term -> segment.contains(term, ignoreCase = true) } &&
                actionTerms.any { term -> segment.contains(term, ignoreCase = true) }
        }
        .joinToString("\n")
        .trim()
    return safe.ifBlank {
        "当前未选择设备能力等级，我不能提供可执行的 ADB、Shizuku、Root、冻结或限频操作。请选择相应等级并完成授权后再进行设备分析。"
    }
}

internal fun buildCloudAnalysisPrompt(
    context: DeviceContext?,
    userQuestion: String,
    selectedLevel: String?,
    levelReport: String?,
    appReport: String?,
    powerDiagnosticReport: String?
): String = buildString {
    val powerIntent = PowerAnalysisPreflight.classify(userQuestion)
    val deviceAdviceMode = context != null || selectedLevel != null || levelReport != null ||
        appReport != null || powerDiagnosticReport != null
    val advancedAdviceAllowed = allowsAdvancedAdvice(selectedLevel)
    val hasValidatedDrainWindow = powerDiagnosticReport?.let { report ->
        report.contains("统计周期：自上次充满后") && report.contains("平均耗电：")
    } == true
    val hasShortDrainWindow = powerDiagnosticReport?.contains("统计窗口不足 2 小时") == true
    appendLine("【用户问题】")
    appendLine(userQuestion)
    appendLine()
    if (!deviceAdviceMode) {
        appendLine("【模式】")
        appendLine("普通对话；本次未附带设备报告或设备快照。")
        return@buildString
    }
    if (powerIntent == PowerQuestionIntent.BATTERY_HEALTH) {
        appendLine("【回答任务：电池健康与更换判断】")
        if (powerDiagnosticReport != null) {
            appendLine("系统耗电诊断已经附加，但它用于耗电归因，不是循环次数或容量健康检测；不得要求用户再次生成或导入该报告。")
        }
        appendLine("直接回答是否已有足够证据建议更换电池。健康“良好”只是 Android 的粗粒度状态，不是电池健康度百分比。")
        appendLine("不要讨论耗电应用、后台排行、调度计数、短时耗电速率或预计续航；这些不能回答电池是否老化。")
        appendLine("没有同时取得可信且单位一致的设计容量与满充容量时，不得计算健康度；没有安全异常或可靠容量证据时，不得断言必须更换。")
        when {
            selectedLevel?.startsWith("Level 2") == true -> appendLine("只有 Root 报告实际提供设计容量、满充容量和循环次数时才可引用；字段缺失就明确缺失，不得猜测。")
            selectedLevel?.startsWith("Level 1") == true -> appendLine("ADB / Shizuku 通常不能可靠读取受保护的容量和循环字段，不得假装已经获得；可建议官方电池检测或售后检测。")
            else -> appendLine("普通权限缺少循环次数或容量证据时，只建议官方电池检测或售后检测，不提供高级权限操作。")
        }
        appendLine("安全建议只处理鼓包、异常发热、异常关机或电量突降等症状；出现鼓包时应停止充电和继续使用并尽快送检。")
        appendLine("优先控制在 400 至 600 个中文字符以内；没有内容的字段可以省略，不要重复免责。")
        appendLine("使用这些纯文本字段：结论：、已知健康信息：、无法判断的原因：、安全建议：、下一步：。不要增加耗电诊断字段。")
        appendLine()
    } else if (powerDiagnosticReport != null && powerIntent != PowerQuestionIntent.OTHER) {
        appendLine("【回答任务：耗电诊断】")
        appendLine("只解释 APA 已完成的本地裁决，不重新归因，也不要自行增加嫌疑应用。")
        appendLine("系统耗电诊断已经附加，不得要求用户重新导入当前报告。")
        when (powerIntent) {
            PowerQuestionIntent.DRAIN_RATE -> {
                if (hasValidatedDrainWindow) {
                    if (hasShortDrainWindow) {
                        appendLine("诊断已提供本地验证但不足 2 小时的统计窗口；只报告该短窗口的实测平均耗电速度，明确样本过短，不得计算或输出预计续航。")
                    } else {
                        appendLine("诊断已提供本地验证的自上次充满后统计窗口；直接报告该统计周期的平均耗电速度和同强度预计续航，明确它不等同于今天全天，不要改写数值。")
                    }
                } else {
                    appendLine("诊断没有提供经过验证的统计周期平均耗电；没有统计周期平均耗电时不得编造速度，只建议在 APA 内完成一次未充电的应用内续航观察。")
                }
                appendLine("即使无法判断速度，仍可解释高耗电应用和后台耗电排行。")
            }
            PowerQuestionIntent.ATTRIBUTION -> appendLine("先回答后台耗电排行中哪个应用最值得优先核对；高耗电或后台耗电较高不等同于异常。")
            PowerQuestionIntent.BATTERY_HEALTH,
            PowerQuestionIntent.OTHER -> Unit
        }
        appendLine("最多解释 3 个主要耗电对象，必须写出应用名或包名、报告中的原始数值和耗电更偏前台还是后台。")
        appendLine("后台耗电排行优先指出后台耗电量和占比；单一耗电估算只能作为优先核对线索。")
        appendLine("系统调度观察必须分别说明真实唤醒与普通定时任务；这些是独立累计计数，系统调度观察不得用于耗电归因，也不得据此生成限制或冻结建议。")
        appendLine("不得修改报告中的数值，不得把高耗电或后台耗电排行改写成已确认异常。")
        appendLine("不得超过报告给出的最高建议级别；尤其不得把观察或限制升级为冻结候选。")
        if (advancedAdviceAllowed) {
            val capability = if (selectedLevel?.startsWith("Level 1") == true) {
                "ADB / Shizuku"
            } else {
                "Root / KernelSU"
            }
            appendLine("本次为 $capability 能力层，可解释报告给出的一项 Scene 手动操作，并说明预期作用、副作用和回退方法；不得声称已经执行。")
        } else {
            appendLine("本次为普通用户能力层，只提供普通 Android 用户可执行的系统设置或观察建议；不要输出任何高级权限工具、命令、冻结或限频建议。")
        }
        appendLine("系统调度数据只有在直接改变结论时才简短写进证据限制，不要单列调度栏目，也不要用累计计数凑篇幅。")
        appendLine("如果证据仍不足，不要复述所有缺失栏目，只给出一个最有价值的下一步；没有内容的字段可以省略。")
        val outputFields = when (powerIntent) {
            PowerQuestionIntent.DRAIN_RATE -> "结论：、耗电窗口：、主要耗电：、建议：、证据限制："
            PowerQuestionIntent.ATTRIBUTION -> "结论：、主要耗电：、后台判断：、建议：、证据限制："
            PowerQuestionIntent.BATTERY_HEALTH,
            PowerQuestionIntent.OTHER -> "结论：、依据：、建议："
        }
        appendLine("输出使用这些纯文本字段：$outputFields。优先控制在 600 至 800 个中文字符以内，不要重复同一限制。")
        appendLine()
    } else {
        appendLine("【回答任务】")
        appendLine("直接回答当前问题；只引用下方与问题直接相关的数据，不要被已附带但无关的报告带偏。")
        if (powerDiagnosticReport != null) {
            appendLine("系统耗电诊断已经附加，不得要求用户重新导入当前报告。")
        }
        appendLine("按结论、依据、一个可执行建议组织；证据不足时只指出最关键缺口。")
        appendLine()
    }
    appendLine("【本次选择】")
    appendLine(selectedLevel ?: "未附带设备报告")
    context?.let { device ->
        appendLine()
        appendLine("【基础设备快照】")
        appendLine("设备：${device.deviceModel}")
        appendLine("系统：${device.androidVersion}")
        appendLine("电量：${device.batteryLevel?.let { "$it%" } ?: "未获取到"}")
        appendLine("RAM：可用 ${device.availableRamBytes} B / 总计 ${device.totalRamBytes} B")
        appendLine("存储：可用 ${device.availableStorageBytes} B / 总计 ${device.totalStorageBytes} B")
        appendLine("可启动应用数量：${device.launchableAppCount}")
    }
    levelReport?.let {
        appendLine()
        appendLine("【Level 报告】")
        appendLine(it)
    }
    appReport?.takeIf { powerIntent != PowerQuestionIntent.BATTERY_HEALTH }?.let {
        appendLine()
        appendLine("【应用报告】")
        appendLine(it)
    }
    powerDiagnosticReport?.takeIf { powerIntent != PowerQuestionIntent.BATTERY_HEALTH }?.let {
        appendLine()
        appendLine("【系统耗电诊断】")
        val levelSafeReport = if (advancedAdviceAllowed) {
            it
        } else {
            it.lineSequence()
                .filterNot { line -> line.contains("Scene", ignoreCase = true) }
                .joinToString("\n")
        }
        appendLine(levelSafeReport)
    }
}

object CloudLlmProvider {
    fun analyze(
        context: DeviceContext?,
        userQuestion: String,
        selectedLevel: String?,
        levelReport: String?,
        appReport: String?,
        powerDiagnosticReport: String? = null,
        conversationHistory: List<AgentConversationMessage>,
        credentials: StoredApiKey
    ): AgentReport {
        val config = CloudProviderCatalog.find(credentials.provider)
            ?: throw AgentFailureException(AgentFailure.UNKNOWN_PROVIDER)
        if (credentials.apiKey.isBlank()) throw AgentFailureException(AgentFailure.MISSING_KEY)
        if (userQuestion.isBlank()) throw AgentFailureException(AgentFailure.EMPTY_QUESTION)

        val hasDeviceEvidence = context != null || levelReport != null || appReport != null ||
            powerDiagnosticReport != null
        val adviceScope = AdviceCapabilityPolicy.cloudAdviceScope(selectedLevel, hasDeviceEvidence)
        val deviceAdviceMode = adviceScope != CloudAdviceScope.GENERAL
        val systemPrompt = AgentPromptPolicy.systemPrompt(deviceAdviceMode)

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
            freshDiagnostic = powerDiagnosticReport != null,
            adviceScope = adviceScope
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
            val rawContent = if (config.protocol == "anthropic") {
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
            val content = sanitizeCloudAnalysisResponse(
                rawContent,
                selectedLevel,
                PowerAnalysisPreflight.classify(userQuestion),
                reportAttached = powerDiagnosticReport != null,
                deviceAdviceMode = deviceAdviceMode
            )
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
