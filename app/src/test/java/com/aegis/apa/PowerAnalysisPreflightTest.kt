package com.aegis.apa

import com.aegis.apa.agent.PowerAnalysisPreflight
import com.aegis.apa.agent.PowerQuestionIntent
import com.aegis.apa.agent.PowerQuestionSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerAnalysisPreflightTest {
    @Test
    fun classifiesRateAttributionAndOrdinaryBatteryQuestionsSeparately() {
        assertEquals(PowerQuestionIntent.DRAIN_RATE, PowerAnalysisPreflight.classify("今天耗电快吗"))
        assertEquals(PowerQuestionIntent.ATTRIBUTION, PowerAnalysisPreflight.classify("哪个应用在后台耗电"))
        assertEquals(PowerQuestionIntent.ATTRIBUTION, PowerAnalysisPreflight.classify("为什么今天耗电这么快"))
        assertEquals(PowerQuestionIntent.DRAIN_RATE, PowerAnalysisPreflight.classify("电量一小时掉了 5%，正常吗"))
        assertEquals(PowerQuestionIntent.ATTRIBUTION, PowerAnalysisPreflight.classify("哪个软件最费电"))
        assertEquals(PowerQuestionIntent.ATTRIBUTION, PowerAnalysisPreflight.classify("电量损耗是谁造成的"))
        assertEquals(PowerQuestionIntent.OTHER, PowerAnalysisPreflight.classify("电池温度正常吗"))
    }

    @Test
    fun detectionRequiresBatteryContextAndKeepsMixedIntents() {
        assertEquals(PowerQuestionSignals(), PowerAnalysisPreflight.detect("哪个软件占内存最多"))
        assertEquals(PowerQuestionSignals(), PowerAnalysisPreflight.detect("游戏帧率掉了"))
        assertEquals(PowerQuestionSignals(), PowerAnalysisPreflight.detect("为什么电池温度高"))
        assertEquals(PowerQuestionSignals(), PowerAnalysisPreflight.detect("下载速度一小时掉了 5%"))
        assertEquals(
            PowerQuestionSignals(asksDrainRate = true),
            PowerAnalysisPreflight.detect("电池掉得很快")
        )
        assertEquals(
            PowerQuestionSignals(asksDrainRate = true),
            PowerAnalysisPreflight.detect("手机太费电了")
        )
        assertEquals(
            PowerQuestionSignals(asksDrainRate = true, asksAttribution = true),
            PowerAnalysisPreflight.detect("为什么今天耗电这么快")
        )
        assertEquals(
            PowerQuestionSignals(asksAttribution = true),
            PowerAnalysisPreflight.detect("这个应用很耗电吗")
        )
        assertEquals(
            PowerQuestionSignals(asksDrainRate = true, asksAttribution = true),
            PowerAnalysisPreflight.detect("哪个应用导致耗电快")
        )
        assertEquals(
            PowerQuestionSignals(asksDrainRate = true, asksAttribution = true),
            PowerAnalysisPreflight.detect("电量每小时掉 5%，哪个应用造成的")
        )
    }

    @Test
    fun blocksDrainQuestionsUntilARealObservationExists() {
        val message = PowerAnalysisPreflight.blockingMessage(
            question = "今天续航怎么样",
            diagnosticAvailable = false,
            diagnosticSelected = false
        )

        assertTrue(message.orEmpty().contains("开始续航观察"))
        assertTrue(message.orEmpty().contains("至少 30 分钟"))
        assertFalse(message.orEmpty().contains("Scene 报告"))
    }

    @Test
    fun explainsWhenImportedReportWasManuallyExcluded() {
        val message = PowerAnalysisPreflight.blockingMessage(
            question = "谁在后台耗电",
            diagnosticAvailable = true,
            diagnosticSelected = false
        )

        assertTrue(message.orEmpty().contains("已经导入"))
        assertTrue(message.orEmpty().contains("没有附加"))
    }

    @Test
    fun allowsSelectedDiagnosticAndUnrelatedBatteryQuestions() {
        assertNull(PowerAnalysisPreflight.blockingMessage("为什么耗电", true, true, false))
        assertNull(PowerAnalysisPreflight.blockingMessage("电池温度正常吗", false, false, false))
    }

    @Test
    fun validObservationAnswersDrainRateButNotAppAttribution() {
        assertNull(
            PowerAnalysisPreflight.blockingMessage(
                question = "今天耗电快吗",
                diagnosticAvailable = false,
                diagnosticSelected = false,
                observationAvailable = true
            )
        )
        val attribution = PowerAnalysisPreflight.blockingMessage(
            question = "哪个应用在后台耗电",
            diagnosticAvailable = false,
            diagnosticSelected = false,
            observationAvailable = true
        )
        assertTrue(attribution.orEmpty().contains("系统报告"))
    }

    @Test
    fun loadedDiagnosticCannotReplaceObservationForDrainRate() {
        val selected = PowerAnalysisPreflight.blockingMessage(
            question = "今天耗电快吗",
            diagnosticAvailable = true,
            diagnosticSelected = true,
            observationAvailable = false
        )
        val excluded = PowerAnalysisPreflight.blockingMessage(
            question = "今天耗电快吗",
            diagnosticAvailable = true,
            diagnosticSelected = false,
            observationAvailable = false
        )

        assertTrue(selected.orEmpty().contains("开始续航观察"))
        assertTrue(excluded.orEmpty().contains("开始续航观察"))
    }

    @Test
    fun mixedQuestionIsNeverSilentlyReducedToOneAnswer() {
        val message = PowerAnalysisPreflight.blockingMessage(
            question = "为什么今天耗电这么快",
            diagnosticAvailable = true,
            diagnosticSelected = true,
            observationAvailable = true
        )

        assertTrue(message.orEmpty().contains("两个问题"))
        assertTrue(message.orEmpty().contains("掉电速度"))
        assertTrue(message.orEmpty().contains("具体应用"))
    }
}
