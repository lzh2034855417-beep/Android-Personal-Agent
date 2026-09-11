# System Power Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Scene CSV report with a read-only, Root-backed system power diagnostic that translates machine data into evidence-based Chinese explanations and manual Scene recommendations.

**Architecture:** A constrained Root collector captures a small allowlisted set of power-related sources. Pure Kotlin parsers normalize those sources into a `PowerDiagnosticSnapshot`; a deterministic finding engine and report builder produce local cards and a bounded summary that is sent only when the user explicitly selects it. Compose and `MainSessionViewModel` orchestrate collection and selection without retaining raw command output.

**Tech Stack:** Kotlin, Android API 26+, Jetpack Compose Material 3, ViewModel, `ProcessBuilder`, JUnit 4, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-11-system-power-diagnostics-design.md`

## Global Constraints

- Root commands are code-defined and read-only; never accept model or user command text.
- Never execute `batterystats --reset`, `force-stop`, freeze, uninstall, whitelist mutation, or writes to system nodes.
- Do not collect or upload IMEI, serial, Android ID, account data, message content, notifications, precise location, or full logcat.
- Raw output is bounded and released after parsing; only an explicitly selected compact summary may reach the configured model provider.
- Missing data is `null` or an explicit source status, never invented zero.
- First release gives advice only and never controls Scene.
- Keep the single `app` module and add no external dependencies.

---

### Task 1: Diagnostic domain model and bounded report builder

**Files:**
- Create: `app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt`
- Create: `app/src/main/java/com/aegis/apa/agent/PowerDiagnosticReport.kt`
- Test: `app/src/test/java/com/aegis/apa/PowerDiagnosticReportTest.kt`

**Interfaces:**
- Produces: `DiagnosticSourceStatus`, `DiagnosticSourceResult`, `AppPowerEvidence`, `SystemPowerEvidence`, `AdviceLevel`, `DiagnosticConfidence`, `PowerFinding`, `PowerDiagnosticSnapshot`.
- Produces: `PowerDiagnosticReportBuilder.build(snapshot: PowerDiagnosticSnapshot): String`.
- Produces: `PowerDiagnosticReportBuilder.MAX_REPORT_CHARS` and deterministic truncation.

- [ ] **Step 1: Write failing model/report tests**

```kotlin
@Test fun reportSeparatesFactsFromAdviceAndNamesMissingSources() {
    val report = PowerDiagnosticReportBuilder.build(sampleSnapshot())
    assertTrue(report.contains("【系统耗电诊断】"))
    assertTrue(report.contains("证据："))
    assertTrue(report.contains("置信度：中"))
    assertTrue(report.contains("建议级别：限制"))
    assertTrue(report.contains("未获取：thermalservice（不支持）"))
}

@Test fun reportIsBoundedAndDoesNotContainRawOutput() {
    val report = PowerDiagnosticReportBuilder.build(sampleSnapshot(title = "x".repeat(50_000)))
    assertTrue(report.length <= PowerDiagnosticReportBuilder.MAX_REPORT_CHARS)
    assertFalse(report.contains("RAW_COMMAND_OUTPUT"))
}
```

- [ ] **Step 2: Run the test and verify missing types fail compilation**

Run: `gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests com.aegis.apa.PowerDiagnosticReportTest`

- [ ] **Step 3: Implement immutable pure Kotlin models and report builder**

```kotlin
enum class DiagnosticSourceStatus { AVAILABLE, UNSUPPORTED, PERMISSION_DENIED, TIMED_OUT, TRUNCATED, PARSE_FAILED }
enum class AdviceLevel { OBSERVE, RESTRICT, FREEZE_CANDIDATE }
enum class DiagnosticConfidence { LOW, MEDIUM, HIGH }

