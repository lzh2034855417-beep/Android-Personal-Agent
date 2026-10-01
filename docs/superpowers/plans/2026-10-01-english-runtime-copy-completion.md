# English Runtime Copy Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure English mode does not fall back to Chinese for Agent failures, battery-observation state, Bug Report import failures, or supplier feedback.

**Architecture:** Pure Kotlin copy providers accept `AppLanguage`, preserving Android-free unit testing and raw device evidence. `MainSessionViewModel` owns the active language for session-generated notices, while `MainActivity` passes the selected language at UI and error boundaries.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Android ViewModel, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-28-english-localization-design.md`

## Global Constraints

- Supported languages remain exactly Simplified Chinese and English.
- Chinese behavior remains the default and existing Chinese copy remains compatible.
- Raw app labels, package names, model/vendor identifiers, HTTP status codes, and report evidence are never translated.
- No parser tokens, collection logic, scoring, persistence format, or report evidence are changed.
- Work in the existing dirty worktree without resetting unrelated changes; do not commit files that already contain the user's uncommitted work.

## Review Focus

- Unknown exceptions must not expose exception messages or credentials in either language.
- Changing language must affect future notices without rewriting stored chat history.
- English HTTP failures must keep the numeric status for unknown codes.
- Supplier feedback must translate labels but preserve vendor/model values exactly.
- Bug Report rejection reasons must remain specific instead of collapsing to a generic error.

---

### Task 1: Agent Failure Messages

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentFailure.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/AgentErrorMessage.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/test/java/com/aegis/apa/AgentErrorMessageTest.kt`

**Interfaces:**
- Produces: `AgentFailure.userMessage(language: AppLanguage): String`.
- Produces: `AgentErrorMessage.from(error: Exception, language: AppLanguage = AppLanguage.ZH_CN): String`.

- [ ] Add English failure tests covering local validation, HTTP 401/429/503/418, network failure, and sanitized unexpected exceptions.
- [ ] Run the focused test and verify RED because the language-aware API does not exist.
- [ ] Implement the minimal bilingual mapping and pass `appLanguage` from the production Agent error boundary.
- [ ] Run the focused test and verify GREEN.

### Task 2: Session and Import Runtime Notices

**Files:**
- Create: `app/src/main/java/com/aegis/apa/localization/RuntimeUiCopy.kt`
- Modify: `app/src/main/java/com/aegis/apa/model/BatteryObservation.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainSessionViewModel.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/test/java/com/aegis/apa/MainSessionViewModelTest.kt`
- Create: `app/src/test/java/com/aegis/apa/localization/RuntimeUiCopyTest.kt`

**Interfaces:**
- Produces: language-aware `BatteryObservationAnalyzer.startError`, `explanation`, and `qualityLabel` with Chinese defaults.
- Produces: `MainSessionViewModel.setLanguage(AppLanguage)` for future and restored notices.
- Produces: pure runtime copy for Bug Report rejection and operational failures.

- [ ] Add failing tests for English start/finish/restore/interruption notices and each Bug Report reject category.
- [ ] Run focused tests and verify RED.
- [ ] Implement the copy provider, session language state, and production call-site wiring.
- [ ] Run focused tests and verify GREEN.

### Task 3: Supplier Feedback and Full Verification

**Files:**
- Modify: `app/src/main/java/com/aegis/apa/HardwareSupplierFeedback.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/test/java/com/aegis/apa/HardwareSupplierFeedbackTest.kt`

**Interfaces:**
- Produces: `HardwareSupplierFeedback.format(..., language: AppLanguage = AppLanguage.ZH_CN): String?`.

- [ ] Add a failing English feedback test that preserves device, RAM, ROM, model, and source values.
- [ ] Run the focused test and verify RED.
- [ ] Implement bilingual labels and pass `appLanguage` from the Device screen.
- [ ] Run focused tests and the full unit suite.
- [ ] Run `git diff --check`, AndroidTest compilation, lint, and Debug/Release APK assembly.
- [ ] Perform a final review of only this plan's touched behavior and fix any Critical/Important issue with RED-to-GREEN coverage.
