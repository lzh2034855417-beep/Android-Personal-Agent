package com.aegis.apa

import com.aegis.apa.agent.AgentPromptPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptPolicyTest {
    @Test
    fun guidesBatteryAnswersToAvoidJudgingHealthFromOneSnapshot() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("单次快照不能判断长期电池寿命"))
        assertTrue(prompt.contains("循环次数、设计容量或满充容量"))
    }

    @Test
    fun guidesHardwareAnswersToTreatBadgesAsConfigurationReferences() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("配置参考"))
        assertTrue(prompt.contains("不得据此推断屏幕、内存或电池供应商"))
    }

    @Test
    fun guidesAnswersToGiveActionableAdviceAndNameMissingData() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("结论、依据、建议"))
        assertTrue(prompt.contains("还需要哪些数据"))
    }
}
