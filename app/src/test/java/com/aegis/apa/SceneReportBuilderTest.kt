package com.aegis.apa

import com.aegis.apa.tool.SceneField
import com.aegis.apa.tool.SceneImportResult
import com.aegis.apa.tool.SceneReportBuilder
import com.aegis.apa.tool.SceneSample
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SceneReportBuilderTest {
    @Test fun calculatesDischargeRateFromTimedBatterySamples() {
        val report = SceneReportBuilder.build(
            SceneImportResult(
                samples = listOf(
                    SceneSample(
                        timestampMillis = OffsetDateTime.parse("2026-09-06T08:00:00+08:00").toInstant().toEpochMilli(),
                        batteryPercent = 90,
                        temperatureCelsius = 31.0,
                        currentMilliAmp = -450.0,
                        powerMilliWatt = 2200.0,
                        foregroundApp = "com.example.game"
                    ),
                    SceneSample(
                        timestampMillis = OffsetDateTime.parse("2026-09-06T10:00:00+08:00").toInstant().toEpochMilli(),
                        batteryPercent = 70,
                        temperatureCelsius = 39.0,
                        currentMilliAmp = -750.0,
                        powerMilliWatt = 3800.0,
                        foregroundApp = "com.example.game"
                    )
                ),
                recognizedFields = SceneField.entries.toSet()
            )
        )

        assertTrue(report.contains("电量变化：90% → 70%（下降 20%）"))
        assertTrue(report.contains("样本覆盖：2.0 小时"))
        assertTrue(report.contains("推算耗电：10.0%/小时"))
        assertTrue(report.contains("最高温度：39.0°C"))
        assertTrue(report.contains("前台应用线索：com.example.game"))
    }

    @Test fun doesNotInferDailyEnduranceWhenTimeEvidenceIsMissing() {
        val report = SceneReportBuilder.build(
            SceneImportResult(
                samples = listOf(SceneSample(batteryPercent = 70)),
                recognizedFields = setOf(SceneField.BATTERY_PERCENT)
            )
        )

        assertTrue(report.contains("没有足够的带时间电量样本"))
        assertTrue(!report.contains("%/小时"))
    }
}
