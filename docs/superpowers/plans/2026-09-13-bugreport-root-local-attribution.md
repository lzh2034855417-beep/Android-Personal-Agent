# Bug Report / Root Local Power Attribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let ordinary users import a complete Android Bug Report and let Root users run the existing read-only collector, then turn both inputs into the same local, evidence-backed battery attribution verdict before AI explains it.

**Architecture:** Two input adapters emit bounded `RawDiagnosticSection` values. Source-specific parsers merge those sections into `AppPowerEvidence` plus field-level `EvidenceCoverage`. A deterministic attribution engine separates total-consumption ranking from background-suspect ranking and produces `LocalPowerVerdict`; the UI renders that verdict before any cloud answer, and the cloud prompt receives only the bounded structured verdict report.

**Tech Stack:** Kotlin/JVM, Android SDK `ContentResolver`, `ActivityResultContracts.OpenDocument`, Java `ZipInputStream`, Jetpack Compose, JUnit 4, AndroidX Compose UI tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-13-local-power-attribution-design.md`

## Global Constraints

- Do not add broad storage permissions. Import only a user-selected `content://` URI or a URI explicitly shared to APA.
- Never extract an imported ZIP to disk. Enforce per-entry, total-uncompressed, entry-count, and nesting limits while streaming.
- Never persist, upload, log, or place raw Bug Report text in chat history. Only `PowerDiagnosticReportBuilder` output may reach a cloud provider.
- Root collection remains read-only and restricted to `AllowedRootCommand`; no arbitrary shell command API is introduced.
- Local deterministic code owns facts, ranking, confidence, and maximum advice level. AI may clarify wording but may not promote `OBSERVE` to `RESTRICT` or `RESTRICT` to `FREEZE_CANDIDATE`.
- No automatic Scene action. Every recommendation includes a manual action, risk, rollback, and re-test condition.
- Preserve existing behavior while migrating: existing Root tests must keep passing, and old `PowerFinding` callers are removed only after the new verdict path is wired.
- Test fixtures must be short, synthetic/redacted excerpts containing no account identifiers, phone numbers, serial numbers, Android IDs, SSIDs, paths, or notification text.
- Run the focused test after every red step and green step. Commit after each task so any stage can be reverted independently.

---

## File and Data Flow

```text
MainActivity / ACTION_OPEN_DOCUMENT / ACTION_SEND
  -> BugReportImporter (Android URI boundary)
  -> BugReportSectionExtractor (pure Kotlin, bounded streaming)
  -> List<RawDiagnosticSection>

SystemPowerDiagnosticsCollector
  -> RootDiagnosticAdapter
  -> List<RawDiagnosticSection>

List<RawDiagnosticSection>
  -> PackageUidResolver
  -> BatteryStatsEvidenceParser
  -> AlarmEvidenceParser
  -> JobSchedulerEvidenceParser
  -> PowerDiagnosticParser
  -> PowerDiagnosticSnapshot(evidenceCoverage, apps, system)
  -> LocalPowerAttributionEngine
  -> LocalPowerVerdict
  -> PowerDiagnosticReportBuilder (bounded structured text)
  -> CloudLlmProvider (optional explanation only)
```

## Task 1: Safe, Pure-Kotlin Bug Report Extraction

**Files:**

- Create: `app/src/main/java/com/aegis/apa/tool/BugReportSectionExtractor.kt`
- Create: `app/src/test/java/com/aegis/apa/BugReportSectionExtractorTest.kt`

- [ ] Write failing tests for plain-text and ZIP input.

```kotlin
@Test fun extractsWhitelistedSectionsFromPlainText() {
    val text = """
        ------ DUMPSYS batterystats (dumpsys batterystats --checkin) ------
        Estimated power use (mAh):
          UID u0a123: 245.5
        ------ DUMPSYS alarm (dumpsys alarm) ------
        u0a123: 180 wakeups
    """.trimIndent()

    val result = BugReportSectionExtractor.extract(
        input = text.byteInputStream(),
        displayName = "bugreport.txt"
    )

    assertTrue(result is BugReportReadResult.Success)
    assertEquals(listOf("batterystats", "alarm"), (result as BugReportReadResult.Success).sections.map { it.source })
}

```

