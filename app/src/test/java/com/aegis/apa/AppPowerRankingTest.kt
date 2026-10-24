package com.aegis.apa

import com.aegis.apa.agent.AppPowerRanking
import com.aegis.apa.agent.PowerDiagnosticReportBuilder
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AppPowerRankingTest {
    @Test fun conflictingWechatIsFlaggedOutsideTheSameWindowRankingInBothLanguages() {
        val sample = snapshot(listOf(
            AppPowerEvidence(10123, listOf("com.tencent.mm"), listOf("微信"),
                estimatedPowerMah = 3148.0, backgroundPowerMah = 2953.0),
            AppPowerEvidence(10124, listOf("video.app"), listOf("Video"), estimatedPowerMah = 428.0)
        ), 1845.0)
        val zh = AppPowerRanking.build(sample, AppLanguage.ZH_CN)
        assertTrue(zh.contains("统计口径待核对"))
        assertTrue(zh.contains("3148.0 mAh"))
        assertTrue(zh.contains("1845.0 mAh"))
        assertTrue(zh.contains("1. Video"))
        assertFalse(zh.contains("1. 微信"))
        assertFalse(zh.contains("170.6%"))
        val en = AppPowerRanking.build(sample, AppLanguage.EN)
        assertTrue(en.contains("measurement basis needs verification"))
        assertFalse(en.contains("1. 微信"))
    }

    @Test fun reportHighlightsWechatRiskWithoutDeclaringNoBackgroundAnomaly() {
        val sample = snapshot(listOf(AppPowerEvidence(10123, listOf("com.tencent.mm"), listOf("微信"),
            estimatedPowerMah = 3148.0, backgroundPowerMah = 2953.0)), 1845.0)
        val report = PowerDiagnosticReportBuilder.build(sample.copy(localVerdict =
            com.aegis.apa.agent.LocalPowerAttributionEngine.attribute(sample)))
        assertTrue(report.contains("待核查"))
        assertFalse(report.contains("没有达到后台异常建议阈值"))
        assertFalse(report.contains("Scene 手动操作"))
    }

    private fun snapshot(apps: List<AppPowerEvidence>, drain: Double? = 1000.0) =
        PowerDiagnosticSnapshot(Instant.EPOCH, 0, emptyMap(), apps,
            SystemPowerEvidence(batteryDrainWindow = drain?.let {
                BatteryDrainWindowEvidence(3_600_000, 5000.0, it, true)
            }), emptyList())

    @Test fun noAnomaliesStillShowsFiveAppsSortedBySameWindowDrain() {
        val apps = (1..6).map { AppPowerEvidence(10000 + it, listOf("app.$it"),
            listOf("App $it"), estimatedPowerMah = it * 10.0) }
        val text = AppPowerRanking.build(snapshot(apps), AppLanguage.ZH_CN)
        assertTrue(text.contains("Top 5"))
        assertTrue(text.indexOf("App 6") < text.indexOf("App 5"))
        assertTrue(text.contains("60.0 mAh"))
        assertTrue(text.contains("6.0%"))
        assertFalse(text.contains("app.1"))
        assertTrue(text.contains("排名高不等于耗电异常"))
    }

    @Test fun sharedUidAndSystemRowsAreNotPresentedAsIndividualApps() {
        val apps = listOf(
            AppPowerEvidence(10010, listOf("app.one"), listOf("One"), estimatedPowerMah = 10.0),
            AppPowerEvidence(10020, listOf("group.a", "group.b"), estimatedPowerMah = 500.0, sharedUid = true),
            AppPowerEvidence(0, emptyList(), estimatedPowerMah = 400.0))
        val text = AppPowerRanking.build(snapshot(apps), AppLanguage.ZH_CN)
        val appPart = text.substringBefore("共享 UID／系统组")
        assertTrue(appPart.contains("One"))
        assertFalse(appPart.contains("500.0 mAh"))
        assertFalse(appPart.contains("UID 0"))
        assertTrue(text.contains("500.0 mAh"))
        assertTrue(text.contains("UID 0"))
    }

    @Test fun missingWindowDenominatorNeverInventsAPercentage() {
        val app = AppPowerEvidence(10001, listOf("app.one"), estimatedPowerMah = 10.0)
        val text = AppPowerRanking.build(snapshot(listOf(app), null), AppLanguage.EN)
        assertTrue(text.contains("10.0 mAh"))
        assertTrue(text.contains("share unavailable"))
        assertFalse(text.contains("%"))
    }

    @Test fun invalidMeasurementsCannotEnterTheRanking() {
        val apps = listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).mapIndexed { index, value ->
            AppPowerEvidence(10000 + index, listOf("invalid.$index"), estimatedPowerMah = value)
        }
        val text = AppPowerRanking.build(snapshot(apps), AppLanguage.EN)
        assertFalse(text.contains("invalid."))
        assertTrue(text.contains("No app-level drain data"))
    }

    @Test fun reportIncludesRankingWithoutAnyAnomalyFindingsInBothLanguages() {
        val sample = snapshot(listOf(AppPowerEvidence(10001, listOf("app.one"),
            listOf("One"), estimatedPowerMah = 10.0)))
        assertTrue(PowerDiagnosticReportBuilder.build(sample).contains("本周期应用耗电 Top 5"))
        assertTrue(PowerDiagnosticReportBuilder.build(sample, language = AppLanguage.EN).contains("App drain Top 5"))
    }

    @Test fun repeatedSharedUidIsNotSummedOrListedTwice() {
        val sample = snapshot(listOf(
            AppPowerEvidence(10010, listOf("app.a"), estimatedPowerMah = 100.0),
            AppPowerEvidence(10010, listOf("app.b"), estimatedPowerMah = 100.0)))
        val text = AppPowerRanking.build(sample, AppLanguage.EN)
        assertEquals(1, Regex("100.0 mAh").findAll(text).count())
        assertFalse(text.contains("200.0 mAh"))
        assertTrue(text.contains("Shared UID 10010"))
    }
}
