package com.aegis.apa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource
import com.aegis.apa.tool.DeviceProfileSnapshot
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HardwareSupplyLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun recognizedSuppliersShowOnlyObjectiveHardwareDetails() {
        showDeviceScreen(
            HardwareSupplyInfo(
                ramVendor = "三星 (Samsung)",
                storageVendor = "SK hynix",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            )
        )

        rule.onNodeWithText("RAM：16GB · 三星 (Samsung)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("ROM：512GB · SK hynix").performScrollTo().assertIsDisplayed()
    }

    @Test fun overviewMergesDeviceCardWithoutRepeatingIdentity() {
        showDeviceScreen(hardware = null)

        assertEquals(
            1,
            rule.onAllNodes(hasText("Xiaomi 17 Pro Max")).fetchSemanticsNodes().size
        )
        rule.onNodeWithText("你的手机").assertDoesNotExist()
        rule.onNodeWithText("免 Key · 本地生成").assertDoesNotExist()
        rule.onNodeWithText("系统型号").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Android 17 · API 37").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Android 17（API 37）").assertDoesNotExist()
        rule.onNodeWithText("设备").performClick()
        assertEquals(
            2,
            rule.onAllNodes(hasText("Android 17 · API 37", substring = true)).fetchSemanticsNodes().size
        )
        assertEquals(0, rule.onAllNodes(hasText("Android 17（API 37）", substring = true)).fetchSemanticsNodes().size)
        rule.onNodeWithText("设备与系统").assertDoesNotExist()
        rule.onNodeWithText("名称来源").assertDoesNotExist()
        rule.onNodeWithText("项目机型映射").assertDoesNotExist()
        rule.onNodeWithText("供应信息").assertDoesNotExist()
        rule.onNodeWithText("内存与存储").assertDoesNotExist()
        rule.onNodeWithText("RAM：16GB · 待读取").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("ROM：512GB · 待读取").performScrollTo().assertIsDisplayed()
    }

    @Test fun pendingSupplierBadgeOffersLocalBugReportImport() {
        var importRequested = false
        showDeviceScreen(hardware = null, onImportHardwareReport = { importRequested = true })

        val importActions = rule.onAllNodes(hasText("导入系统报告读取厂商"))
        assertEquals(1, importActions.fetchSemanticsNodes().size)
        importActions[0]
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithText("本地解析，不上传；无需 Root。").assertDoesNotExist()
        rule.onNodeWithText("复制识别信息").assertDoesNotExist()
        rule.runOnIdle { assertTrue(importRequested) }
    }

    @Test fun busySupplierImportDisablesTheSingleEntryPoint() {
        showDeviceScreen(hardware = null, isHardwareReportBusy = true)

        val busyActions = rule.onAllNodes(hasText("系统报告处理中…"))
        assertEquals(1, busyActions.fetchSemanticsNodes().size)
        busyActions.fetchSemanticsNodes().indices.forEach { index ->
            busyActions[index].performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun sharedBugReportHelpIncludesHardwareScopeAndGenerationPaths() {
        rule.setContent {
            AndroidPersonalAgentTheme {
                BugReportImportHelpContent()
            }
        }

        rule.onNodeWithText("*#*#284#*#*", substring = true).assertIsDisplayed()
        rule.onNodeWithText("开发者选项", substring = true).assertIsDisplayed()
        rule.onNodeWithText("耗电诊断白名单和硬件供应商字段", substring = true).assertIsDisplayed()
    }

    @Test fun overviewCanExpandAndCollapseBugReportHelp() {
        showDeviceScreen(hardware = null)

        rule.onNodeWithText("怎么生成系统报告")
            .performScrollTo()
            .performClick()
        rule.onNodeWithText("*#*#284#*#*", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("收起生成步骤")
            .performScrollTo()
            .performClick()
        rule.onNodeWithText("*#*#284#*#*", substring = true).assertDoesNotExist()
    }

    @Test fun unknownSupplierOffersSanitizedFeedbackCopy() {
        var copiedText: String? = null
        showDeviceScreen(
            hardware = HardwareSupplyInfo(
                ramVendor = "NewChip Labs",
                ramType = "LPDDR5X",
                storageVendor = "Samsung",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            ),
            onCopyHardwareFeedback = { copiedText = it }
        )

        rule.onNodeWithText("复制识别信息")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle {
            assertTrue(copiedText?.contains("RAM 厂商：NewChip Labs") == true)
            assertTrue(copiedText?.contains("原始系统报告") == true)
        }
    }

    @Test fun completeSupplierBadgeHidesImportAction() {
        showDeviceScreen(
            HardwareSupplyInfo(
                ramVendor = "三星 (Samsung)",
                storageVendor = "SK hynix",
                source = HardwareSupplySource.ANDROID_BUGREPORT
            )
        )

        rule.onNodeWithText("导入系统报告读取厂商").assertDoesNotExist()
        rule.onNodeWithText("复制识别信息").assertDoesNotExist()
    }

    private fun showDeviceScreen(
        hardware: HardwareSupplyInfo?,
        onImportHardwareReport: () -> Unit = {},
        onCopyHardwareFeedback: (String) -> Unit = {},
        isHardwareReportBusy: Boolean = false
    ) {
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
                            access = DeviceProfileAccess.STANDARD,
                            hardwareSupplyInfo = hardware
                        ),
                        importedHardwareSupplyInfo = null,
                        isDeviceProfileReading = false,
                        onReadDeviceProfile = {},
                        onImportHardwareReport = onImportHardwareReport,
                        isHardwareReportBusy = isHardwareReportBusy,
                        onCopyHardwareFeedback = onCopyHardwareFeedback,
                        onOpenUsageAccessSettings = {},
                        onRefresh = {}
                    )
                }
            }
        }
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
