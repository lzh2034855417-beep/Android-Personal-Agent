package com.aegis.apa.agent

/** Only locally defined messages may cross the exception-to-UI boundary. */
enum class AgentFailure(val userMessage: String) {
    MISSING_KEY("请先在设置中保存 API Key"),
    EXPIRED_KEY("API Key 已过期或无法读取，请在设置中重新保存"),
    PROVIDER_CHANGED("模型服务已切换，请重新发送"),
    UNKNOWN_PROVIDER("不支持的模型服务，请在设置中重新选择"),
    EMPTY_QUESTION("请输入问题后再发送"),
    EMPTY_RESPONSE("模型返回了空内容，请稍后重试"),
    INVALID_RESPONSE("模型返回的数据格式异常，请稍后重试或切换服务"),
    RESPONSE_TOO_LARGE("模型响应超过大小限制，请缩小问题范围后重试")
}

class AgentFailureException(val reason: AgentFailure) : IllegalStateException(reason.userMessage)
class ModelHttpException(val statusCode: Int) : IllegalStateException("HTTP $statusCode")
