package com.aegis.apa

import com.aegis.apa.agent.PowerAnalysisPreflight
import com.aegis.apa.agent.PowerQuestionIntent
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
        assertEquals(PowerQuestionIntent.OTHER, PowerAnalysisPreflight.classify("电池温度正常吗"))
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
}
