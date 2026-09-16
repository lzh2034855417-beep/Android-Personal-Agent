# Pre-device Trust Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Agent capability selection, attachment controls, cloud disclosure, and Level 0 output match what APA can actually verify and consume.

**Architecture:** Add small pure policy functions for effective advice level and report-picker presentation, then let the Compose screen and cloud prompt consume those results. Keep Shizuku unavailable until a real authorization integration exists, and require a successful Root battery read for Level 2 advice.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-09-16-pre-device-trust-gates.md`

## Global Constraints

- Preserve all existing dirty-worktree changes.
- Do not add automatic Root, Scene, freeze, limit, delete, or cleanup actions.
- Every behavior change follows red-green TDD.
- Do not claim device compatibility without real-device verification.

---

### Task 1: Effective advice capability

**Files:**
- Create: `app/src/main/java/com/aegis/apa/agent/AdviceCapabilityPolicy.kt`
- Create: `app/src/test/java/com/aegis/apa/AdviceCapabilityPolicyTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`

**Interfaces:**
- Produces: `AdviceCapabilityPolicy.effectiveLevel(requestedLevel: String, shizukuAuthorized: Boolean, rootAuthorized: Boolean): String`
- Produces: `AdviceCapabilityPolicy.allowsLevel(level: String, shizukuAuthorized: Boolean, rootAuthorized: Boolean): Boolean`

- [x] Write tests proving L0 is always allowed, unavailable L1/L2 fall back to L0, and successful Root evidence permits L2.
- [x] Run `:app:testDebugUnitTest --tests com.aegis.apa.AdviceCapabilityPolicyTest` and confirm the missing policy fails compilation.
- [x] Add the minimal pure policy.
- [x] Run the focused test and confirm it passes.
- [x] Use the effective level for prompt/report construction and disable unavailable level buttons.

### Task 2: Level 0 output safety

**Files:**
- Modify: `app/src/test/java/com/aegis/apa/PowerDiagnosticPromptTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt`

**Interfaces:**
- Consumes: `sanitizeCloudAnalysisResponse(response: String, selectedLevel: String): String`

- [x] Add a failing test with a safe sentence and Magisk/LSPosed/shell advice on the same input line.
- [x] Run the focused test and confirm advanced advice remains.
- [x] Split response text into sentence-like segments and remove advanced segments while retaining safe content.
- [x] Run the focused prompt tests and confirm they pass.

### Task 3: Honest attachment presentation and disclosure

**Files:**
- Create: `app/src/main/java/com/aegis/apa/agent/AgentAttachmentPolicy.kt`
- Create: `app/src/test/java/com/aegis/apa/AgentAttachmentPolicyTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`

**Interfaces:**
- Produces: `AgentAttachmentPolicy.showAppReport(isOnline: Boolean): Boolean`
- Produces: `AgentAttachmentPolicy.disclosure(isOnline: Boolean): String`

- [x] Add failing tests proving the app-report toggle is online-only and disclosure names every transmitted report category plus derived Root fields.
- [x] Run the focused test and confirm the policy is missing.
- [x] Add the minimal policy and pass `isOnline` into the screen presentation.
- [x] Run the focused tests and confirm they pass.

### Task 4: Regression verification and documentation

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/TESTING.md`
- Modify: `docs/TECH_DEBT.md`

- [x] Run the complete JVM unit suite.
- [x] Run Debug and Release builds plus lint and androidTest compilation.
- [x] Update the roadmap and test record with only the freshly verified results.
- [x] Inspect the final diff for accidental unrelated changes and report remaining device-only risks.
