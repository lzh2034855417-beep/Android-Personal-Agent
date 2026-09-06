package com.aegis.apa

import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.agent.ApiKeyStore
import com.aegis.apa.agent.ApiSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
            "am start -W -n com.aegis.apa/.MainActivity"
        )
        ParcelFileDescriptor.AutoCloseInputStream(command).bufferedReader().use { it.readText() }
        rule.waitUntil(15_000) { sampleLabel() != null && sampleLabel() != before }
        assertNotEquals(before, sampleLabel())
    }

    @Test fun settingsIdentifiesInstalledBuild() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("APA ${BuildConfig.VERSION_NAME} · debug").assertIsDisplayed()
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
            rule.onNodeWithText("输入消息").performTextInput("检查设备")
            rule.onNodeWithText("发送").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasText("LOCAL BASELINE", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            val reply = rule.onAllNodes(hasText("采样时间：", substring = true)).fetchSemanticsNodes()
                .first().config[SemanticsProperties.Text].joinToString { it.text }
            assertFalse(reply.contains(oldTimestamp))
        } finally { rule.runOnUiThread { ApiSession.update(credential) } }
    }

    @Test fun scenePlaceholderCannotBeAttached() {
        rule.waitUntil(15_000) { sampleLabel() != null }
        rule.onNodeWithText("Agent").performClick()
        rule.onNodeWithText("报告  L0").performClick()
        rule.onNodeWithText("Scene 续航（开发中）").assertIsNotEnabled()
    }
}
