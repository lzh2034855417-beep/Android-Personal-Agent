package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage

/** Only locally defined messages may cross the exception-to-UI boundary. */
enum class AgentFailure(val userMessage: String, private val englishMessage: String) {
    MISSING_KEY("请先在设置中保存 API Key", "Save an API Key in Settings before sending."),
    EXPIRED_KEY("API Key 已过期或无法读取，请在设置中重新保存", "The API Key expired or could not be read. Save it again in Settings."),
    PROVIDER_CHANGED("模型服务已切换，请重新发送", "The model service changed. Send your question again."),
    UNKNOWN_PROVIDER("不支持的模型服务，请在设置中重新选择", "This model service is not supported. Select another service in Settings."),
    EMPTY_QUESTION("请输入问题后再发送", "Enter a question before sending."),
    EMPTY_RESPONSE("模型返回了空内容，请稍后重试", "The model returned an empty response. Try again later."),
    INVALID_RESPONSE("模型返回的数据格式异常，请稍后重试或切换服务", "The model returned an invalid response. Try again later or switch services."),
    RESPONSE_TOO_LARGE("模型响应超过大小限制，请缩小问题范围后重试", "The model response exceeded the size limit. Narrow the question and try again.");

    fun userMessage(language: AppLanguage): String =
        if (language == AppLanguage.EN) englishMessage else userMessage
}

class AgentFailureException(val reason: AgentFailure) : IllegalStateException(reason.userMessage)
class ModelHttpException(val statusCode: Int) : IllegalStateException("HTTP $statusCode")
