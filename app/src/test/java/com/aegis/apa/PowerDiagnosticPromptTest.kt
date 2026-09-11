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

        assertTrue(prompt.contains("【系统耗电诊断】"))
        assertTrue(prompt.contains("本次未附带"))
        assertFalse(prompt.contains("微信存在耗电线索"))
    }

    @Test
    fun selectedCompactDiagnosticIsIncludedVerbatim() {
        val report = "微信存在耗电线索\n证据：唤醒 180 次"
        val prompt = buildCloudAnalysisPrompt(context, "为什么耗电", "Level 2", "level", null, report)

        assertTrue(prompt.contains(report))
    }
}
