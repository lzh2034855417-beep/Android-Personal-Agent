# APA Chinese/English Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent Chinese/English switch that localizes APA's complete user experience, generated reports, share cards, and Agent response language without translating raw device evidence.

**Architecture:** Android string resources localize static UI while a small `AppLanguage`/`AppLanguageStore` boundary applies and persists the locale before `MainActivity` is created. Pure Kotlin report and Agent builders accept `AppLanguage` and use domain copy providers, keeping analysis code independent of Android `Context`.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, Android resources, SharedPreferences, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-28-english-localization-design.md`

## Global Constraints

- Supported languages are exactly Simplified Chinese (`zh-CN`) and English (`en`); Simplified Chinese is the fallback for no or invalid saved preference.
- Minimum Android version remains API 26; do not add AppCompat or change `MainActivity` away from `ComponentActivity`.
- English lives in `res/values/strings.xml`; Chinese lives in `res/values-zh-rCN/strings.xml`.
- Raw app labels, package names, device/model/chip identifiers, supplier names, scheduler names, commands, paths, and imported Bugreport evidence remain unchanged.
- Existing conversations remain in their original language; only controls and newly generated content switch.
- Parser tokens and source-data aliases are not user-facing copy and must not be translated.
- Work in the existing dirty worktree without resetting unrelated changes. Commit steps apply only if execution occurs in a clean isolated worktree; otherwise record the verification checkpoint without committing.

## Review Focus

- Invalid stored language must load Chinese, covered by Task 1's `invalidStoredLanguageFallsBackToChinese` test.
- Switching language during a saved conversation must preserve messages, covered by Task 2's recreation test.
- Raw package/model/vendor values must survive English generation unchanged, covered by Task 4's report tests.
- English Agent mode must not answer in Chinese because a Chinese safety prompt dominates, covered by Task 5's prompt-language test.
- Wide English copy must remain usable on a 280 dp layout, covered by Task 6's narrow-layout Compose test.

---

### Task 1: Language State and Localized Activity Context

**Files:**
- Create: `app/src/main/java/com/aegis/apa/localization/AppLanguage.kt`
- Create: `app/src/main/java/com/aegis/apa/localization/AppLanguageStore.kt`
- Create: `app/src/test/java/com/aegis/apa/localization/AppLanguageTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Produces: `enum class AppLanguage(val languageTag: String)` with `ZH_CN`, `EN`, and `fromStoredValue(String?): AppLanguage`.
- Produces: `AppLanguageStore.load(Context): AppLanguage`, `save(Context, AppLanguage)`, and `Context.withAppLanguage(AppLanguage): Context`.
- Produces: `LocalAppLanguage`, a Compose `staticCompositionLocalOf<AppLanguage>` supplied once around APA content.

- [ ] **Step 1: Write the failing language parsing tests**

Add tests named `storedEnglishLoadsEnglish`, `storedChineseLoadsChinese`, and `invalidStoredLanguageFallsBackToChinese`, using literal stored values `en`, `zh-CN`, `null`, and `garbage`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests com.aegis.apa.localization.AppLanguageTest`
Expected: FAIL because `AppLanguage` does not exist.

- [ ] **Step 3: Implement the language boundary**

Implement the exact interfaces above. Override `MainActivity.attachBaseContext` to load the preference and call `withAppLanguage` before `super.attachBaseContext`. Supply the loaded language through `LocalAppLanguage` inside `setContent`.

- [ ] **Step 4: Add baseline English and Chinese resources**

Keep `app_name` and add only the language-control and primary navigation keys needed by Task 2. English is the default resource; Chinese contains an explicit translation for every new key.

- [ ] **Step 5: Run focused tests and compile both source sets**

Run: `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
Expected: PASS.

- [ ] **Step 6: Checkpoint**

If isolated: commit `feat: add persistent app language state`.

### Task 2: Functional Settings Switch and Persistence

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt` (`SettingsScreen` and caller)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt`

