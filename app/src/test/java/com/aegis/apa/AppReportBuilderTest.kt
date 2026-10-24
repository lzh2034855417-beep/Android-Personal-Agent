package com.aegis.apa

import com.aegis.apa.agent.AppReportBuilder
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.AppCategory
import com.aegis.apa.model.DetectedApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReportBuilderTest {
    private val detectedApps = listOf(
        DetectedApp("小米社区", AppCategory.COMMON, "com.xiaomi.vipaccount", true),
        DetectedApp("KernelSU", AppCategory.ROOT_AND_FRAMEWORK, null, true),
        DetectedApp("Magisk", AppCategory.ROOT_AND_FRAMEWORK, null, false)
    )

    @Test
    fun englishReportLocalizesLabelsAndPreservesRawAppEvidence() {
        val report = AppReportBuilder.build(
            launchableAppCount = 143,
            detectedApps = detectedApps,
            language = AppLanguage.EN
        )

        assertTrue(report.contains("Launchable apps: 143"))
        assertTrue(report.contains("小米社区: Installed (com.xiaomi.vipaccount)"))
        assertTrue(report.contains("KernelSU: Installed (package name unavailable)"))
        assertTrue(report.contains("Magisk: Not detected"))
        listOf("可启动应用数量", "已安装", "未检测到", "包名未知").forEach {
            assertFalse("unexpected Chinese template: $it", report.contains(it))
        }
    }

    @Test
    fun chineseReportRemainsTheDefault() {
        val report = AppReportBuilder.build(143, detectedApps)

        assertTrue(report.contains("可启动应用数量：143"))
        assertTrue(report.contains("小米社区：已安装（com.xiaomi.vipaccount）"))
        assertTrue(report.contains("KernelSU：已安装（包名未知）"))
        assertTrue(report.contains("Magisk：未检测到"))
    }
}