In the same test class, implement the remaining cases with these exact inputs and assertions:

- `readsReportTextInsideZipWithoutWritingEntriesToDisk`: build an in-memory ZIP containing `bugreport-device.txt` with the text above and assert `Success` with the same two source names.
- `rejectsEntryWhoseNormalizedNameContainsParentTraversal`: ZIP entry `../bugreport.txt`; assert `Rejected(UNSAFE_ENTRY_NAME)`.
- `rejectsMoreThan128Entries`: pass `BugReportReadLimits(maxEntries = 2)`, create three one-byte `.txt` entries, and assert `Rejected(TOO_MANY_ENTRIES)`.
- `rejectsEntryLargerThanEightMiB`: pass `maxEntryBytes = 16`, create one 17-byte report entry, and assert `Rejected(ENTRY_TOO_LARGE)`.
- `rejectsTotalOutputLargerThanThirtyTwoMiB`: pass `maxEntryBytes = 16, maxTotalBytes = 20`, create two 11-byte report entries, and assert `Rejected(TOTAL_TOO_LARGE)`.
- `rejectsNestedZipEntry`: ZIP entry `reports/inner.zip`; assert `Rejected(NESTED_ARCHIVE)` before reading its payload.
- `returnsUnsupportedForUnknownBinary`: input `byteArrayOf(0, 1, 2, 0, 3)` named `dump.bin`; assert `Rejected(UNSUPPORTED_FORMAT)`.
- `stopsWhenCancellationCheckReturnsTrue`: plain text containing the two sections above with `isCancelled = { true }`; assert `Cancelled` and no sections.

- [ ] Run the focused test and verify it fails because the extractor types do not exist.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'D:\AndroidPersonalAgent\gradlew.bat' :app:testDebugUnitTest --tests com.aegis.apa.BugReportSectionExtractorTest
```

- [ ] Implement the bounded extractor with these public types and constants.

```kotlin
data class BugReportReadLimits(
    val maxEntries: Int = 128,
    val maxEntryBytes: Long = 8L * 1024 * 1024,
    val maxTotalBytes: Long = 32L * 1024 * 1024,
    val maxSectionChars: Int = 2 * 1024 * 1024
)

sealed interface BugReportReadResult {
    data class Success(val sections: List<RawDiagnosticSection>) : BugReportReadResult
    data class Rejected(val reason: BugReportRejectReason) : BugReportReadResult
    data object Cancelled : BugReportReadResult
}

enum class BugReportRejectReason {
    EMPTY, UNSUPPORTED_FORMAT, CORRUPT_ARCHIVE, UNSAFE_ENTRY_NAME,
    NESTED_ARCHIVE, TOO_MANY_ENTRIES, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE
}

