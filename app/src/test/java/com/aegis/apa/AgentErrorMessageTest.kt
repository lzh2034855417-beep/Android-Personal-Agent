package com.aegis.apa

import com.aegis.apa.agent.AgentErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class AgentErrorMessageTest {
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
