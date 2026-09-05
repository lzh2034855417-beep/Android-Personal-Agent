# Level 0+ Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give ordinary Android users a richer, privacy-bounded Level 0 report that APA Agent can use for concrete battery, display, performance, storage, and optional usage-habit analysis.

**Architecture:** Add focused read-only tools for display and usage statistics, extend the existing battery snapshot, and pass all results through `DeviceSnapshot` into one pure Level 0 report formatter. The device page shows short summaries and one explicit Usage Access button; cloud upload behavior remains unchanged because reports are included only when the user actively sends a Level 0 question.

**Tech Stack:** Kotlin, Android API 26+, Jetpack Compose Material 3, Android `BatteryManager`, `Display`, `UsageStatsManager`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-05-level0-plus-design.md`

## Global Constraints

- Support Android 8.0 / API 26 and above.
- Read only; do not add Root, Shizuku, network, location, contacts, photo, SMS, call-log, or account access.
- Usage information is read only after the user enables Android Usage Access; a missing grant never blocks base reporting.
- Every unavailable vendor/API field prints `设备未提供`; never invent a default value.
- User data enters a cloud request only through the existing user-initiated Level 0 send flow.

---

### Task 1: Enrich the standard battery snapshot

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/tool/BatteryTool.kt`
- Create: `app/src/test/java/com/aegis/apa/BatteryReportTextTest.kt`

**Interfaces:**
- Produces: `BatteryInfo` with nullable `temperatureCelsius`, `voltageMilliVolt`, `health`, `plugged`, `technology`, and `isPresent` fields.
- Consumed by: `Level0ReportBuilder` and `DeviceReportScreen`.

- [ ] **Step 1: Write the failing formatter test**

```kotlin
@Test
fun printsStandardBatteryFieldsAndKeepsMissingFieldsExplicit() {
    val text = BatteryReportText.format(
        BatteryInfo(
            level = 69, status = "正在充电", temperatureCelsius = 32.1,
            voltageMilliVolt = 4480, health = "良好", plugged = "USB",
            technology = null, isPresent = true
        )
    )
    assertTrue(text.contains("电池温度：32.1°C"))
    assertTrue(text.contains("充电方式：USB"))
    assertTrue(text.contains("电池技术：设备未提供"))
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.BatteryReportTextTest`

Expected: compilation fails because `BatteryReportText` does not exist.

- [ ] **Step 3: Implement the smallest formatter and source mapping**

```kotlin
object BatteryReportText {
    fun format(info: BatteryInfo): String = buildString {
        appendLine("电池温度：${info.temperatureCelsius?.let { "$it°C" } ?: "设备未提供"}")
        appendLine("电池电压：${info.voltageMilliVolt?.let { "$it mV" } ?: "设备未提供"}")
        appendLine("电池健康：${info.health ?: "设备未提供"}")
        appendLine("充电方式：${info.plugged ?: "设备未提供"}")
    }
}
```

