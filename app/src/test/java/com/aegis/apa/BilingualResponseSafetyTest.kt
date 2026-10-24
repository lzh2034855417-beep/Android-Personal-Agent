package com.aegis.apa

import com.aegis.apa.agent.PowerQuestionIntent
import com.aegis.apa.agent.sanitizeCloudAnalysisResponse
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class BilingualResponseSafetyTest {
    @Test fun attachedChineseReportFactsSurviveButPostObjectRequestsAreRemoved() {
        val result = sanitizeCloudAnalysisResponse("已导入的报告提供了 120 mAh。请将系统报告上传。", "Level 0", reportAttached = true)
        assertTrue(result.contains("已导入的报告提供了 120 mAh"))
        assertFalse(result.contains("请将系统报告上传"))
    }
    @Test fun bulletFormattedActionsCannotBypassEitherCapabilityGuard() {
        for (deviceMode in listOf(true, false)) {
            val result = sanitizeCloudAnalysisResponse("- Freeze WeChat.\n• Throttle the CPU.\n+ Disable the app org.fixture.app.",
                if (deviceMode) "Level 0" else null, deviceAdviceMode = deviceMode, language = AppLanguage.EN)
            assertFalse(result.contains("Freeze WeChat"))
            assertFalse(result.contains("Throttle the CPU"))
            assertFalse(result.contains("org.fixture.app"))
        }
    }
    @Test fun standaloneNamedAppDisableIsBlockedButOrdinaryRadioSettingIsKept() {
        val result = sanitizeCloudAnalysisResponse("Disable WeChat. Disable Bluetooth temporarily.", "Level 0", language = AppLanguage.EN)
        assertFalse(result.contains("Disable WeChat"))
        assertTrue(result.contains("Disable Bluetooth temporarily"))
    }

    @Test fun mixedLanguageNegativeWarningSurvives() {
        assertTrue(sanitizeCloudAnalysisResponse("不要 freeze WeChat。", "Level 0").contains("不要 freeze WeChat"))
    }

    @Test fun alreadyAttachedDiagnosticCannotBeRequestedUnderItsChineseName() {
        val result = sanitizeCloudAnalysisResponse("请重新上传系统耗电诊断。已有证据：120 mAh。", "Level 0", reportAttached = true)
        assertFalse(result.contains("重新上传"))
        assertTrue(result.contains("已有证据：120 mAh"))
    }

    @Test fun nonComPackageDisableIsBlocked() {
        assertFalse(sanitizeCloudAnalysisResponse("Disable org.telegram.messenger.", "Level 0", language = AppLanguage.EN).contains("org.telegram.messenger"))
    }

    @Test fun ordinaryBackgroundDataSettingIsNotAppDisable() {
        assertTrue(sanitizeCloudAnalysisResponse("Disable app background data temporarily.", "Level 0", language = AppLanguage.EN).contains("Disable app background data"))
    }

    @Test fun throttleNounInTechnicalExplanationIsNotAnAction() {
        assertTrue(sanitizeCloudAnalysisResponse("A CPU throttle reduces frequency.", null, deviceAdviceMode = false, language = AppLanguage.EN).contains("A CPU throttle reduces frequency"))
    }

    @Test fun reportUploadEventsAreFactsNotReuploadRequest() {
        val result = sanitizeCloudAnalysisResponse("Upload events in the imported report account for 120 mAh.", "Level 0", reportAttached = true, language = AppLanguage.EN)
        assertTrue(result.contains("Upload events in the imported report account for 120 mAh"))
    }

    @Test fun mixedLanguageDoNotUploadWarningIsRetained() {
        assertTrue(sanitizeCloudAnalysisResponse("不要 upload 系统报告。", "Level 0", reportAttached = true).contains("不要 upload 系统报告"))
    }
    @Test fun levelZeroRemovesEnglishFreezeAndThrottleButPreservesSameLineFacts() {
        val result = sanitizeCloudAnalysisResponse(
            "Evidence: com.fixture.app used 120 mAh. Freeze WeChat. Throttle the CPU. Observe the next comparable window.",
            "Level 0", language = AppLanguage.EN)
        assertTrue(result.contains("com.fixture.app used 120 mAh"))
        assertTrue(result.contains("Observe the next comparable window"))
        assertFalse(result.contains("Freeze WeChat"))
        assertFalse(result.contains("Throttle the CPU"))
    }

    @Test fun levelZeroBlocksDisablingAnAppButKeepsOrdinarySettingAdvice() {
        val result = sanitizeCloudAnalysisResponse(
            "Disable the app com.fixture.app. Disable adaptive brightness temporarily. Disable app notifications only if you accept missed alerts.",
            "Level 0", language = AppLanguage.EN)
        assertFalse(result.contains("Disable the app com.fixture.app"))
        assertTrue(result.contains("Disable adaptive brightness temporarily"))
        assertTrue(result.contains("Disable app notifications"))
    }

    @Test fun generalChatBlocksEnglishActionWithoutDeletingTechnicalExplanation() {
        val result = sanitizeCloudAnalysisResponse(
            "Freezing an app prevents its background execution. Please freeze WeChat. Try throttling the CPU.",
            null, deviceAdviceMode = false, language = AppLanguage.EN)
        assertTrue(result.contains("Freezing an app prevents its background execution"))
        assertFalse(result.contains("Please freeze WeChat"))
        assertFalse(result.contains("Try throttling the CPU"))
    }

    @Test fun safeNegativeWarningIsNotMistakenForAnAction() {
        val response = "Do not freeze messaging apps. Never throttle the CPU based on one reading."
        val result = sanitizeCloudAnalysisResponse(response, "Level 0", language = AppLanguage.EN)
        assertTrue(result.contains("Do not freeze messaging apps"))
        assertTrue(result.contains("Never throttle the CPU"))
    }

    @Test fun attachedReportRemovesEnglishReuploadRequestButKeepsExistingEvidence() {
        val result = sanitizeCloudAnalysisResponse(
            "Please upload the Bug Report again. The imported report provides 120 mAh. Compare that value within its recorded window.",
            "Level 0", reportAttached = true, language = AppLanguage.EN)
        assertFalse(result.contains("Please upload"))
        assertTrue(result.contains("The imported report provides 120 mAh"))
        assertTrue(result.contains("Compare that value"))
    }

    @Test fun attachedReportRemovesMixedLanguageRequestWithoutDroppingSafeSentence() {
        val result = sanitizeCloudAnalysisResponse(
            "请 upload 系统报告。已有证据：120 mAh。", "Level 0", reportAttached = true)
        assertFalse(result.contains("upload"))
        assertTrue(result.contains("已有证据：120 mAh"))
    }

    @Test fun unattachedReportCanStillRequestRequiredEvidenceInEnglish() {
        val response = "Generate and import an Android Bug Report for app attribution."
        assertEquals(response, sanitizeCloudAnalysisResponse(response, "Level 0", reportAttached = false, language = AppLanguage.EN))
    }

    @Test fun englishHealthResponseDropsAppDrainSectionsButKeepsOfficialTest() {
        val result = sanitizeCloudAnalysisResponse(
            "Known health evidence: status good.\nTotal consumption: com.fixture.app 120 mAh.\nBackground judgement: freeze com.fixture.app.\nNext step: request an official battery test.",
            "Level 2", PowerQuestionIntent.BATTERY_HEALTH, language = AppLanguage.EN)
        assertTrue(result.contains("Known health evidence: status good"))
        assertTrue(result.contains("official battery test"))
        assertFalse(result.contains("com.fixture.app"))
    }

    @Test fun authorizedAdvancedAdviceIsNotSilentlyRemovedByTheLevelZeroFilter() {
        val response = "Freeze com.fixture.app only as the local verdict permits; restore the previous policy to roll back."
        assertEquals(response, sanitizeCloudAnalysisResponse(response, "Level 2", language = AppLanguage.EN))
    }
}