object BugReportSectionExtractor {
    fun extract(
        input: InputStream,
        displayName: String?,
        limits: BugReportReadLimits = BugReportReadLimits(),
        isCancelled: () -> Boolean = { false }
    ): BugReportReadResult
}
```

Implementation rules:

- Identify ZIP by the `PK\u0003\u0004` signature, not only by filename.
- For ZIPs, iterate `ZipInputStream` entries; reject names beginning with `/`, containing `\`, drive prefixes, or normalized `..` segments; reject entries ending in `.zip`.
- Count bytes while reading, stop immediately at any limit, and close each entry in `finally`.
- Consider only text-like entries whose basename starts with `bugreport` or ends with `.txt`; ignore images and protobuf/binary payloads.
- Recognize both `------ DUMPSYS <service> (...) ------` and `DUMP OF SERVICE <service>:` markers.
- Extract only `batterystats`, `alarm`, `jobscheduler`, `package`, `power`, `deviceidle`, and `thermalservice` sections; map `package`/`packages` to source `packages`.
- Mark a section `TRUNCATED` when it reaches `maxSectionChars`; do not retain unrelated report text.

- [ ] Re-run the focused test and verify all extractor cases pass.

- [ ] Commit.

```powershell
git add app/src/main/java/com/aegis/apa/tool/BugReportSectionExtractor.kt app/src/test/java/com/aegis/apa/BugReportSectionExtractorTest.kt
git commit -m "feat: safely extract bugreport evidence"
```

## Task 2: Android URI Import Boundary and Shared-Intent Handling

**Files:**

- Create: `app/src/main/java/com/aegis/apa/tool/BugReportImporter.kt`
- Create: `app/src/test/java/com/aegis/apa/BugReportImportPolicyTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/main/java/com/aegis/apa/PowerDiagnosticPanel.kt`
- Modify: `app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt`

- [ ] Write a failing JVM test for accepted MIME/display-name combinations and rejection messages.

```kotlin
@Test fun acceptsZipTextAndOctetStreamOnlyWhenNameLooksLikeReport() {
    assertTrue(BugReportImportPolicy.accepts("application/zip", "bugreport-device.zip"))
    assertTrue(BugReportImportPolicy.accepts("text/plain", "bugreport.txt"))
    assertTrue(BugReportImportPolicy.accepts("application/octet-stream", "bugreport.zip"))
    assertFalse(BugReportImportPolicy.accepts("application/octet-stream", "photo.bin"))
}
```

- [ ] Run `BugReportImportPolicyTest` and verify red.

- [ ] Implement `BugReportImporter` as the only Android-specific I/O layer.

```kotlin
class BugReportImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri, isCancelled: () -> Boolean = { false }): BugReportReadResult {
        val name = queryDisplayName(uri)
        val mime = resolver.getType(uri)
        if (!BugReportImportPolicy.accepts(mime, name)) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        return resolver.openInputStream(uri)?.use {
            BugReportSectionExtractor.extract(it, name, isCancelled = isCancelled)
        } ?: BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
    }
}
```

- [ ] Add an `OpenDocument` launcher in `MainActivity` using `arrayOf("application/zip", "text/plain", "application/octet-stream")`; launch it from a new `onImportBugReport` callback. Read on `Dispatchers.IO`, reset “随问题发送” to false before import, and translate every rejection enum into one stable Chinese error string.

- [ ] Add an `ACTION_SEND` intent filter for the three MIME types with `DEFAULT` category. Consume only `Intent.EXTRA_STREAM` content URIs with temporary read grants. Do not add `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, or `MANAGE_EXTERNAL_STORAGE`.

- [ ] Update `PowerDiagnosticPanel` so every user sees “导入系统报告”; show “开始只读诊断（Root）” only when Root is available. Add importing state text and retain the existing “只诊断，不执行” notice.

- [ ] Add Compose assertions that the import button is visible without Root and that no automatic-action wording is present.

- [ ] Run focused JVM and compiled instrumentation tests.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'D:\AndroidPersonalAgent\gradlew.bat' :app:testDebugUnitTest --tests com.aegis.apa.BugReportImportPolicyTest
& 'D:\AndroidPersonalAgent\gradlew.bat' :app:compileDebugAndroidTestKotlin
```

- [ ] Commit.

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/aegis/apa app/src/test/java/com/aegis/apa/BugReportImportPolicyTest.kt app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt
git commit -m "feat: import system bug reports"
```

## Task 3: Converge Root and Import Paths on One Section Contract

**Files:**

- Create: `app/src/main/java/com/aegis/apa/tool/RootDiagnosticAdapter.kt`
- Create: `app/src/test/java/com/aegis/apa/RootDiagnosticAdapterTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/tool/SystemPowerDiagnosticsCollector.kt`
- Modify: `app/src/test/java/com/aegis/apa/SystemPowerDiagnosticsCollectorTest.kt`