**Interfaces:**
- Consumes: Task 1's `AppLanguage`, `AppLanguageStore`, and `LocalAppLanguage`.
- Produces: `SettingsScreen(currentLanguage: AppLanguage, onLanguageSelected: (AppLanguage) -> Unit, ...)` with two semantically selectable controls.

- [ ] **Step 1: Replace the placeholder test with failing switch behavior**

Update `settingsShowsEnglishLanguageOption` to save the original preference, force Chinese in setup, assert Chinese is selected, tap `English`, wait for activity recreation, assert `Device`, `Agent`, `Capabilities`, `Apps`, and `Settings` English navigation, recreate again, and assert English persists; restore the original preference in `finally`. Add a second isolated test that switches back and sees `设备` and `设置` while preserving a pre-existing chat message.

- [ ] **Step 2: Run the targeted connected test and verify RED**

Run the two `StabilityUiTest` language methods with `connectedDebugAndroidTest`.
Expected: FAIL because the English button is disabled/no-op. If the known Android 17 runner remains at 0/N, record the infrastructure block and use AndroidTest compilation plus manual code-path verification; do not report the UI test as passed.

- [ ] **Step 3: Implement selection, persistence, and recreation**

Use Material 3 selectable semantics. Save only when the new language differs, then call the activity-level callback that recreates `MainActivity`. Do not clear `MainSessionViewModel` or saved conversation state.

- [ ] **Step 4: Run unit tests and AndroidTest compilation**

