# Published-version regression review — 2026-10-10

Baseline: GitHub release v0.3.0, commit `7756a7b`. Compared against the current
Android Studio checkout (`work/current`), including its uncommitted changes.
Existing changes were preserved; the release was not installed over user data.

## Fixed in this review

1. Multiple bootlogs in a single report could overwrite the entire hardware
   section. A later UFS-only log erased an earlier DDR manufacturer/type.
   Retain the four existing whitelisted fields per archive; complement missing
   fields and keep the first observed value on conflicts. No cache across imports.
   This bug also exists in the release; it is not established as a speed-optimization
   regression or as the cause of the user's exact screenshot.
2. Returning to the activity starts a Root authorization recheck. Its temporary
   CHECKING state caused the chat effect to persistently downgrade a selected
   Level 2 report to Level 0. Preserve the requested selection during pending
   authorization, but continue gating actual use on verified access. Final failure
   still downgrades. This is a regression relative to the release.

## Verification

- Both hardware-loss cases failed before the fix, then passed.
- Root selection test failed with the previous downgrade rule, then passed;
  it also verifies actual access remains restricted while checking.
- 736 JVM tests: zero failures/errors.
- `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`: successful.
- Independent read-only review of both fixes: no blocking findings.
- Repair APK installed with `adb install -r`, preserving application data.
- On the connected Xiaomi phone, `AgentChatHeaderLayoutTest` and
  `RootAuthorizationUiTest` completed: 8 tests passed. Includes the real-page
  pending/success/failure transition, compact/narrow/large-font layouts, and
  detection of the existing Root grant without collecting battery or reports.
- Actual APA MainActivity relaunched successfully after instrumentation.

## Still requires verification / not changed

- Import of the user's exact report and its RAM vendor display still require
  reselecting the report through the system picker. The page tests do not validate
  this private report's contents, and no raw report was pulled from the phone.
- At the time of the verified two-fix build above, imported hardware information
  was session-only in both the release and current
  code. Process restart can lose it; a new report intentionally replaces previous
  imported information. This review does not introduce persistent or cross-report
  hardware merging, which could misidentify another device's report.
- Root authorization is separate from hardware or diagnostic readability.
- The comparison focused on the reported hardware/import, Root selection and
  Agent layout paths; it is not a claim that every project bug has been found.

## Follow-up hardware separation — build and actual-page phone acceptance verified

The subsequent user-approved change separates narrow hardware collection from
manual scheduling reads. Device-page entry reads hardware independently;
Root collection requires already verified authorization and never starts a full
battery/system diagnostic. DDR manufacturer IDs are now collected from the
device-tree node, rather than collecting only DDR type. Unknown IDs are not
guessed as known vendors.

Confirmed hardware fields are kept in a bounded, atomic no-backup local cache,
bound to the current manufacturer/model. Partial reads supplement existing
fields; failures do not clear them. Report fields may be saved only after matching
device headers and explicit confirmation of report ownership. Scheduling reads
exclude the vendor nodes and have a separate, explicitly labelled UI action.

Added JVM tests for the node parser, independent scripts, cache restart,
partial reads, report confirmation/identity checks, corrupt cache and failed
writes; added a UI regression test for visible hardware without a scheduling
profile and independent read actions. A read-only reviewer found two missing
Material 3 imports; these have been corrected in source.

Approval review initially failed due to an account usage limit. After the stated
reset time passed, the same normal approval path succeeded; no blocked command
was rerouted. Current follow-up verification:

- 745 JVM tests, zero failures/errors/skips in the final build.
- `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`: successful.
- Additional actual-device acceptance test compiled successfully: automatic
  hardware availability/cache without scheduling or full diagnostics.
- Repair and test APKs installed with `adb install -r`, preserving app data.
- Phone UI acceptance has NOT passed yet. The test runner waited without
  entering an APA activity while Chrome was foreground. Screen was unlocked
  and on. The hanging run was deliberately stopped via APA force-stop;
  its final `Process crashed` instrumentation status is a consequence of this
  interruption, not established evidence of an app crash.
- On retry, the actual-page test exposed an ambiguous selector: two nodes
  named Device (page chip and navigation tab). Restricted the selector to
  the Tab role; the unchanged app then passed the automatic-read acceptance.
- Real device-tree DDR manufacturer ID was `00000001`. Added a failing JVM
  test, then a separate mode-register-ID mapping for device-tree reads:
  Samsung=1, Hynix=6, Micron=255. Extended bootlog IDs remain separate.
  Reference: [Linux JEDEC definitions](https://github.com/torvalds/linux/blob/master/drivers/memory/jedec_ddr.h).
- Final `RootAuthorizationUiTest`: 2 tests passed, including Samsung/SK cache
  assertions and verifying that battery, scheduling profile and full power
  diagnostics remain uncollected.
- After force-stop, MainActivity launched with `LaunchState: COLD`. Its UI
  hierarchy showed RAM `16GB / Samsung / LPDDR5`, ROM `512GB / SK hynix /
  HN8T271EJKX152`; chip and scheduler remained unread. The no-backup cache
  retained the same confirmed suppliers. No scheduling read was clicked.
- The standalone layout-fixture test Activity still did not enter/resume on
  this phone; that run was stopped and is NOT counted as passing. Independent
  hardware/scheduling scripts are covered by JVM tests, and real-page automatic
  reading without scheduling is covered by the two passing phone tests.

The earlier 736 JVM tests and 8 phone tests apply to the earlier two-fix build,
not to this follow-up's device acceptance.

## Background warnings and measurement-basis checks

The supplied WeChat example reports app estimate 3148 mAh / background
2953 mAh while the overall window is about 1845 mAh. High background power
alone now produces a visible OBSERVE warning; two independent signals remain
required for RESTRICT. An app/window basis conflict or background estimate above
app total blocks RESTRICT even with a wake lock. The warning does not establish
module causation, parser failure or abnormal app behavior from mismatched data.

Conflicting raw values are retained in warnings/limits, but excluded from both
the user-facing Top 5 and local-verdict comparable rank lists. Conflicts have no
percentage. Model instructions retain these warnings even when advice is only
observation, rather than summarizing missing corroboration as normal behavior.

Five new regression tests failed before implementation. The full suite initially
exposed old positive fixtures whose app estimate exceeded their overall window;
those eligible-action fixtures now use coherent totals, while the supplied
conflict remains covered by a negative-action regression. One report-section
assertion was updated for the new risk-warning label.

Final verification: 750 JVM tests, zero failures/errors/skips; lintDebug,
assembleDebug and assembleDebugAndroidTest successful. Independent read-only
review found a second-ranking inconsistency; corrected and rechecked. The
user's private archive and actual external-model response have not been replayed
automatically; re-import and a new question are required for user acceptance.
The repair APK was installed with `adb install -r` (Success), and MainActivity
started successfully with `LaunchState: COLD`; application data was preserved.