- [ ] Write a failing adapter test asserting status, truncation, detail, and output are preserved exactly.

```kotlin
@Test fun mapsEveryRootResultToTheSharedSectionContract() {
    val result = RootCommandResult(
        AllowedRootCommand.ALARM,
        DiagnosticSourceStatus.TRUNCATED,
        "alarm excerpt",
        truncated = true,
        detail = "输出过长"
    )
    assertEquals(
        RawDiagnosticSection("alarm", DiagnosticSourceStatus.TRUNCATED, "alarm excerpt", true, "输出过长"),
        RootDiagnosticAdapter.toSection(result)
    )
}
```

- [ ] Run the test and verify red.

- [ ] Implement `RootDiagnosticAdapter.toSection` and `toSections`; replace the inline mapping in `SystemPowerDiagnosticsCollector`.

- [ ] Add an equivalence test: the same synthetic sections passed through import and Root adapters must produce identical `apps`, `system`, and source statuses from `PowerDiagnosticParser`.

- [ ] Run adapter and collector tests; commit.

```powershell
git add app/src/main/java/com/aegis/apa/tool/RootDiagnosticAdapter.kt app/src/main/java/com/aegis/apa/tool/SystemPowerDiagnosticsCollector.kt app/src/test/java/com/aegis/apa
git commit -m "refactor: share diagnostic section pipeline"
```

## Task 4: Field-Level Evidence Coverage and Source-Specific Parsers

**Files:**

- Modify: `app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt`
- Create: `app/src/main/java/com/aegis/apa/tool/PackageUidResolver.kt`
- Create: `app/src/main/java/com/aegis/apa/tool/BatteryStatsEvidenceParser.kt`
- Create: `app/src/main/java/com/aegis/apa/tool/AlarmEvidenceParser.kt`
- Create: `app/src/main/java/com/aegis/apa/tool/JobSchedulerEvidenceParser.kt`
- Modify: `app/src/main/java/com/aegis/apa/tool/PowerDiagnosticParser.kt`
- Create: `app/src/test/java/com/aegis/apa/PackageUidResolverTest.kt`
- Create: `app/src/test/java/com/aegis/apa/BatteryStatsEvidenceParserTest.kt`
- Create: `app/src/test/java/com/aegis/apa/AlarmEvidenceParserTest.kt`
- Create: `app/src/test/java/com/aegis/apa/JobSchedulerEvidenceParserTest.kt`
- Modify: `app/src/test/java/com/aegis/apa/PowerDiagnosticParserTest.kt`

- [ ] Add failing parser tests using one small redacted Xiaomi-like excerpt and alternate iQOO/vivo whitespace/header forms. Cover numeric UID, `u0a123`, shared UID, decimal mAh, foreground/background duration, wakelock duration, wakeup alarms, and jobs.

- [ ] Add coverage tests proving `AVAILABLE` source plus no recognized rows becomes `NOT_PARSED`, not zero.

```kotlin
enum class EvidenceField { UID_PACKAGES, POWER_MAH, FOREGROUND_TIME, WAKELOCK_TIME, WAKEUP_ALARMS, JOBS }
enum class EvidenceFieldStatus { PARSED, NOT_PRESENT, NOT_PARSED, SOURCE_UNAVAILABLE, TRUNCATED }
data class EvidenceCoverage(val fields: Map<EvidenceField, EvidenceFieldStatus>)
```

- [ ] Extend `AppPowerEvidence` without changing existing constructor call sites by appending defaults.

```kotlin
val foregroundDurationMillis: Long? = null,
val backgroundDurationMillis: Long? = null,
val sharedUid: Boolean = false
```

- [ ] Implement each parser as a pure function returning keyed partial rows plus field status. Do not silently convert an unrecognized line to `0`.

