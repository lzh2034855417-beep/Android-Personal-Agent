package com.aegis.apa.agent

import java.io.IOException

object AgentErrorMessage {
    fun from(error: Exception): String = when (error) {
        is AgentFailureException -> error.reason.userMessage
        is ModelHttpException -> when (error.statusCode) {
            401 -> "模型服务未接受 API Key，请在设置中检查或重新保存。"
            402 -> "模型服务要求付费，请检查该服务的账户余额或账单。"
            403 -> "模型服务拒绝访问，请检查账户、模型权限及服务可用地区。"
            429 -> "模型服务额度不足或请求过于频繁，请检查余额与额度，稍后再试。"
            in 500..599 -> "模型服务暂时异常，请稍后重试或切换其他已配置的服务。"
            else -> "模型请求失败（HTTP ${error.statusCode}），请检查配置或稍后重试。"
        }
        is IOException -> "暂时无法连接模型服务，请检查网络后重试，或在设置中切换其他已配置的模型。"
        else -> "在线分析失败，请稍后重试。"
    }
}
