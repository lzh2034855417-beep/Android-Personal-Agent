package com.aegis.apa

import com.aegis.apa.agent.DeviceContext
import com.aegis.apa.agent.buildCloudAnalysisPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerDiagnosticPromptTest {
    private val context = DeviceContext("device", "Android 16", 80, 1, 2, 3, 4, 5)

    @Test
    fun unselectedDiagnosticIsNotIncludedInCloudPrompt() {
        val prompt = buildCloudAnalysisPrompt(context, "为什么耗电", "Level 2", "level", null, null)

        assertFalse(prompt.contains("【应用报告】"))
        assertFalse(prompt.contains("【系统耗电诊断】"))
        assertFalse(prompt.contains("本次未附带"))
        assertFalse(prompt.contains("微信存在耗电线索"))
    }

    @Test
    fun selectedDiagnosticIncludesAConcreteAnswerContract() {
        val report = "微信存在耗电线索\n证据：唤醒 180 次"
        val prompt = buildCloudAnalysisPrompt(context, "为什么耗电", "Level 2", "level", null, report)

        assertTrue(prompt.contains(report))
        assertTrue(prompt.contains("【回答任务：耗电诊断】"))
        assertTrue(prompt.contains("最多 3 个嫌疑应用"))
        assertTrue(prompt.contains("报告中的原始数值"))
        assertTrue(prompt.contains("Scene 手动操作"))
        assertTrue(prompt.contains("副作用和回退方法"))
        assertTrue(prompt.contains("一个最有价值的下一步采样动作"))
        assertTrue(prompt.contains("只解释 APA 已完成的本地裁决"))
        assertTrue(prompt.contains("不得修改报告中的数值"))
        assertTrue(prompt.contains("不得把耗电总量排行改写成后台异常"))
        assertTrue(prompt.contains("不得超过报告给出的最高建议级别"))
        assertTrue(prompt.contains("不得声称已经执行"))
        assertFalse(prompt.contains("本次未附带"))
    }

    @Test
    fun drainRateQuestionRequiresDurationAndBatteryDelta() {
        val prompt = buildCloudAnalysisPrompt(
            context, "今天耗电快不快", "Level 0", "level", null, "本地裁决"
        )

        assertTrue(prompt.contains("先回答能否判断耗电速度"))
        assertTrue(prompt.contains("观察时长和电量变化"))
        assertTrue(prompt.contains("耗电速度："))
        assertTrue(prompt.contains("后台异常嫌疑："))
    }

    @Test
    fun attributionQuestionKeepsTotalUseSeparateFromBackgroundAnomaly() {
        val prompt = buildCloudAnalysisPrompt(
            context, "哪个应用造成后台耗电", "Level 0", "level", null, "本地裁决"
        )

        assertTrue(prompt.contains("先回答哪个应用存在后台异常证据"))
        assertTrue(prompt.contains("不得把耗电总量排行改写成后台异常"))
    }
}
