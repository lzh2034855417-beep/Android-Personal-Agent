package com.aegis.apa

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.agent.ApiKeyStore
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class SettingsStateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun restoredProviderLoadsItsOwnCredentialSlot() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedName = "page_state_test_apa_api_credentials"
        val isolatedContext = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("page_state_test_$name", mode)
        }
        // Only isolated test preferences are touched; no saved user credential is read.
        base.deleteSharedPreferences(isolatedName)
        try {
            assertNotNull(ApiKeyStore.save(isolatedContext, "DeepSeek", "synthetic-deepseek"))
            assertNotNull(ApiKeyStore.save(isolatedContext, "OpenAI · GPT", "synthetic-openai"))
            val restoration = StateRestorationTester(rule)
            restoration.setContent {
                CompositionLocalProvider(LocalContext provides isolatedContext) {
                    AndroidPersonalAgentTheme { SettingsPrivacyScreen() }
                }
            }
            rule.onNodeWithText("DeepSeek", substring = true).performScrollTo().performClick()
            rule.onNode(hasSetTextAction()).assertTextContains("synthetic-deepseek")
            restoration.emulateSavedInstanceStateRestore()
            rule.onNodeWithText("已选择：DeepSeek").assertExists()
            rule.onNode(hasSetTextAction()).assertTextContains("synthetic-deepseek")
        } finally {
            base.deleteSharedPreferences(isolatedName)
        }
    }
}
