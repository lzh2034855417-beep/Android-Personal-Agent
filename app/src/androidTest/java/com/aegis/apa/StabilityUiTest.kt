package com.aegis.apa

import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.agent.ApiKeyStore
import com.aegis.apa.agent.ApiSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StabilityUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun sampleLabel(): String? = rule.onAllNodes(hasText("LAST SAMPLE", substring = true))
        .fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.Text)?.joinToString { it.text }

    @Test fun returningToActivityRefreshesSampleAutomatically() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        val before = sampleLabel()
        rule.runOnUiThread {
            rule.activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        SystemClock.sleep(1_100) // Sample labels have one-second precision.
        // Use the real settings-return path. No permission is granted or revoked by this test.
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "input keyevent KEYCODE_BACK"
        )
        ParcelFileDescriptor.AutoCloseInputStream(command).bufferedReader().use { it.readText() }
        rule.waitUntil(15_000) { sampleLabel() != null && sampleLabel() != before }
        assertNotEquals(before, sampleLabel())
    }

    @Test fun settingsIdentifiesInstalledBuild() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("APA ${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE}").assertIsDisplayed()
    }

    @Test fun localAnalysisCollectsNewSampleAtSendTime() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        val oldTimestamp = requireNotNull(sampleLabel()).substringAfter("最近采样：")
        val credential = ApiKeyStore.load(rule.activity)
        try {
            // Keep this regression fully local, irrespective of the user's saved provider.
            rule.runOnUiThread { ApiSession.update(null) }
            rule.onNodeWithText("Agent").performClick()
            SystemClock.sleep(1_100)
            rule.onNode(hasSetTextAction()).performTextInput("检查设备")
            rule.onNodeWithText("发送").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasText("LOCAL BASELINE", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            val reply = rule.onAllNodes(hasText("采样时间：", substring = true)).fetchSemanticsNodes()
                .first().config[SemanticsProperties.Text].joinToString { it.text }
            assertFalse(reply.contains(oldTimestamp))
        } finally { rule.runOnUiThread { ApiSession.update(credential) } }
    }

    @Test fun sceneImportIsAvailableInReportSelection() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        rule.onNodeWithText("Agent").performClick()
        rule.onNodeWithText("选择报告").performClick()
        rule.onNodeWithText("导入 Scene CSV").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test fun keyboardKeepsComposerVisibleAndNavigationReturnsAfterBack() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        rule.onNodeWithText("Agent").performClick()
        rule.onNodeWithText("能力").assertIsDisplayed()
        rule.onNodeWithText("选择报告").performClick()
        val input = rule.onNode(hasSetTextAction())
        try {
            input.performClick()
            // Require a real soft keyboard, so hardware-keyboard test setups cannot silently pass.
            rule.waitUntil(5_000) {
                rule.runOnIdle {
                    ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
            }
            input.performTextInput("保留草稿")
            input.assertIsDisplayed()
            val inputBottom = input.fetchSemanticsNode().boundsInWindow.bottom
            val gap = rule.runOnIdle {
                val decor = rule.activity.window.decorView
                val ime = requireNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(WindowInsetsCompat.Type.ime())
                (decor.height - ime.bottom - inputBottom) / rule.activity.resources.displayMetrics.density
            }
            assertTrue("Composer-to-keyboard gap was $gap dp", gap in 0f..48f)
            rule.onNodeWithText("发送").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("能力").assertDoesNotExist()
            // Never press Send: this regression is independent of saved cloud credentials.
            Espresso.pressBack()
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasText("能力")).fetchSemanticsNodes().isNotEmpty()
            }
            input.assertIsDisplayed().assertTextContains("保留草稿")
            rule.onNodeWithText("能力").assertIsDisplayed().performClick()
            rule.onNodeWithText("能力中心").assertIsDisplayed()
        } finally {
            Espresso.closeSoftKeyboard()
        }
    }
}
