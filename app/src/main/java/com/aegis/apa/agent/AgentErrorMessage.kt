package com.aegis.apa.agent

import java.io.IOException

object AgentErrorMessage {
    fun from(error: Exception): String = when (error) {
        is IOException -> "暂时无法连接模型服务，请检查网络后重试，或在设置中切换其他已配置的模型。"
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "在线分析失败，请稍后重试。"
        else -> "在线分析失败，请稍后重试。"
    }
}