```kotlin
data class PartialAppEvidence(
    val uid: Int,
    val estimatedPowerMah: Double? = null,
    val foregroundDurationMillis: Long? = null,
    val backgroundDurationMillis: Long? = null,
    val wakeLockDurationMillis: Long? = null,
    val wakeupCount: Long? = null,
    val alarmCount: Long? = null,
    val jobCount: Long? = null
)
```

- [ ] Make `PowerDiagnosticParser` merge partial evidence by UID, attach all packages for shared UIDs, and store `EvidenceCoverage` in `PowerDiagnosticSnapshot` with a default so old tests compile during migration.

- [ ] Run the five focused parser test classes and existing full unit suite; commit.

```powershell
git add app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt app/src/main/java/com/aegis/apa/tool app/src/test/java/com/aegis/apa
git commit -m "feat: parse per-app power evidence"
```

## Task 5: Deterministic Local Attribution Verdict

**Files:**

- Modify: `app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt`
- Create: `app/src/main/java/com/aegis/apa/agent/LocalPowerAttributionEngine.kt`
- Create: `app/src/test/java/com/aegis/apa/LocalPowerAttributionEngineTest.kt`
- Modify: `app/src/main/java/com/aegis/apa/tool/SystemPowerDiagnosticsCollector.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/PowerDiagnosticFindingEngine.kt`
- Modify: `app/src/test/java/com/aegis/apa/PowerDiagnosticFindingEngineTest.kt`

- [ ] Write failing tests for these non-negotiable classifications:

  - mAh alone ranks an app under total consumption but never labels it a background offender.
  - long foreground usage explains high mAh as use cost.
  - two independent background signals produce at most `RESTRICT`.
  - system UID, phone/SMS/launcher/payment/Root packages, and shared UIDs never exceed `OBSERVE` without an explicit package-level split.
  - missing/incompatible windows prevent per-hour normalization and reduce confidence.
  - no parsed app evidence returns one concrete next-sampling action.

```kotlin
enum class DiagnosticInputSource { BUGREPORT, ROOT }
enum class PowerVerdictType { SUFFICIENT, INSUFFICIENT }

data class RankedPowerCandidate(
    val uid: Int?,
    val packageNames: List<String>,
    val facts: List<String>,
    val confidence: DiagnosticConfidence,
    val maxAdviceLevel: AdviceLevel,
    val reason: String
)

data class LocalPowerVerdict(
    val type: PowerVerdictType,
    val totalConsumption: List<RankedPowerCandidate>,
    val backgroundSuspects: List<RankedPowerCandidate>,
    val nextStep: String?,
    val limits: List<String>
)
```

- [ ] Implement conservative first-version thresholds in one named policy object:

```kotlin
object AttributionPolicy {
    const val MIN_WAKELOCK_MS = 10 * 60_000L
    const val MIN_WAKEUP_ALARMS = 100L
    const val MIN_JOBS = 100L
    const val MIN_BACKGROUND_SIGNALS_FOR_RESTRICT = 2
    const val MAX_RESULTS = 3
}
```

Rules:

- `estimatedPowerMah` affects only `totalConsumption` sorting.
- Background signals are wakelock duration, wakeup/alarm count, and job count. A metric is active only when parsed and above policy threshold.
- Foreground time above background time adds “前台使用成本” and suppresses background classification unless two background signals remain active.
- Initial release never emits `FREEZE_CANDIDATE`; keep the enum for compatibility but cap local results at `RESTRICT` until multi-device validation establishes a safe calibrated rule.
- `sharedUid` and protected-package candidates are capped at `OBSERVE` with an explicit limitation.
- Every `RESTRICT` candidate supplies exactly one Scene action, one risk, one rollback instruction, and one re-test instruction.

- [ ] Make `SystemPowerDiagnosticsCollector` attach `LocalPowerVerdict`; keep a temporary `PowerFinding` projection only if needed by UI migration in Task 7.

- [ ] Run local attribution, legacy finding, and collector tests; commit.