Run: `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
Expected: PASS.

- [ ] **Step 5: Checkpoint**

If isolated: commit `feat: enable Chinese and English switching`.

### Task 3: Localize the Five Primary Screens

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/main/java/com/aegis/apa/QuickReportPanel.kt`
- Modify: `app/src/main/java/com/aegis/apa/PowerDiagnosticPanel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: relevant tests under `app/src/androidTest/java/com/aegis/apa/`

**Interfaces:**
- Consumes: localized Activity context and `LocalAppLanguage`.
- Produces: resource-backed user-visible UI for Device, Agent, Capability, Apps, Settings, dialogs, import help, states, errors, and navigation.

- [ ] **Step 1: Add failing English screen-smoke assertions**

In Compose/UI tests, set English and assert representative unique headings and actions from each primary page. Assert the old Chinese headings do not exist on those screens.

- [ ] **Step 2: Run AndroidTest compilation and the target runner**

Expected before implementation: target assertions FAIL or the documented device runner block occurs.

- [ ] **Step 3: Move static Compose copy to resources**

Replace user-facing literals with `stringResource`; use numbered placeholders for values. Keep parser tokens, raw evidence, model/vendor/app labels, package names, and developer-only identifiers as literals.

- [ ] **Step 4: Verify resource completeness and source compilation**

Run: `gradlew.bat :app:lintDebug :app:compileDebugAndroidTestKotlin`
Expected: PASS with no missing/invalid resource formatting.

- [ ] **Step 5: Checkpoint**

If isolated: commit `feat: localize primary app screens`.

### Task 4: Localize Reports, Hardware Grades, and Share Cards

**Files:**
- Create: `app/src/main/java/com/aegis/apa/localization/ReportCopy.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/QuickReport.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/Level0ReportBuilder.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/PowerDiagnosticReport.kt`
- Modify: `app/src/main/java/com/aegis/apa/model/BatteryObservation.kt`
- Modify: `app/src/main/java/com/aegis/apa/HardwareSupplySummary.kt`
- Modify: `app/src/main/java/com/aegis/apa/tool/HardwareExperienceEvaluator.kt`
- Modify: `app/src/main/java/com/aegis/apa/QuickReportPanel.kt`
- Modify: corresponding unit tests in `app/src/test/java/com/aegis/apa/`

**Interfaces:**
- Consumes: `AppLanguage`.
- Produces: language parameters on report/summary/evaluation builders; `buildReportCardText(..., language: AppLanguage)`.

- [ ] **Step 1: Write failing bilingual report tests**

For one battery observation, one power diagnostic, one hardware grade, and one share card, assert English mode contains English headings and no Chinese labels; Chinese mode preserves current Chinese headings. Use literal `com.tencent.mm`, `Xiaomi 17 Pro Max`, and `SK hynix` evidence and assert they are byte-for-byte unchanged in both outputs.

- [ ] **Step 2: Run focused unit tests and verify RED**

Run the relevant test classes with `:app:testDebugUnitTest --tests ...`.
Expected: FAIL because builders have no language input and emit Chinese only.

- [ ] **Step 3: Implement domain copy providers and pass language explicitly**

Keep analyzers free of Android `Context`. Localize labels, explanations, confidence/advice names, limits, and next steps. Do not translate raw evidence fields.

- [ ] **Step 4: Run the complete unit suite**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Checkpoint**

If isolated: commit `feat: localize reports and share cards`.

### Task 5: Localize Agent Prompts and Local Responses

**Files:**
- Create: `app/src/main/java/com/aegis/apa/localization/AgentCopy.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentPromptPolicy.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentAttachmentPolicy.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/LocalDeviceAnalyzer.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/PowerAnalysisPreflight.kt`
- Modify: Agent-related tests under `app/src/test/java/com/aegis/apa/`

**Interfaces:**
- Consumes: `AppLanguage` and Task 4's localized reports.
- Produces: `AgentPromptPolicy.systemPrompt(language: AppLanguage)`, `buildCloudAnalysisPrompt(..., language: AppLanguage)`, and localized local/preflight responses.

- [ ] **Step 1: Write failing prompt and local-response tests**

Assert English prompts explicitly require English, use English attachment headings/safety rules, and retain raw package names. Assert Chinese preserves existing rules. Assert local no-key and preflight replies follow the selected language.

- [ ] **Step 2: Run focused Agent tests and verify RED**

Expected: FAIL because current prompts and fallbacks are Chinese-only.

- [ ] **Step 3: Implement bilingual Agent copy**

Pass the active language through every production prompt call. Do not translate the user's message or stored history. Keep equivalent safety constraints in both languages.

- [ ] **Step 4: Run the complete unit suite**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Checkpoint**

If isolated: commit `feat: localize Agent analysis`.

### Task 6: Audit Mixed Copy, Narrow Layouts, and Release Artifacts

**Files:**
- Modify: any production file containing an unclassified user-facing Chinese literal found by the audit
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: narrow-layout tests under `app/src/androidTest/java/com/aegis/apa/`

**Interfaces:**
- Consumes: all prior localization boundaries.
- Produces: a classified Chinese-literal audit and release-ready APKs.

- [ ] **Step 1: Add the narrow English layout regression**

Render the primary navigation/settings/report controls at 280 dp width and assert required labels are displayed, selectable, and do not exceed intended single-line constraints where specified.

- [ ] **Step 2: Audit Chinese literals in production Kotlin**

Run an `rg` scan over `app/src/main/java`. Move every unclassified user-facing literal to resources/copy providers. Record remaining matches by the four allowed classes from the spec; parser tokens must stay untouched.

- [ ] **Step 3: Run final verification**

Run: `git diff --check` followed by `gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease`.
Expected: `BUILD SUCCESSFUL`, zero test failures, zero lint errors, and both APKs present.

- [ ] **Step 4: Attempt connected language-flow tests**

Run only the language-related instrumentation methods. Report actual results. If Android 17 remains at 0/N, terminate the hung runner, preserve compiled tests, and state that execution was blocked rather than passed.

- [ ] **Step 5: Final review and artifact handoff**

Request whole-change code review, address Critical/Important findings, rebuild if any production or test file changes, and report the Debug APK path, build time, size, and SHA-256.

- [ ] **Step 6: Checkpoint**

If isolated: commit `feat: complete English localization`.
