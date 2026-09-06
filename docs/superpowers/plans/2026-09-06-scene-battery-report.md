# Scene 一天续航报告 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a Scene CSV export and attach an evidence-bounded one-day battery summary to APA Agent analysis.

**Architecture:** A pure Kotlin parser maps known CSV headers to optional telemetry fields, then a pure report builder formats only supported aggregates. `MainActivity` owns the picked URI, imports text off the main thread, renders import state, and sends the report through the existing `sceneReport` model field.

**Tech Stack:** Kotlin, JUnit 4, Android Storage Access Framework, Jetpack Compose, coroutines.

**Spec:** `docs/superpowers/specs/2026-09-06-scene-battery-report-design.md`

## Global Constraints

- Support `text/csv` and `text/plain` through `ActivityResultContracts.OpenDocument`.
- Do not add storage permissions or dependencies.
- Never infer battery health, mandatory battery replacement, background power use, or screen-on time from a Scene CSV.
- Keep release builds blocked when `keystore.properties` is absent, but allow debug/unit-test tasks without it.

---

### Task 1: Fix local worktree build isolation

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] Write the failing reproduction by running `:app:testDebugUnitTest` in a worktree without `keystore.properties`.
- [ ] Defer the release-signing presence check to release task execution while retaining it for every `Release` task.
- [ ] Run `:app:testDebugUnitTest` with `ANDROID_HOME` set; expect success.
- [ ] Run `:app:assembleRelease` without properties; expect the signing error.

### Task 2: Parse Scene CSV files

**Files:**
- Create: `app/src/main/java/com/aegis/apa/tool/SceneReport.kt`
- Create: `app/src/test/java/com/aegis/apa/SceneCsvParserTest.kt`

**Interfaces:**
- Produces: `SceneCsvParser.parse(text: String): SceneImportResult`.
- Produces: `SceneSample` with optional timestamp, battery, temperature, current, power and app name.

- [ ] Write failing tests for BOM removal, Chinese/English header aliases, quoted CSV values and unsupported files.
- [ ] Run `:app:testDebugUnitTest --tests com.aegis.apa.SceneCsvParserTest`; expect failure because parser is absent.
- [ ] Implement a quote-aware CSV row parser and header alias mapping without Android dependencies.
- [ ] Re-run the focused parser test; expect success.

### Task 3: Build evidence-bounded summaries

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/tool/SceneReport.kt`
- Create: `app/src/test/java/com/aegis/apa/SceneReportBuilderTest.kt`

**Interfaces:**
- Produces: `SceneReportBuilder.build(import: SceneImportResult): String`.

- [ ] Write failing tests for percentage-per-hour calculation, temperature peak, and missing-evidence language.
- [ ] Run the focused report-builder test; expect failure because the builder is absent.
- [ ] Implement calculations requiring two timed battery samples and render unavailable fields explicitly.
- [ ] Re-run focused tests; expect success.

### Task 4: Connect file import and Agent reports

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `README.md`
- Modify: `README_EN.md`

- [ ] Add `OpenDocument` handling, background text import, success/error state, removal action and Scene report selector state.
- [ ] Pass the generated report to the existing local/online analysis paths and show it in local analysis.
- [ ] Replace the disabled Scene button with import/remove UI and document accepted CSV columns and limitations.
- [ ] Run `:app:compileDebugKotlin` and a focused UI/manual device check.

### Task 5: Verify and commit

**Files:**
- Test: all JVM tests and lint

- [ ] Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with `JAVA_HOME` and `ANDROID_HOME`.
- [ ] Install the debug APK and verify import, summary, removal, and Agent attachment on a device.
- [ ] Inspect `git diff --check` and commit the feature in focused commits.
