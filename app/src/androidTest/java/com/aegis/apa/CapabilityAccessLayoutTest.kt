package com.aegis.apa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.aegis.apa.model.RootStatus
import com.aegis.apa.model.ShizukuAccessState
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Rule
import org.junit.Test

class CapabilityAccessLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun confirmedCapabilitiesShowDisabledGrayFeedbackActions() {
        rule.setContent {
            AndroidPersonalAgentTheme {
                Box(Modifier.fillMaxSize()) {
                    CapabilitySectionsScreen(
                        launchableAppCount = 42,
                        usageAccessGranted = true,
                        rootStatus = RootStatus(
                            hasSuBinary = true,
                            isShizukuInstalled = true,
                            kernelSuManagerPackage = "me.weishu.kernelsu",
                            isMagiskManagerInstalled = false
                        ),
                        shizukuAccessState = ShizukuAccessState.AUTHORIZED,
                        rootAuthorized = true,
                        rootBatteryInfo = null,
                        isRootBatteryReading = false,
                        deviceProfile = null,
                        isDeviceProfileReading = false,
                        onOpenUsageAccessSettings = {},
                        onShizukuAction = {},
                        onOpenRootManager = {},
                        onReadRootBattery = {},
                        onReadDeviceProfile = {}
                    )
                }
            }
        }

        rule.onNodeWithText("已获得使用情况访问").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("Shizuku 已授权").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("Root 已授权").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }
}
