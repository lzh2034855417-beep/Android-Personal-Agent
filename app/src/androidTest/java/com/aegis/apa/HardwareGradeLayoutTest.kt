package com.aegis.apa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.aegis.apa.model.BatteryInfo
import com.aegis.apa.model.DeviceIdentifiers
import com.aegis.apa.model.DeviceInfo
import com.aegis.apa.model.DeviceIdentity
import com.aegis.apa.model.DeviceNameSource
import com.aegis.apa.model.DisplayInfo
import com.aegis.apa.model.RamInfo
import com.aegis.apa.model.StorageInfo
import com.aegis.apa.model.UsageSummary
import com.aegis.apa.tool.CpuPolicyProfile
import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HardwareGradeLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun topGradeValueStaysOnOneLineOnANarrowScreen() {
        rule.setContent {
            AndroidPersonalAgentTheme {
                Box(Modifier.width(280.dp)) {
                    DeviceReportScreen(
                        deviceInfo = DeviceInfo(
                            identity = DeviceIdentity(
                                identifiers = DeviceIdentifiers("Xiaomi", "2509FPN0BC"),
                                displayName = "Xiaomi 17 Pro Max",
                                nameSource = DeviceNameSource.CURATED
                            ),
                            androidRelease = "17",
                            apiLevel = 37
                        ),
                        batteryInfo = BatteryInfo(97, "正在充电"),
                        displayInfo = DisplayInfo(1200, 2608, 480, 120f, 120f),
                        ramInfo = RamInfo(16L * GIB, 8L * GIB, false),
                        storageInfo = StorageInfo(512L * GIB, 256L * GIB),
                        usageSummary = UsageSummary(false, null, emptyList()),
                        rootBatteryInfo = null,
                        sampledAt = "11:19:00",
                        deviceProfile = DeviceProfileSnapshot(
                            model = "2509FPN0BC",
                            device = null,
                            soc = "SM8850",
                            androidVersion = "17",
                            buildVersion = null,
                            kernelVersion = null,
                            cpuPresent = "0-7",
                            cpuOnline = "0-7",
                            cpuPolicies = listOf(
                                CpuPolicyProfile("policy7", listOf(7), null, 4_608_000, "walt")
                            ),
                            thermalSensors = emptyList(),
                            access = DeviceProfileAccess.STANDARD
                        ),
                        isDeviceProfileReading = false,
                        onReadDeviceProfile = {},
                        onOpenUsageAccessSettings = {},
                        onRefresh = {}
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("顶级金标 · 性能配置充足")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }

        assertEquals(1, layouts.single().lineCount)
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
