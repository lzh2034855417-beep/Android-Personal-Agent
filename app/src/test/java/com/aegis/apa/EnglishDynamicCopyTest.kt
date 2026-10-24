package com.aegis.apa

import com.aegis.apa.agent.AgentAttachmentPolicy
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.DeviceUiCopy
import com.aegis.apa.model.AppCategory
import com.aegis.apa.model.CapabilityAccessFeedback
import com.aegis.apa.model.DisplayInfo
import com.aegis.apa.model.ShizukuAccessState
import com.aegis.apa.model.UsageSummary
import com.aegis.apa.tool.DisplayReportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishDynamicCopyTest {
    @Test
    fun capabilityActionsUseEnglishWithoutChangingAuthorizationState() {
        val feedback = CapabilityAccessFeedback(
            usageAccessGranted = true,
            shizukuAccessState = ShizukuAccessState.SERVICE_UNAVAILABLE,
            rootAuthorized = false
        )

        assertEquals("Usage access granted", feedback.usageActionLabel(AppLanguage.EN))
        assertEquals("Open Shizuku", feedback.shizukuActionLabel(AppLanguage.EN))
        assertEquals("Open Root manager", feedback.rootActionLabel(AppLanguage.EN))
        assertFalse(feedback.usageActionEnabled)
    }

    @Test
    fun agentDisclosureIsFullyEnglishInEnglishMode() {
        val text = AgentAttachmentPolicy.disclosure(true, AppLanguage.EN)

        assertTrue(text.contains("Sent online"))
        assertTrue(text.contains("Root raw output is not sent"))
        assertFalse(text.contains("在线发送"))
    }

    @Test
    fun displayReportUsesEnglishLabelsAndPreservesReadings() {
        val text = DisplayReportText.format(
            DisplayInfo(1200, 2608, 480, 120f, 120f),
            AppLanguage.EN
        )

        assertTrue(text.contains("Resolution: 1200 × 2608"))
        assertTrue(text.contains("Display density: 480 dpi"))
        assertTrue(text.contains("Current refresh rate: 120 Hz"))
        assertFalse(text.contains("分辨率"))
    }

    @Test
    fun appCategoryAndDetectionStateUseEnglishButRawAppLabelIsPreserved() {
        assertEquals("Root & frameworks", AppCategory.ROOT_AND_FRAMEWORK.displayName(AppLanguage.EN))
        assertEquals("Regular apps", AppCategory.COMMON.displayName(AppLanguage.EN))
        assertEquals("Installed", DeviceUiCopy.installedState(true, AppLanguage.EN))
        assertEquals("Not detected", DeviceUiCopy.installedState(false, AppLanguage.EN))
        assertEquals("小米社区", DeviceUiCopy.rawLabel("小米社区"))
    }

    @Test
    fun knownBatteryAndDurationValuesAreLocalized() {
        assertEquals("Charging", DeviceUiCopy.batteryStatus("正在充电", AppLanguage.EN))
        assertEquals("Good", DeviceUiCopy.batteryHealth("良好", AppLanguage.EN))
        assertEquals("AC", DeviceUiCopy.plugged("交流电", AppLanguage.EN))
        assertEquals("AC + USB", DeviceUiCopy.plugged("交流电 + USB", AppLanguage.EN))
        assertEquals("0 h 0 min", DeviceUiCopy.duration(0, AppLanguage.EN))
    }

    @Test
    fun usageRangeUsesAnEnglishSeparator() {
        val summary = UsageSummary(
            accessGranted = true,
            foregroundTimeMillis = 0,
            topApps = emptyList(),
            startTimeMillis = 0,
            endTimeMillis = 60_000
        )

        assertTrue(summary.rangeText(AppLanguage.EN)!!.contains(" to "))
        assertFalse(summary.rangeText(AppLanguage.EN)!!.contains(" 至 "))
    }
}
