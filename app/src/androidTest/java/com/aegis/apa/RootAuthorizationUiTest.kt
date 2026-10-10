package com.aegis.apa

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.lifecycle.ViewModelProvider
import com.aegis.apa.model.RootAccessState
import com.aegis.apa.tool.RootTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Device acceptance: requires this app's existing Root grant; never grants it. */
class RootAuthorizationUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var activity: ComponentActivity? = null

    @After fun closeTestActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() }
    }

    @Test fun devicePageAutomaticallyReadsAndCachesHardwareWithoutScheduling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Acceptance fixture requires the connected Xiaomi with an existing Root grant",
            android.os.Build.MODEL == "2509FPN0BC" && RootTool.read(context).hasSuBinary)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/com.aegis.apa.MainActivity"
        )).use { it.readBytes() }
        rule.waitUntil(10_000) {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
            }
            activity != null
        }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("设备") or hasText("Device")).fetchSemanticsNodes().isNotEmpty()
        }
        val english = rule.onAllNodes(hasText("Device")).fetchSemanticsNodes().isNotEmpty()
        rule.onNode(hasText(if (english) "Device" else "设备") and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        rule.waitUntil(20_000) {
            var ready = false
            instrumentation.runOnMainSync {
                val session = ViewModelProvider(checkNotNull(activity))[MainSessionViewModel::class.java]
                ready = session.rootAccessState.value == RootAccessState.AUTHORIZED &&
                    session.hardwareSupplyInfo.value?.let { it.ramVendor != null && it.storageVendor != null } == true
            }
            ready
        }
        val cache = (context.applicationContext as ApaApplication).hardwareSupplyCache.load()
        assertNotNull(cache)
        assertEquals("三星 (Samsung)", cache?.ramVendor)
        assertEquals("SK hynix", cache?.storageVendor)
        rule.runOnIdle {
            val session = ViewModelProvider(checkNotNull(activity))[MainSessionViewModel::class.java]
            assertEquals(session.hardwareSupplyInfo.value?.ramVendor, cache?.ramVendor)
            assertEquals(session.hardwareSupplyInfo.value?.storageVendor, cache?.storageVendor)
            assertNull(session.rootBatteryInfo.value)
            assertNull(session.deviceProfile.value)
            assertNull(session.powerDiagnostic.value)
        }
    }

    @Test fun existingRootGrantIsDetectedWithoutReadingBatteryOrReports() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("This device acceptance test requires a Root interface", RootTool.read(context).hasSuBinary)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/com.aegis.apa.MainActivity"
        )).use { it.readBytes() }
        rule.waitUntil(10_000) {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
            }
            activity != null
        }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("能力") or hasText("Capabilities")).fetchSemanticsNodes().isNotEmpty()
        }
        val english = rule.onAllNodes(hasText("Capabilities")).fetchSemanticsNodes().isNotEmpty()
        rule.onNodeWithText(if (english) "Capabilities" else "能力").performClick()
        val authorizedLabel = if (english) "Root authorized" else "Root 已授权"
        rule.waitUntil(12_000) {
            rule.onAllNodes(hasText(authorizedLabel)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(authorizedLabel).performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            val session = ViewModelProvider(checkNotNull(activity))[MainSessionViewModel::class.java]
            assertEquals(RootAccessState.AUTHORIZED, session.rootAccessState.value)
            assertNull(session.rootBatteryInfo.value)
            assertNull(session.deviceProfile.value)
            assertNull(session.powerDiagnostic.value)
        }
    }
}
