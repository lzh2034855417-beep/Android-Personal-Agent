package com.aegis.apa

import com.aegis.apa.tool.SceneCsvParser
import com.aegis.apa.tool.SceneField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCsvParserTest {
    @Test fun rejectsOutOfRangePercentBeforeRounding() {
        val result = SceneCsvParser.parse("battery,app\n-0.5,a\n100.5,b")
        assertTrue(result.samples.all { it.batteryPercent == null })
    }

    @Test fun doesNotStripLettersToInventNumericValues() {
        val result = SceneCsvParser.parse("temperature,app\nerror42,a\n1e2,b")
        assertNull(result.samples[0].temperatureCelsius)
        assertEquals(100.0, result.samples[1].temperatureCelsius!!, 0.001)
    }

    @Test fun convertsExplicitHeaderUnitsToCanonicalUnits() {
        val result = SceneCsvParser.parse("current(uA),power(W),app\n-450000,2.2,a")
        assertEquals(-450.0, result.samples.single().currentMilliAmp!!, 0.001)
        assertEquals(2200.0, result.samples.single().powerMilliWatt!!, 0.001)
    }

    @Test fun rejectsAmbiguousDuplicateColumns() {
        assertTrue(SceneCsvParser.parse("battery,电量\n80,20").error != null)
    }

    @Test fun rejectsRowsWithWrongColumnCount() {
        assertTrue(SceneCsvParser.parse("battery,app\n80,a,extra").error != null)
    }

    @Test fun parsesCommonChineseColumnsAndKeepsQuotedApplicationNames() {
        val result = SceneCsvParser.parse(
            """
            时间,电量(%),温度(℃),电流(mA),功耗(mW),前台应用
            2026-09-06 08:00:00,90,31.5,-450,2200,"视频,播放器"
            2026-09-06 09:00:00,82,35.0,-800,3100,com.example.game
            """.trimIndent()
        )

        assertNull(result.error)
        assertEquals(2, result.samples.size)
        assertEquals(90, result.samples.first().batteryPercent)
        assertEquals(31.5, result.samples.first().temperatureCelsius!!, 0.001)
        assertEquals("视频,播放器", result.samples.first().foregroundApp)
        assertTrue(result.recognizedFields.containsAll(SceneField.entries))
    }

    @Test fun acceptsBomAndEnglishHeaders() {
        val result = SceneCsvParser.parse(
            "\uFEFFtimestamp,battery %,temperature,current,power,package\n" +
                "2026-09-06T08:00:00+08:00,70,30.0,-300,1200,com.example.reader"
        )

        assertNull(result.error)
        assertEquals(1, result.samples.size)
        assertEquals(70, result.samples.single().batteryPercent)
        assertEquals("com.example.reader", result.samples.single().foregroundApp)
    }

    @Test fun rejectsFilesWithoutRecognizedTelemetryColumns() {
        val result = SceneCsvParser.parse("name,value\nfoo,bar")

        assertTrue(result.samples.isEmpty())
        assertTrue(result.error!!.contains("未识别"))
    }
}