```powershell
git add app/src/main/java/com/aegis/apa/model/PowerDiagnostic.kt app/src/main/java/com/aegis/apa/agent app/src/main/java/com/aegis/apa/tool/SystemPowerDiagnosticsCollector.kt app/src/test/java/com/aegis/apa
git commit -m "feat: attribute battery drain locally"
```

## Task 6: Structured Local Report and AI Boundary

**Files:**

- Modify: `app/src/main/java/com/aegis/apa/agent/PowerDiagnosticReport.kt`
- Modify: `app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt`
- Modify: `app/src/test/java/com/aegis/apa/PowerDiagnosticReportTest.kt`
- Modify: `app/src/test/java/com/aegis/apa/PowerDiagnosticPromptTest.kt`

- [ ] Write failing tests that the report contains input source, coverage, total-consumption list, background-suspect list, confidence, raw numeric facts, maximum advice, risk/rollback/re-test, and limitations.

- [ ] Add a sentinel raw string (`RAW_BUGREPORT_SECRET`, phone number, notification body) to source sections and assert none appears in the report or cloud prompt.

- [ ] Add prompt tests asserting AI is explicitly forbidden to change metrics, accuse an app based only on mAh, exceed local `maxAdviceLevel`, claim a Scene action ran, or request unrelated missing data.

- [ ] Update `PowerDiagnosticReportBuilder.build(snapshot)` to serialize only snapshot evidence and verdict. Keep `MAX_REPORT_CHARS = 16 * 1024` and the current truncation marker.

- [ ] Change the cloud instruction from “perform diagnosis” to “explain the following local verdict”; require a short answer in this order: conclusion, evidence meaning, one manual Scene step, risk/rollback, re-test. If verdict is insufficient, return only the local next step.

- [ ] Run report and prompt tests; commit.

```powershell
git add app/src/main/java/com/aegis/apa/agent/PowerDiagnosticReport.kt app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt app/src/test/java/com/aegis/apa/PowerDiagnosticReportTest.kt app/src/test/java/com/aegis/apa/PowerDiagnosticPromptTest.kt
git commit -m "feat: constrain ai to local power verdict"
```

## Task 7: Local-First Compose UI

**Files:**

- Modify: `app/src/main/java/com/aegis/apa/PowerDiagnosticPanel.kt`
- Modify: `app/src/main/java/com/aegis/apa/MainActivity.kt`
- Modify: `app/src/test/java/com/aegis/apa/MainSessionPowerDiagnosticTest.kt`
- Create: `app/src/androidTest/java/com/aegis/apa/PowerDiagnosticPanelUiTest.kt`
- Modify: `app/src/androidTest/java/com/aegis/apa/StabilityUiTest.kt`

- [ ] Write Compose tests with stable test tags for:

  - import and Root buttons as separate entry points;
  - imported/Root source label;
  - local verdict visible before the “交给 AI 解释” selection control;
  - insufficient evidence state exposing one next step;
  - total consumption and background suspects shown as distinct sections;
  - protected/shared-UID warning;
  - remove clears both snapshot and send-selection state.

- [ ] Replace the single Root-centric card copy with:

```text
系统耗电归因
来源：系统 Bug Report / Root 只读采集
本地结论（不联网也可用）
耗电总量排行
后台异常嫌疑
证据覆盖与限制
[导入系统报告] [开始只读诊断（Root）]
[交给 AI 解释（默认关闭）] [移除]
```

- [ ] Ensure configuration changes/coroutine cancellation produce `Interrupted` without retaining raw text; starting a new import or Root collection deselects the previous report.

- [ ] Keep copy support for the complete AI reply and package names. Do not add execute/freeze/limit buttons.