data class PowerFinding(
    val id: String,
    val title: String,
    val packageNames: List<String>,
    val evidence: List<String>,
    val explanation: String,
    val confidence: DiagnosticConfidence,
    val adviceLevel: AdviceLevel,
    val sceneAdvice: String,
    val caveat: String
)
```

Use `Instant` for sampling time, milliseconds for durations, nullable numeric facts, and stable Chinese labels only in the report layer. Limit the compact report to 16 KiB and end truncated output with an explicit marker.

- [ ] **Step 4: Run focused and full unit tests**

Run: `gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt app/src/main/java/com/aegis/apa/agent/PowerDiagnosticReport.kt app/src/test/java/com/aegis/apa/PowerDiagnosticReportTest.kt
git commit -m "feat: add system power diagnostic model"
```

### Task 2: Pure parsers and deterministic finding engine

**Files:**
- Create: `app/src/main/java/com/aegis/apa/tool/PowerDiagnosticParser.kt`
- Create: `app/src/main/java/com/aegis/apa/agent/PowerDiagnosticFindingEngine.kt`
- Create: `app/src/test/java/com/aegis/apa/PowerDiagnosticParserTest.kt`
- Create: `app/src/test/java/com/aegis/apa/PowerDiagnosticFindingEngineTest.kt`
- Create: `app/src/test/resources/power-diagnostics/xiaomi-sample.txt`
- Create: `app/src/test/resources/power-diagnostics/partial-sample.txt`

**Interfaces:**
- Consumes: domain types from Task 1.
- Produces: `PowerDiagnosticParser.parse(sections: List<RawDiagnosticSection>, sampledAt: Instant): PowerDiagnosticSnapshot`.
- Produces: `PowerDiagnosticFindingEngine.find(snapshot: PowerDiagnosticSnapshot): List<PowerFinding>`.

- [ ] **Step 1: Add failing parser tests for source boundaries**

```kotlin
@Test fun mapsSharedUidToAllPackagesAndPreservesSourceStatus() {
    val snapshot = PowerDiagnosticParser.parse(sampleSections(), FIXED_TIME)
    assertEquals(listOf("com.example.one", "com.example.two"), snapshot.apps.single().packageNames)
    assertEquals(DiagnosticSourceStatus.TRUNCATED, snapshot.sources.getValue("alarm").status)
}

@Test fun malformedNumbersStayUnknown() {
    val snapshot = PowerDiagnosticParser.parse(malformedSections(), FIXED_TIME)
    assertNull(snapshot.apps.single().wakeLockDurationMillis)
}
```

- [ ] **Step 2: Add failing finding tests**

```kotlin
@Test fun corroboratedLongWakeLockProducesRestrictAdvice() {
    val finding = PowerDiagnosticFindingEngine.find(corroboratedSnapshot()).single()
    assertEquals(AdviceLevel.RESTRICT, finding.adviceLevel)
    assertEquals(DiagnosticConfidence.HIGH, finding.confidence)
    assertTrue(finding.evidence.all { it.contains(Regex("\\d")) })
}

@Test fun protectedPackagesNeverBecomeFreezeCandidates() {
    val findings = PowerDiagnosticFindingEngine.find(snapshotFor("com.android.phone"))
    assertTrue(findings.none { it.adviceLevel == AdviceLevel.FREEZE_CANDIDATE })
}
```

- [ ] **Step 3: Implement the section envelope and conservative parsers**

```kotlin
data class RawDiagnosticSection(
    val source: String,
    val status: DiagnosticSourceStatus,
    val output: String,
    val truncated: Boolean = false
)
```

Parse only anchored, documented patterns from package/UID maps, batterystats, power, alarm, jobscheduler, deviceidle, thermalservice, and wakeup sources. A format mismatch marks the source `PARSE_FAILED`; do not scrape arbitrary numbers.

- [ ] **Step 4: Implement named thresholds and confidence reduction**

Use named internal constants for long wake-lock duration, high wakeup count, repeated jobs, and thermal severity. Require a valid window for rates; require two independent evidence categories for `HIGH`; downgrade shared UID, truncated or single-source findings. Protect system UID, launcher, phone/SMS, payment indicators and Root managers from freeze-candidate output.

- [ ] **Step 5: Run focused and full unit tests, then commit**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests "com.aegis.apa.PowerDiagnostic*Test"
git add app/src/main/java/com/aegis/apa/tool/PowerDiagnosticParser.kt app/src/main/java/com/aegis/apa/agent/PowerDiagnosticFindingEngine.kt app/src/test app/src/test/resources/power-diagnostics
git commit -m "feat: parse power evidence into findings"
```

### Task 3: Constrained Root collector with partial-success semantics

**Files:**
- Create: `app/src/main/java/com/aegis/apa/tool/RootCommandRunner.kt`
- Create: `app/src/main/java/com/aegis/apa/tool/SystemPowerDiagnosticsCollector.kt`
- Modify: `app/src/main/java/com/aegis/apa/tool/RootBatteryTool.kt`
- Test: `app/src/test/java/com/aegis/apa/RootCommandRunnerTest.kt`
- Test: `app/src/test/java/com/aegis/apa/SystemPowerDiagnosticsCollectorTest.kt`

**Interfaces:**
- Produces: `RootCommandRunner.run(command: AllowedRootCommand): RootCommandResult`.
- Produces: `AllowedRootCommand` sealed entries only; callers cannot provide arbitrary shell text.
- Produces: `SystemPowerDiagnosticsCollector.collect(): PowerDiagnosticSnapshot`.
- Consumes: `PowerDiagnosticParser` and `PowerDiagnosticFindingEngine` from Task 2.

- [ ] **Step 1: Add failing tests for command allowlisting, timeout and truncation**

