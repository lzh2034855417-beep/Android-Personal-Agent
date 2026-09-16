package com.aegis.apa

import com.aegis.apa.agent.AgentAttachmentPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAttachmentPolicyTest {
    @Test
    fun applicationReportIsOnlyOfferedWhenOnlineAnalysisConsumesIt() {
        assertFalse(AgentAttachmentPolicy.showAppReport(isOnline = false))
        assertTrue(AgentAttachmentPolicy.showAppReport(isOnline = true))
    }

    @Test
    fun onlineDisclosureNamesEveryReportCategoryAndDerivedRootData() {
        val disclosure = AgentAttachmentPolicy.disclosure(isOnline = true)

        assertTrue(disclosure.contains("等级报告"))
        assertTrue(disclosure.contains("应用报告"))
        assertTrue(disclosure.contains("使用习惯排行"))
        assertTrue(disclosure.contains("系统耗电诊断"))
        assertTrue(disclosure.contains("最近最多 12 条"))
        assertTrue(disclosure.contains("Root 原始输出"))
        assertTrue(disclosure.contains("Root 数据摘要"))
    }

    @Test
    fun localDisclosureSaysDataStaysOnDevice() {
        val disclosure = AgentAttachmentPolicy.disclosure(isOnline = false)

        assertTrue(disclosure.contains("仅在本机"))
        assertTrue(disclosure.contains("不会上传"))
    }
}
