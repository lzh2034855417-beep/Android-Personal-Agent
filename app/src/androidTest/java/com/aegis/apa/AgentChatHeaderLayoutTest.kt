package com.aegis.apa

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aegis.apa.agent.ApiSession
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.localization.withAppLanguage
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real chat layout with inert callbacks: no stored conversation or cloud request. */
class AgentChatHeaderLayoutTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var activity: ComponentActivity

    @Before fun openEmptyTestActivity() {
        ApiSession.update(null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        // Some device ROMs reject ActivityScenario's background launch. Use the
        // shell's explicit foreground launch, without granting any permission.
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n $packageName/androidx.activity.ComponentActivity"
        ).close()
        rule.waitUntil(timeoutMillis = 10_000) {
            var resumed: ComponentActivity? = null
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ComponentActivity>().firstOrNull()
            }
            resumed?.also { activity = it } != null
        }
    }

    @After fun closeTestActivity() {
        if (::activity.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() }
        }
    }

    // An unweighted title and action row squeezes Settings into multiple lines.
    @Test fun englishSettingsRemainsCompleteAndClickableAt320dp() {
        var openedSettings = false
        showChat(AppLanguage.EN, 320, 1f) { openedSettings = true }

        assertAccessibleAction("Settings")
        assertAccessibleAction("Clear")
        rule.onNodeWithContentDescription("Clear").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.runOnIdle { assertTrue(openedSettings) }
    }

    @Test fun englishSettingsDoesNotWrapAtLargeFont() {
        showChat(AppLanguage.EN, 320, 1.5f)

        assertAccessibleAction("Settings")
        assertAccessibleAction("Clear")
    }

    @Test fun chineseSettingsRemainsCompleteAtNarrowWidth() {
        showChat(AppLanguage.ZH_CN, 280, 1.5f)

        assertAccessibleAction("设置")
        assertAccessibleAction("清空")
    }

    @Test fun collapsedHeaderLeavesAtLeastHalfTheScreenForChatAtLargeFont() {
        showChat(AppLanguage.ZH_CN, 320, 1.5f)
        val density = activity.resources.displayMetrics.density
        val timeline = rule.onNodeWithTag("chat_timeline").fetchSemanticsNode().boundsInRoot
        assertTrue("Chat viewport is too small: ${timeline.height / density}dp",
            timeline.height / density >= 360f)
        val header = rule.onNodeWithTag("agent_chat_header").fetchSemanticsNode().boundsInRoot
        assertTrue("Header must stay compact", header.height / density <= 96f)
    }

    @Test fun infoKeepsPrivacyAndVerificationAccessibleWithoutTakingSpaceByDefault() {
        var openedVerification = false
        showChat(AppLanguage.EN, 320, 1f, onOpenVerification = { openedVerification = true })
        rule.onNodeWithText("Chat may contain device details.", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Info").performClick()
        rule.onNodeWithText("Chat may contain device details.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Local verification records", substring = true).performClick()
        rule.runOnIdle { assertTrue(openedVerification) }
        rule.onNodeWithContentDescription("Info").performClick()
        rule.onNodeWithText("Chat may contain device details.", substring = true).assertDoesNotExist()
    }

    @Test fun storageFailureIsVisibleEvenWhenInfoIsCollapsed() {
        showChat(AppLanguage.EN, 320, 1f, storageError = true)
        rule.onNodeWithText("History could not be read or saved.", substring = true).assertIsDisplayed()
    }

    @Test fun rootRecheckDoesNotPermanentlyDowngradeTheSelectedReport() {
        val page = AgentPageState().apply { selectedLevel.value = "Level 2" }
        val authorized = mutableStateOf(true)
        val pending = mutableStateOf(false)
        showChat(AppLanguage.EN, 320, 1f, pageState = page,
            rootAuthorized = { authorized.value }, rootPending = { pending.value })
        rule.runOnIdle { assertEquals("Level 2", page.selectedLevel.value) }
        rule.runOnIdle { pending.value = true; authorized.value = false }
        rule.runOnIdle { assertEquals("Level 2", page.selectedLevel.value) }
        rule.runOnIdle { authorized.value = true; pending.value = false }
        rule.runOnIdle { assertEquals("Level 2", page.selectedLevel.value) }
        rule.runOnIdle { pending.value = true; authorized.value = false }
        rule.runOnIdle { pending.value = false }
        rule.runOnIdle { assertEquals("Level 0", page.selectedLevel.value) }
    }

    private fun assertAccessibleAction(label: String) {
        rule.onNodeWithText("APA Agent").assertIsDisplayed()
        rule.onNodeWithContentDescription(label).assertIsDisplayed()
        val bounds = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
        val density = activity.resources.displayMetrics.density
        assertTrue("$label must keep a 48dp touch target: ${bounds.width / density}dp",
            bounds.width + 1f >= 48f * density)
    }

    private fun showChat(
        language: AppLanguage,
        widthDp: Int,
        fontScale: Float,
        onOpenVerification: () -> Unit = {},
        storageError: Boolean = false,
        pageState: AgentPageState? = null,
        rootAuthorized: () -> Boolean = { false },
        rootPending: () -> Boolean = { false },
        onOpenSettings: () -> Unit = {}
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.setContent {
            val context = LocalContext.current
            val localizedContext = remember(context, language) { context.withAppLanguage(language) }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalAppLanguage provides language,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                AndroidPersonalAgentTheme {
                    Box(Modifier.width(widthDp.dp).height(720.dp)) {
                        AgentChatScreen(
                            state = pageState ?: remember { AgentPageState() }, messages = emptyList(),
                            onAnalyze = { _, _, _ -> }, isOnlineAnalyzing = false,
                            onOnlineAnalyze = { _, _, _, _, _, _ -> },
                            onPreflightMessage = { _, _, _ -> }, onLocalEvidenceMessage = { _, _, _ -> },
                            onClearConversation = {}, onOpenSettings = onOpenSettings,
                            onOpenVerification = onOpenVerification, conversationStorageError = storageError,
                            powerDiagnosticState = PowerDiagnosticUiState.Idle, powerDiagnostic = null,
                            batteryObservationStart = null, batteryObservationResult = null,
                            batteryObservationNotice = null, rootAvailable = false,
                            rootAdviceAuthorized = rootAuthorized(),
                            rootAuthorizationPending = rootPending(),
                            onCollectPowerDiagnostic = {}, onImportBugReport = {},
                            onStartBatteryObservation = {}, onFinishBatteryObservation = {},
                            onClearBatteryObservation = {}, onRemovePowerDiagnostic = {},
                            onCopyPackage = {}, onCopyMessage = {}
                        )
                    }
                }
            }
        } }
    }
}
