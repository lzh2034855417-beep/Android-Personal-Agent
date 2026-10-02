package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.*
import org.junit.Test

/** Tests the actual model-bound prompt contract, not the quality of a live model response. */
class FocusedAnalysisPromptTest {
    private val context = DeviceContext("Fixture", "16", 60, 10, 20, 30, 40, 2)

    @Test fun deviceSystemPolicyRequiresTargetEvidenceAndOneVerifiableActionInBothLanguages() {
        val zh = AgentPromptPolicy.systemPrompt(language = AppLanguage.ZH_CN)
        val en = AgentPromptPolicy.systemPrompt(language = AppLanguage.EN)
        listOf("优先对象", "数值、单位、来源和统计窗口", "一个优先动作", "回退", "不得编造置信度百分比").forEach {
            assertTrue("Missing Chinese contract: $it", zh.contains(it))
        }
        listOf("priority target", "value, unit, source, and measurement window", "one priority action", "rollback", "Never invent confidence percentages").forEach {
            assertTrue("Missing English contract: $it", en.contains(it))
        }
    }

    @Test fun attributionTasksAnchorPriorityToLocalVerdictInsteadOfInventingCulprits() {
        val report = "Fixture app (com.fixture.app): 120 mAh; confidence low"
        val zh = buildCloudAnalysisPrompt(context, "哪个应用最费电", "Level 0", null, null, report)
        val en = buildCloudAnalysisPrompt(context, "Which app is causing battery drain?", "Level 0", null, null, report, AppLanguage.EN)
        assertTrue(zh.contains("只从本地裁决已有的候选中选择优先对象"))
        assertTrue(en.contains("Choose the priority target only from candidates already in the local verdict"))
        assertTrue(zh.contains("不能唯一排序"))
        assertTrue(en.contains("cannot establish a unique priority"))
        assertTrue(en.contains(report))
    }

    @Test fun englishHealthQuestionExcludesUnrelatedAppAndPowerPayloads() {
        val prompt = buildCloudAnalysisPrompt(context, "Should I replace my battery?", "Level 0",
            "Health: good", "UNRELATED_APP_PAYLOAD", "UNRELATED_POWER_PAYLOAD", AppLanguage.EN)
        assertTrue(prompt.contains("Health: good"))
        assertFalse(prompt.contains("UNRELATED_APP_PAYLOAD"))
        assertFalse(prompt.contains("UNRELATED_POWER_PAYLOAD"))
        assertTrue(prompt.contains("Do not turn a coarse good-health status into a health percentage"))
        assertTrue(prompt.contains("official battery test"))
    }

    @Test fun englishShortDrainWindowCannotBecomeRuntimeProjection() {
        val prompt = buildCloudAnalysisPrompt(context, "Is my battery drain rate normal?", "Level 0", null, null,
            "Period: since last full charge\nAverage drain: 90 mAh/hour\nWindow note: under 2 hours", AppLanguage.EN)
        assertTrue(prompt.contains("90 mAh/hour"))
        assertTrue(prompt.contains("Do not project full runtime from a window under 2 hours"))
        assertTrue(prompt.contains("Do not label a rate abnormal without a comparable baseline"))
    }

    @Test fun drainQuestionDoesNotRedirectMissingRateIntoUnrelatedAppRankings() {
        val prompt = buildCloudAnalysisPrompt(context, "掉电速度正常吗", "Level 2", null, null, "没有有效速率")
        assertFalse(prompt.contains("即使无法判断速度，仍可解释高耗电应用和后台耗电排行"))
        assertFalse(prompt.contains("后台耗电排行优先指出后台耗电量和占比"))
        assertFalse(prompt.contains("可解释报告给出的一项 Scene 手动操作"))
        assertFalse(prompt.contains("主要耗电："))
        assertTrue(prompt.contains("一个取得该证据的下一步") || prompt.contains("一次 APA 未充电续航观察"))
    }

    @Test fun englishLevelZeroPromptDoesNotCarryAdvancedDiagnosticAdvice() {
        val prompt = buildCloudAnalysisPrompt(context, "Which app is causing battery drain?", "Level 0", null, null,
            "Evidence: com.fixture.app 120 mAh\nScene advice: freeze com.fixture.app", AppLanguage.EN)
        assertTrue(prompt.contains("Evidence: com.fixture.app 120 mAh"))
        assertFalse(prompt.contains("Scene advice: freeze"))
    }

    @Test fun freeChatRemainsNaturalRatherThanForcedIntoDeviceDiagnosis() {
        for (language in AppLanguage.entries) {
            val system = AgentPromptPolicy.systemPrompt(deviceAdviceMode = false, language = language)
            val prompt = buildCloudAnalysisPrompt(null, "Hello", null, null, null, null, language)
            assertFalse(system.contains("priority target"))
            assertFalse(system.contains("优先对象"))
            assertFalse(prompt.contains("[Focused answer task]"))
            assertFalse(prompt.contains("【定向回答任务】"))
        }
    }
}
