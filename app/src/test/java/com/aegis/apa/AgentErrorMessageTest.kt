package com.aegis.apa

import com.aegis.apa.agent.AgentErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.aegis.apa.agent.StoredApiKey
import com.aegis.apa.agent.AgentFailure
import com.aegis.apa.agent.AgentFailureException
import com.aegis.apa.agent.ModelHttpException
import org.junit.Test
import java.net.SocketTimeoutException

class AgentErrorMessageTest {
    @Test fun localFailureReasonsKeepUsefulRecoveryInstructions() {
        assertEquals("请先在设置中保存 API Key", AgentErrorMessage.from(AgentFailureException(AgentFailure.MISSING_KEY)))
        assertEquals("模型服务已切换，请重新发送", AgentErrorMessage.from(AgentFailureException(AgentFailure.PROVIDER_CHANGED)))
    }

    @Test fun httpFailuresDistinguishAuthenticationQuotaAndServiceOutage() {
        assertEquals("模型服务未接受 API Key，请在设置中检查或重新保存。", AgentErrorMessage.from(ModelHttpException(401)))
        assertEquals("模型服务额度不足或请求过于频繁，请检查余额与额度，稍后再试。", AgentErrorMessage.from(ModelHttpException(429)))
        assertEquals("模型服务暂时异常，请稍后重试或切换其他已配置的服务。", AgentErrorMessage.from(ModelHttpException(503)))
    }

    @Test fun unexpectedExceptionsNeverExposeTheirMessage() {
        for (error in listOf(IllegalStateException("sk-secret-sentinel"), IllegalArgumentException("private-device-report"))) {
            assertEquals("在线分析失败，请稍后重试。", AgentErrorMessage.from(error))
        }
    }

    @Test fun credentialDebugStringHidesKey() {
        val credential = StoredApiKey("DeepSeek", "sk-secret-sentinel", 123L)
        assertFalse(credential.toString().contains("sk-secret-sentinel"))
    }

    @Test
    fun hidesNetworkDetailsAndOffersARecoveryPath() {
        val message = AgentErrorMessage.from(
            SocketTimeoutException("failed to connect to api.openai.com/103.252.115.153")
        )

        assertEquals(
            "暂时无法连接模型服务，请检查网络后重试，或在设置中切换其他已配置的模型。",
            message
        )
    }
}