Map `ACTION_BATTERY_CHANGED` extras `EXTRA_TEMPERATURE`, `EXTRA_VOLTAGE`, `EXTRA_HEALTH`, `EXTRA_PLUGGED`, `EXTRA_TECHNOLOGY`, and `EXTRA_PRESENT`; convert temperature tenths of °C to `Double`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command in Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aegis/apa/tool/BatteryTool.kt app/src/test/java/com/aegis/apa/BatteryReportTextTest.kt
git commit -m "Add Level 0 battery details"
```

### Task 2: Read and format normal display information

**Files:**
- Create: `app/src/main/java/com/aegis/apa/tool/DisplayInfoTool.kt`
- Create: `app/src/test/java/com/aegis/apa/DisplayReportTextTest.kt`

**Interfaces:**
- Produces: `DisplayInfo(widthPixels: Int?, heightPixels: Int?, densityDpi: Int?, currentRefreshRate: Float?, maxRefreshRate: Float?)`.
- Consumed by: `DeviceSnapshot`, device page, and `Level0ReportBuilder`.

- [ ] **Step 1: Write the failing formatter test**

```kotlin
@Test
fun formatsScreenResolutionAndMaximumRefreshRate() {
    val text = DisplayReportText.format(
        DisplayInfo(1220, 2712, 480, 120f, 120f)
    )
    assertTrue(text.contains("分辨率：1220 × 2712"))
    assertTrue(text.contains("当前刷新率：120 Hz"))
    assertTrue(text.contains("最高支持刷新率：120 Hz"))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.DisplayReportTextTest`

Expected: compilation fails because the display report types do not exist.

- [ ] **Step 3: Implement the display reader and formatter**

Use the current `Display` to read current width, height, density and refresh rate. Use `supportedModes.maxOfOrNull { it.refreshRate }` for the maximum supported rate. Catch unavailable values as null; do not use panel supplier, HDR support, or window dimensions as a fabricated physical specification.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command in Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aegis/apa/tool/DisplayInfoTool.kt app/src/test/java/com/aegis/apa/DisplayReportTextTest.kt
git commit -m "Add Level 0 display details"
```

### Task 3: Add user-authorized daily usage summaries

**Files:**
- Create: `app/src/main/java/com/aegis/apa/tool/UsageStatsTool.kt`
- Create: `app/src/test/java/com/aegis/apa/UsageSummaryTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`

**Interfaces:**
- Produces: `UsageSummary(accessGranted: Boolean, foregroundTimeMillis: Long?, topApps: List<UsageApp>)`.
- Consumed by: `DeviceSnapshot`, Level 0 report, and device page.

- [ ] **Step 1: Write the failing aggregation test**

```kotlin
@Test
fun selectsTheMostUsedAppsAndSumsForegroundTime() {
    val summary = UsageSummaryBuilder.from(
        accessGranted = true,
        appDurationsMillis = mapOf("pkg.a" to 3_600_000L, "pkg.b" to 1_800_000L),
        labels = mapOf("pkg.a" to "App A", "pkg.b" to "App B")
    )
    assertEquals(5_400_000L, summary.foregroundTimeMillis)
    assertEquals("App A", summary.topApps.first().label)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.UsageSummaryTest`

Expected: compilation fails because `UsageSummaryBuilder` does not exist.

- [ ] **Step 3: Implement permission detection and today-only aggregation**

Detect usage access with `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, uid, packageName)`. If denied, return `UsageSummary(false, null, emptyList())` and do not query usage events. If granted, query from local midnight to current time with `UsageStatsManager.queryAndAggregateUsageStats`, retain only positive foreground durations, resolve labels through `PackageManager`, sort descending, and keep five apps. Sum retained foreground durations as the report's “应用前台使用时长合计”; label it explicitly so it is not misrepresented as Android's exact screen-on time.

- [ ] **Step 4: Connect the existing system settings entry**

Pass `onOpenUsageAccessSettings` into `DeviceReportScreen`; show a plain-language button and status text:

```kotlin
Text("使用习惯：未授权，不影响基础报告")
Button(onClick = onOpenUsageAccessSettings) { Text("授权使用情况") }
```

Refresh `DeviceSnapshot` when the user returns to the page or presses refresh.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the command in Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/aegis/apa/tool/UsageStatsTool.kt app/src/main/java/com/aegis/apa/MainActivity.kt app/src/test/java/com/aegis/apa/UsageSummaryTest.kt
git commit -m "Add optional Level 0 usage summary"
```

### Task 4: Assemble the structured L0+ report and device page summaries

**Files:**
- Create: `app/src/main/java/com/aegis/apa/agent/Level0ReportBuilder.kt`
- Create: `app/src/test/java/com/aegis/apa/Level0ReportBuilderTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentContracts.kt`

**Interfaces:**
- Consumes: `DeviceInfo`, enriched `BatteryInfo`, `DisplayInfo`, `RamInfo`, `StorageInfo`, `UsageSummary`, and `HardwareExperienceGrade`.
- Produces: `Level0ReportBuilder.build(...) : String` with six named sections.

- [ ] **Step 1: Write the failing report contract test**

```kotlin
@Test
fun separatesBaseAndOptionalDataInTheLevel0Report() {
    val report = Level0ReportBuilder.build(
        sampledAt = "12:00:00",
        deviceInfo = DeviceInfo("Example Phone", "Android 16（API 36）"),
        batteryInfo = BatteryInfo(level = 70, status = "未充电"),
        displayInfo = DisplayInfo(null, null, null, null, null),
        ramInfo = RamInfo(8L, 4L, false),
        storageInfo = StorageInfo(128L, 64L),
        usageSummary = UsageSummary(false, null, emptyList()),
        hardwareGrade = HardwareExperienceGrade("待读取", "读取芯片档案后给出等级", emptyList()),
        securityPatch = null,
        socName = null,
        supportedAbis = emptyList()
    )
    assertTrue(report.contains("【屏幕体验】"))
    assertTrue(report.contains("【电池即时状态】"))
    assertTrue(report.contains("【可选使用习惯】"))
    assertTrue(report.contains("未授权，不影响基础报告"))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.Level0ReportBuilderTest`

Expected: compilation fails because `Level0ReportBuilder` does not exist.

- [ ] **Step 3: Implement the pure report builder and wire the snapshot**

Create the six sections from the approved specification. Replace the inline `"Level 0"` block in `DeviceSnapshot.buildLevelReport` with the builder. Extend `DeviceSnapshot` and `DeviceContext` to carry display and usage values; include compact device-page cards for display and usage status. Add `Build.VERSION.SECURITY_PATCH`, `Build.SOC_MANUFACTURER`/`SOC_MODEL` when API supports them, and `Build.SUPPORTED_ABIS` to the device/system report; print `设备未提供` for unavailable values.

- [ ] **Step 4: Update AgentPromptPolicy for new Level 0 sections**

Add one concise instruction: use usage duration only as a same-day clue, distinguish it from battery-life measurement, and never infer app content or background behavior from foreground time alone.

- [ ] **Step 5: Run report tests and existing AgentPromptPolicy tests**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.Level0ReportBuilderTest --tests com.aegis.apa.AgentPromptPolicyTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/aegis/apa/MainActivity.kt app/src/main/java/com/aegis/apa/agent/Level0ReportBuilder.kt app/src/main/java/com/aegis/apa/agent/AgentContracts.kt app/src/main/java/com/aegis/apa/agent/AgentPromptPolicy.kt app/src/test/java/com/aegis/apa/Level0ReportBuilderTest.kt
git commit -m "Build detailed Level 0 plus report"
```

### Task 5: Verify and install the complete L0+ build

**Files:**
- Modify if required: `README.md`, `README_EN.md`, `CHANGELOG.md`

- [ ] **Step 1: Run the full verification**

Run: `./gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Perform connected-device checks**

With Usage Access disabled, refresh device data and send a Level 0 question; verify the report contains the six sections and states that usage is not authorized. Enable Usage Access in system settings, return to APA, refresh, and verify today’s foreground-time summary appears. Check that the device page remains usable if display, battery technology, SoC, or usage stats are unavailable.

- [ ] **Step 3: Update user-facing documentation if copy changed**

Add a Level 0+ note explaining that usage habits are optional and only sent with a user-initiated report.

- [ ] **Step 4: Commit**

```bash
git add README.md README_EN.md CHANGELOG.md
git commit -m "Document Level 0 plus privacy controls"
```