- [ ] Run JVM session tests and compile instrumentation tests; commit.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'D:\AndroidPersonalAgent\gradlew.bat' :app:testDebugUnitTest --tests com.aegis.apa.MainSessionPowerDiagnosticTest
& 'D:\AndroidPersonalAgent\gradlew.bat' :app:compileDebugAndroidTestKotlin
git add app/src/main/java/com/aegis/apa app/src/test/java/com/aegis/apa app/src/androidTest/java/com/aegis/apa
git commit -m "feat: show local power verdict first"
```

## Task 8: Three-Device Fixture Validation, Documentation, and Final Verification

**Files:**

- Create: `app/src/test/resources/power-fixtures/xiaomi-redacted.txt`
- Create: `app/src/test/resources/power-fixtures/iqoo-redacted.txt`
- Create: `app/src/test/resources/power-fixtures/vivo-redacted.txt`
- Create: `app/src/test/java/com/aegis/apa/VendorPowerFixtureTest.kt`
- Modify: `README.md`
- Modify: `README_EN.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-09-13-local-power-attribution-design.md` only if implementation revealed a documented mismatch.

- [ ] On each device, generate one Bug Report after a representative battery interval. Before adding a fixture, manually reduce it to only the whitelisted section fragments and replace every package/user/device identifier with stable test values such as `com.example.chat` and `u0a123`.

- [ ] Add a fixture test that parses all three files, requires at least one resolved UID, and asserts no forbidden PII pattern appears.

```kotlin
@Test fun vendorFixturesStayRedactedAndProduceEvidence() {
    listOf("xiaomi", "iqoo", "vivo").forEach { vendor ->
        val text = resource("power-fixtures/$vendor-redacted.txt")
        assertFalse(PII_PATTERN.containsMatchIn(text))
        val result = BugReportSectionExtractor.extract(text.byteInputStream(), "$vendor.txt")
        assertTrue((result as BugReportReadResult.Success).sections.isNotEmpty())
    }
}
```

- [ ] On the rooted Xiaomi device only, compare the same approximate observation window through import and Root collection. Record parser/coverage differences in test expectations, not device identifiers.

- [ ] Document the user flow: generate Bug Report, share/select it in APA, read local result, optionally ask AI to explain, manually apply one Scene change, re-test, and roll back if notifications/functions regress.

- [ ] Run the complete verification suite without installing or packaging an APK for the user.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'D:\AndroidPersonalAgent\gradlew.bat' --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Expected evidence:

- All JVM tests pass with zero failures/errors.
- `lintDebug` finishes without new fatal findings.
- Android instrumentation sources compile.
- Debug assembly succeeds; do not install it automatically.

- [ ] Review the working tree for raw logs and placeholders.

```powershell
rg -n "TODO|FIXME|RAW_BUGREPORT_SECRET|serialno|android_id|wifi|ssid" app/src docs README.md README_EN.md CHANGELOG.md
git status --short
git diff --check
```

- [ ] Commit final fixtures/docs only after the redaction scan is clean.

```powershell
git add app/src/test/resources app/src/test/java/com/aegis/apa/VendorPowerFixtureTest.kt README.md README_EN.md CHANGELOG.md docs
git commit -m "test: validate vendor power reports"
```

## Final Review Checklist

- [ ] Spec coverage: both ordinary-user import and Root collection reach the same parser and attribution engine.
- [ ] Security coverage: no broad storage permission, no ZIP extraction, bounded streaming, traversal/nesting/size/cancellation tests, no raw cloud payload.
- [ ] Type consistency: every model constructor, copy call, report builder, Compose state, and test uses the final `PowerDiagnosticSnapshot` and `LocalPowerVerdict` types.
- [ ] Product coverage: local verdict works offline; AI is optional; recommendations are manual and reversible.
- [ ] Evidence honesty: missing/unparsed data is never displayed as zero and mAh alone never becomes a background accusation.
- [ ] Placeholder scan: no `TODO`, `FIXME`, stub parser, fake production result, or unredacted fixture remains.
- [ ] Git hygiene: each task is independently revertible and the final working tree contains only intentional changes.
