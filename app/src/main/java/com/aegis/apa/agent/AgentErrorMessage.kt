package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import java.io.IOException

object AgentErrorMessage {
    fun from(error: Exception, language: AppLanguage = AppLanguage.ZH_CN): String = when (error) {
        is AgentFailureException -> error.reason.userMessage(language)
        is ModelHttpException -> when (error.statusCode) {
            401 -> if (language == AppLanguage.EN) "The model service did not accept the API Key. Check or save it again in Settings." else "模型服务未接受 API Key，请在设置中检查或重新保存。"
            402 -> if (language == AppLanguage.EN) "The model service requires payment. Check the account balance or billing status." else "模型服务要求付费，请检查该服务的账户余额或账单。"
            403 -> if (language == AppLanguage.EN) "The model service denied access. Check the account, model permission, and regional availability." else "模型服务拒绝访问，请检查账户、模型权限及服务可用地区。"
            429 -> if (language == AppLanguage.EN) "The model service quota is exhausted or requests are too frequent. Check the balance and quota, then try again later." else "模型服务额度不足或请求过于频繁，请检查余额与额度，稍后再试。"
            in 500..599 -> if (language == AppLanguage.EN) "The model service is temporarily unavailable. Try again later or switch to another configured service." else "模型服务暂时异常，请稍后重试或切换其他已配置的服务。"
            else -> if (language == AppLanguage.EN) "The model request failed (HTTP ${error.statusCode}). Check the configuration or try again later." else "模型请求失败（HTTP ${error.statusCode}），请检查配置或稍后重试。"
        }
        is IOException -> if (language == AppLanguage.EN) "Could not reach the model service. Check the network, try again, or switch to another configured model in Settings." else "暂时无法连接模型服务，请检查网络后重试，或在设置中切换其他已配置的模型。"
        else -> if (language == AppLanguage.EN) "Online analysis failed. Try again later." else "在线分析失败，请稍后重试。"
    }
}
