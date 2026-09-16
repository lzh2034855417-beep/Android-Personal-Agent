package com.aegis.apa

import com.aegis.apa.agent.AgentPromptPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptPolicyTest {
    @Test
    fun gatesAdvancedAdviceBySelectedCapabilityLevel() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("Level 0 的回答不得提及 Scene、ADB、Shizuku、Root"))
        assertTrue(prompt.contains("Level 1（ADB / Shizuku）和 Level 2（Root / KernelSU）"))
    }

    @Test
    fun guidesBatteryAnswersToAvoidJudgingHealthFromOneSnapshot() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("单次快照不能判断长期电池寿命"))
        assertTrue(prompt.contains("循环次数、设计容量或满充容量"))
        assertTrue(prompt.contains("不得把未充电观察或 Android 系统 Bug Report 说成电池健康数据"))
        assertTrue(prompt.contains("已附加系统耗电诊断时不得要求再次生成或导入"))
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
        assertTrue(prompt.contains("本次问题中的用户描述只能作为使用背景或待验证假设"))
        assertTrue(prompt.contains("不得把改装版应用或模块信息说成系统报告已经确认"))
    }

    @Test
    fun guidesUsageAnswersToAvoidMistakingForegroundTimeForBatteryLife() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("同一天的使用线索"))
        assertTrue(prompt.contains("不等同于亮屏时长"))
        assertTrue(prompt.contains("不得推断应用内容或后台行为"))
    }

    @Test
    fun guidesBatteryAnswersToTrustSystemChargingStatusOverCurrentSign() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("电流正负方向可能受厂商实现影响"))
        assertTrue(prompt.contains("以系统充电状态为主"))
    }

    @Test
    fun keepsDrainEvidenceCollectionSeparateFromBatteryHealthChecks() {
        val prompt = AgentPromptPolicy.systemPrompt()

        assertTrue(prompt.contains("不得要求用户导出或上传 Scene 报告"))
        assertTrue(prompt.contains("耗电速度或应用归因问题"))
        assertTrue(prompt.contains("电池健康或是否更换问题"))
    }
}