```kotlin
@Test fun runnerHasNoArbitraryStringEntryPoint() {
    assertEquals(setOf("BATTERYSTATS", "POWER", "ALARM", "JOBS", "DEVICE_IDLE", "THERMAL", "WAKEUP", "PACKAGES"),
        AllowedRootCommand.entries.map { it.name }.toSet())
}

@Test fun collectorKeepsSuccessfulSectionsWhenOneTimesOut() {
    val snapshot = collectorWithOneTimeout().collect()
    assertEquals(DiagnosticSourceStatus.AVAILABLE, snapshot.sources.getValue("power").status)
    assertEquals(DiagnosticSourceStatus.TIMED_OUT, snapshot.sources.getValue("alarm").status)
}
```

- [ ] **Step 2: Implement bounded process execution**

Each enum entry owns a fixed command, timeout and maximum bytes. Start `ProcessBuilder("su", "-c", command.shell)`, consume bounded merged output without deadlock, forcibly destroy on timeout, classify nonzero exit without preserving stderr, and return no raw exception text.

- [ ] **Step 3: Implement sequential collection and parsing**

Collect one command at a time on `Dispatchers.IO`, publish optional progress callbacks, release raw section strings after parsing, and return a partial snapshot. Do not add reset, bugreport, logcat, mutation or user-provided arguments.

- [ ] **Step 4: Migrate `RootBatteryTool` to the shared runner without behavior changes**

Keep its existing eight-second timeout, user-facing error categories and parser contract. Add a regression test for denied Root and successful fixture output.

- [ ] **Step 5: Run tests and commit**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
git add app/src/main/java/com/aegis/apa/tool app/src/test/java/com/aegis/apa
git commit -m "feat: collect bounded root power diagnostics"
```

### Task 4: Session state and Agent attachment boundary

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/MainSessionViewModel.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentPromptPolicy.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/CloudHistoryPolicy.kt` only if attachment labels need filtering changes.
- Test: `app/src/test/java/com/aegis/apa/MainSessionViewModelTest.kt`
- Create: `app/src/test/java/com/aegis/apa/PowerDiagnosticPromptTest.kt`

**Interfaces:**
- `MainSessionViewModel.powerDiagnostic: MutableState<PowerDiagnosticSnapshot?>`.
- `MainSessionViewModel.powerDiagnosticState: MutableState<PowerDiagnosticUiState>`.
- `AgentPageState.includePowerDiagnosticReport` replaces `includeSceneReport`.
- `CloudLlmProvider.analyze(..., powerDiagnosticReport: String?, ...)` includes the section only when non-null.

- [ ] **Step 1: Add failing state and prompt-boundary tests**

```kotlin
@Test fun diagnosticSelectionSurvivesPageStateButRawOutputIsNotStored() {
    val vm = MainSessionViewModel()
    vm.agent.includePowerDiagnosticReport.value = true
    vm.powerDiagnostic.value = sampleSnapshot()
    assertTrue(vm.agent.includePowerDiagnosticReport.value)
}

@Test fun unselectedDiagnosticIsNotIncludedInCloudPrompt() {
    val prompt = buildCloudPrompt(powerDiagnosticReport = null)
    assertTrue(prompt.contains("本次未附带"))
    assertFalse(prompt.contains("系统耗电诊断：微信"))
}
```

- [ ] **Step 2: Replace Scene session state with typed diagnostic state**

Use idle/collecting/ready/error/interrupted states. Activity destruction cancels active collection and records a retry message; it never restarts Root work automatically.

- [ ] **Step 3: Add explicit compact-report parameter to cloud prompt construction**

Extract prompt construction to a pure internal function so it can be tested without network. Add `【系统耗电诊断】` and preserve existing history/provider boundaries. Update policy text to require evidence, confidence, manual-only Scene advice and protected-package caution.

- [ ] **Step 4: Run tests and commit**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
git add app/src/main/java/com/aegis/apa/MainSessionViewModel.kt app/src/main/java/com/aegis/apa/agent app/src/test/java/com/aegis/apa
git commit -m "feat: attach selected power diagnostics to agent"
```

### Task 5: Compose collection and review experience

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Create: `app/src/main/java/com/aegis/apa/PowerDiagnosticPanel.kt`
- Modify: `app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt`

**Interfaces:**
- `PowerDiagnosticPanel(state, snapshot, selected, onCollect, onToggleSelected, onRemove, onCopyPackage)` renders the report-selection experience.
- `AgentChatScreen` accepts typed diagnostic state/callbacks instead of Scene import strings and document-picker callbacks.

- [ ] **Step 1: Replace the Scene UI test with failing diagnostic tests**

```kotlin
@Test fun systemPowerDiagnosticReplacesSceneImport() {
    openAgentReportPicker()
    rule.onNodeWithText("系统耗电诊断").assertExists()
    rule.onNodeWithText("导入 Scene CSV").assertDoesNotExist()
}

