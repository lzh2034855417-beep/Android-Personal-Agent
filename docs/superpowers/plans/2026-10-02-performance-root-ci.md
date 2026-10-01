# Performance, Root and CI plan

**Spec:** ../specs/2026-10-02-performance-root-ci-design.md
**Execution:** Independent file ownership with parallel agents; main integrates and verifies.

- [x] Chat: extract screen and submission preflight; bounded session history; lazy last-30 paging, stable keys and conditional following; JVM and instrumentation fixtures.
- [x] Root: controlled process tests reproduce cancellation; concurrent bounded draining; timeout/cancellation cleanup; main integrates cancellation and old-result guards.
- [x] Diagnostic: Chinese/English conclusion/evidence/sample/next-step cards with existing evidence only; model regressions.
- [x] CI: pinned official action revisions, required SDK/JDK, source checks + emulator job, artifacts and local static validation (YAML parser unavailable).
- [x] Verification: full JVM tests, preview/test APK, both lint variants; device inventory checked, UI fixtures compile only because no device/AVD is available.
- [x] Review: independent review and regression fixes. Local commit/import is the final integration step; no push or publication.

## Progress

- ec83969 baseline and original source both clean. Latest user explicitly authorized all proposed improvements.
- Initial code evidence: chat eagerly renders all messages; profile runner waits before draining pipe; diagnostic English and Chinese use different presentation; repo lacks CI workflow.
- RED: 22 selected tests ran, seven expected failures (three cancellation/no-fallback, three diagnostic fallback/status, one session retention); process helper absence was temporarily isolated so these were runtime assertions, not compilation errors.
- GREEN: 355 JVM tests passed, debug/test APK builds and both lint variants passed (0 errors, 35 warnings). Final post-review verification pending.
- Device inventory: no adb device, AVD or installed system image. Instrumentation fixtures will be compiled, not claimed as executed. CI hosted execution is likewise unverified.
- Independent review caught scroll-animation feedback and obsolete L2/hidden-diagnostic test assumptions; use immediate latest scrolling, allowed L0 fixture, isolated preview conversation/language and enabled emulator soft keyboard.
- Final post-review command succeeded: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --offline --console=plain`. XML confirms 355 tests across 63 suites, zero failure/error. Both lint reports have zero errors and 35 warnings. New timeline and diagnostic UI fixtures compile; neither was run on a device.