@Test fun diagnosticDefaultsToNotSelected() {
    openAgentReportPicker()
    rule.onNodeWithText("随问题发送（默认关闭）").assertExists()
}
```

- [ ] **Step 2: Remove the document picker and wire Root collection**

Launch collection only from the explicit button on `Dispatchers.IO`; reflect progress in ViewModel; build findings and compact report after success; prevent duplicate clicks while collecting.

- [ ] **Step 3: Implement focused diagnostic UI**

Show sampled time, source completeness, finding cards, evidence expansion, confidence, advice level, caveat, manual Scene direction and package-copy action. Show unsupported sources as a compact list. Do not show full raw command output or exact unverified Scene menu labels.

- [ ] **Step 4: Wire local and online sends**

Local analysis appends the compact diagnostic when selected. Online analysis passes it explicitly to `CloudLlmProvider`; attachment label becomes `系统耗电诊断`. Unselected reports never enter message content or provider history.

- [ ] **Step 5: Run unit and instrumentation compilation, then commit**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
git add app/src/main/java/com/aegis/apa app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt
git commit -m "feat: add power diagnostic report experience"
```

### Task 6: Remove obsolete Scene flow and update documentation

**Files:**
- Delete: `app/src/main/java/com/aegis/apa/tool/SceneCsvInput.kt`
- Delete: `app/src/main/java/com/aegis/apa/tool/SceneReport.kt`
- Delete: `app/src/test/java/com/aegis/apa/SceneCsvInputTest.kt`
- Delete: `app/src/test/java/com/aegis/apa/SceneCsvParserTest.kt`
- Delete: `app/src/test/java/com/aegis/apa/SceneReportBuilderTest.kt`
- Delete: `docs/examples/scene-example.csv`
- Modify: `README.md`
- Modify: `README_EN.md`
- Modify: `CHANGELOG.md`
- Modify: `ROADMAP.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/TESTING.md`
- Modify: `docs/TECH_DEBT.md`

**Interfaces:**
- Removes all Scene CSV production/session/prompt paths.
- Documents system power diagnostic permissions, evidence limits, privacy, manual Scene advice and device validation.

- [ ] **Step 1: Search and remove all reachable Scene flow references**

Run: `rg -n "SceneCsv|sceneReport|includeScene|导入 Scene|Scene 一天续航" app/src README.md README_EN.md docs CHANGELOG.md ROADMAP.md`

Delete the obsolete parser/input/tests/example after Task 5 no longer imports them. Keep historical specs/plans unchanged as historical records; add a note in the new docs that the feature was superseded.

- [ ] **Step 2: Update user and architecture documentation**

Document the explicit collection action, Root requirement, source list, partial results, local-first behavior, online opt-in, no automation, protected apps and diagnostic limitations. Do not claim universal Xiaomi/HyperOS compatibility.

- [ ] **Step 3: Add a release note entry and run doc consistency searches**

Run:

```bash
rg -n "Scene.*开发中|导入 Scene CSV|Scene 仅本地" app/src README.md README_EN.md docs CHANGELOG.md ROADMAP.md
rg -n "自动.*冻结|已经.*限频|已执行.*强停" app/src README.md README_EN.md docs
```

Expected: no active-product references to the removed flow and no claims of automatic action.

- [ ] **Step 4: Run tests and commit**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
git add -A
git commit -m "docs: replace Scene report with power diagnostics"
```

### Task 7: Full verification and rooted Xiaomi device acceptance

**Files:**
- Modify: `docs/TESTING.md` with commands, device/build identity, pass/fail evidence and unsupported sources.
- Modify only files required by verified failures.

**Interfaces:**
- Produces a reviewable branch with build and device evidence; does not merge or push.

- [ ] **Step 1: Run static and build verification**

```bash
gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

- [ ] **Step 2: Inspect the APK and command allowlist**

Run source searches proving there is no reset, force-stop, freeze, uninstall, mutable whitelist or writable sysfs command. Inspect Debug manifest permissions and exported components; ensure no storage permission was added.

- [ ] **Step 3: Install Debug APK and run device tests**

Use the connected rooted Xiaomi device. Verify explicit Root prompt, collection progress, partial-source behavior, readable cards, package copy, default-off attachment, local send, online prompt boundary without exposing credentials, page navigation and Activity recreation. Do not alter Scene or system settings.

- [ ] **Step 4: Record evidence and rerun final verification**

Update `docs/TESTING.md` with exact tested build/commit, device model code, Android API, available/missing diagnostic sources and test totals. Rerun unit tests and Debug assembly after documentation changes.

- [ ] **Step 5: Commit verification evidence**

```bash
git add docs/TESTING.md
git commit -m "test: verify system power diagnostics"
```

