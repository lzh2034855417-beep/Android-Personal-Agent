# Android Personal Agent (APA)

An experimental Android app that explains battery, heat and resource readings using data the phone actually provides.

[中文](README.md) · [Roadmap](ROADMAP.md) · [Architecture audit](docs/ARCHITECTURE.md) · [Data contract](docs/DATA_MODEL.md) · [Technical debt](docs/TECH_DEBT.md) · [Testing](docs/TESTING.md)

> The current source is configured as pending release `0.3.1` (`versionCode 6`); debug installs display `0.3.1-preview`. Preserve this candidate and complete release checks before publishing, no earlier than 2026-10-17 02:23:45 Asia/Shanghai (15 days after v0.3.0). See the [release checklist](docs/RELEASE_0.3.1.md). Published APKs and release notes are listed on [GitHub Releases](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases).

## Features

- Local device/display/battery/RAM/storage readings, limited local rules and previewable PNG report cards, without a model key.
- Structured device identity: original system identifiers, resolved display name and lookup source. Unknown models fall back to the system value.
- User-triggered cloud analysis with a personally configured provider key; limited local rules when no valid configuration is available.
- Root-free Android Bug Report import is the primary app-attribution path; a collapsed two-point battery measurement remains only as an auxiliary interval-rate tool.
- User-triggered, read-only Root power diagnostics with evidence, confidence and manual Scene recommendations.
- Optional Usage Access, Shizuku and Root; ordinary Android APIs work independently.

APA returns explanations; it does not execute model suggestions or change scheduling, clean files or flash devices. A single snapshot cannot establish battery lifetime or a replacement decision.

## Getting started

1. Open Device to inspect local readings without a key or Root.
2. Open Agent, choose a report and ask a question. Without a configured key, the response uses local rules.
3. For online analysis, open Settings, choose a provider, enter your key and save it encrypted for seven days. Return to Agent and explicitly send.
4. Check the report-card preview before choosing a sharing app.

The configured catalog includes DeepSeek, OpenAI, Anthropic, MiMo and Kimi. See [CloudProviderCatalog](app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt) for configured model/endpoint values. These defaults are not proof of account availability; this audit made no live provider requests.

### System power diagnostics

For app attribution, ordinary users should first use **Agent → Select report → System power diagnostics → Import system report**. The collapsed **Auxiliary: rough battery measurement** only answers “how fast did this interval drain?” Unplug first, start it, then leave APA and use the phone normally for at least 30 minutes. APA stores only one local start checkpoint. It can resume after process death or reboot, but the user must confirm whether charging occurred; restored measurements are always labelled rough. Reporting charging or uncertainty invalidates the measurement. This tool does not identify a responsible app.

Root users may instead use the read-only collector below. APA treats rate measurement and app attribution as different evidence and no longer substitutes a single instantaneous battery snapshot.

The report panel now includes expandable generation instructions. Some Xiaomi, Redmi and POCO builds generate a Bug Report ZIP after entering `*#*#284#*#*` in the system dialer. On other Android builds, enable Developer options and search Settings for “Take bug report” or the OEM equivalent, then choose an interactive report. OEM menus and output locations vary. Bug reports can contain sensitive data: do not publish them. APA extracts only an allowlisted diagnostic subset locally and does not send the raw file to a model.

Use **Agent → Select report → System power diagnostics → Start read-only diagnostics** and grant Root when prompted. APA runs only a fixed allowlist of power-related reads. Unsupported, denied or timed-out sources are reported without discarding successful sections. Results separate facts, interpretation, confidence and manual Scene advice. APA never freezes, throttles or changes settings.

After a successful import or collection, APA automatically selects the bounded human-readable diagnostic summary for the next question. The user can deselect it before sending. The raw system report and Root output are neither sent nor retained in session state.

## Capability levels

### Guided diagnosis (current development code)

Choose Guided diagnosis near System power diagnostics in Agent to investigate idle drain or heat during use without a model key. After confirming answers and samples, local rules show facts, up to three unverified explanations, evidence gaps and one primary action. Incomplete sources, shared UID groups, unknown report ownership or time cannot authorize a specific app adjustment. Production report clock bounds are usually unknown and cannot establish adjustment effects.

The guide lives only in the page ViewModel: configuration changes retain it, but process death requires starting again. A session holds at most two confirmed summaries. Battery temperature and reported thermal state are descriptive observations with separate sources and times, not CPU/surface temperatures or cooling/health percentages. There is no universal danger threshold or instruction to create heat.

Explain this diagnosis requires preview and explicit send confirmation. Free notes default off; only current allowlisted evidence, local results and the primary action are sent, without ordinary chat history, raw reports or unique device identifiers. The combined system/user prompts have an inclusive 16 KiB UTF-8 limit; oversized payloads are rejected without truncation. Package names and times can still be sensitive. Model prose cannot replace the local action, and valid format does not prove accuracy. Synthetic real-parser → rule → payload checks have run; UI/protocol instrumentation is compiled only. Authorized device execution, human usability checks and live-model evaluation remain release gates: see [Testing](docs/TESTING.md) and [Explanation evaluation](docs/guided-diagnostics-evaluation.md).

### Diagnose → adjust → retest

After a successful diagnostic, open Local verification records to preview and save a baseline, record one manual adjustment and rollback plan, then select the current diagnostic as a retest. No Key is required. Records are separately encrypted, retained for 30 days and limited to 20; clearing chat keeps them. Same-source samples still need verified windows, ownership, scenario and per-metric gates. Unknown or overlapping cumulative windows permit only raw-value display; observations do not prove adjustment effects.

Explain this comparison previews the sending scope before requesting a model. Only the current record's structured summary and local results are sent; title, scenario, adjustment, rollback and side-effect notes default off. Attachments above 16 KiB block sending without truncation. Explanations use no ordinary chat history and are never stored in chat or the verification archive. A model explanation cannot replace the local gate, and lexical filtering does not guarantee semantic safety. Synthetic JVM tests and APK compilation are verified; protocol/UI instrumentation was compiled only, with device execution and real model performance pending separate validation.

| Level | Implemented | Limits |
| --- | --- | --- |
| 0 | Ordinary APIs; optional daily foreground-use estimate | Foreground duration is not screen-on time or health certification |
| 1 | Shizuku installation detection and launch | No Binder connection, authorization or advanced API integration yet |
| 2 | Known Root indicators; manually read selected battery/CPU nodes | Manager detection is not authorization; OEM compatibility varies |

App visibility can limit enumeration. Root readings retain separate timestamps and require manual refresh. Local analysis mainly consumes the basic snapshot; choosing an advanced level does not mean all attachments are analyzed locally.

## Data handling

- Basic readings and visible apps are collected **locally on entry and Activity resume**. Usage events are read if Usage Access was granted. Collection and transmission selection are different boundaries.
- When a Root interface or manager is detected, entry and foreground resume check APA's identity with `su -c "id -u"`. Only UID 0 confirms access, independently of battery-node availability. The check is limited to 8 seconds and 1 KiB; it does not read a full report, change the system, or upload data. A manager grant prompt may appear. Denied, timed-out or failed checks are not automatically repeated: use **Recheck Root access**, or open the manager from APA and return to retry. Report collection remains manual.
- Explicit online Send triggers a direct provider request with a basic snapshot, selected Level report, optional app report and any diagnostic summary that remains selected after import or collection; there is no APA relay server.
- Deterministic battery-observation answers are generated locally. Configuring a provider key does not automatically send the exact observation timestamps or battery trajectory.
- A successful system diagnostic selects only its bounded human-readable summary and shows that attachment state clearly. The user can deselect it; the raw Bug Report ZIP and Root output remain local and are released after parsing.
- Level 0 usage rankings have an independent send toggle, off by default. Granting Usage Access does not select transmission. The base report excludes grades derived from advanced profiles. The report picker explains transmission scope; historical answers may reference earlier data, and clearing the conversation removes that history.
- Local messages, including Scene summaries, are excluded from cloud history. Switching providers does not forward another provider's history. Up to 12 eligible messages from the same provider can be included, potentially containing previous report details in answers.
- Keys are encrypted per provider with Android Keystore AES/GCM and expire locally after seven days by default. This does not revoke the provider-issued key.
- `allowBackup=false` is configured. Legacy cloud backup, Android 31+ cloud backup and device transfer explicitly exclude the credential preferences file. Actual OEM restore behavior remains unverified.
- Up to 100 recent chat messages are encrypted locally with Android Keystore AES/GCM in a bounded 512 KiB archive (64 KiB per field), retained for seven days from the last save. Clear deletes the archive. Displayed text may include device details; key configuration, attachment bodies and raw system reports are not stored. The archive is excluded from backup. Agent shows read/save failures; oversized messages are not saved. Drafts, attachment choices and diagnostic objects remain session-only; select reports again after restarting.\n- Stop releases the UI and closes the connection in the background; a provider may continue processing an accepted request. Retry after failure or interruption uses the current provider and report choices, refreshes evidence and sends a new request without duplicating the question. No automatic retries occur.\n- The auxiliary battery measurement separately retains its start point and observed-charging flag, without a trajectory; restored measurements require charging confirmation and remain rough. Expiry is enforced when reopening; forcibly ending the process while saving can lose the newest changes.

Do not post keys, account details or unique device identifiers in issues, logs or screenshots. Treat model output as a limited explanation, not a repair verdict.

## Build and test

Android 8/API 26+; compile SDK 36.1, target SDK 36. Use the committed Gradle wrapper and a compatible Android Studio JBR/JDK with the required SDK. Local validation used Android Studio's bundled JBR.

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
```

On Windows use `gradlew.bat` and configure JAVA_HOME/ANDROID_HOME. Initial dependency downloads require network; use `--offline` only with a populated cache.

- Debug package: `com.aegis.apa.preview` (APA Preview); APK: `app/build/outputs/apk/debug/APA-v0.3.1-debug.apk`, installed version `0.3.1-preview`.
- Release package: `com.aegis.apa` (APA). The two installations have separate data.
- Debug and source checks require no release credentials. Release APK/AAB packaging requires complete local signing properties and an existing keystore.
- Ignored `keystore.properties` contains storeFile, storePassword, keyAlias, keyPassword. Optional `-Papa.signingProperties=local-file` selects another properties file; quote the whole argument in PowerShell. Relative storeFile paths resolve from the app module.
- Maintainers must separately increment versions, verify signatures/hashes and complete device checks before publishing. See [Testing](docs/TESTING.md) for evidence and limitations.

## Contributing

This interest-driven project is maintained by an undergraduate in mainland China. Use [GitHub Issues](https://github.com/lzh2034855417-beep/Android-Personal-Agent/issues) for bugs and suggestions. Include model, Android/API, app version/build type, permission state, reproduction steps, expected/actual results and sanitized screenshots. PRs should follow the data contract and report checks and untested paths.

The next milestone closes high-priority audit items before adding Shizuku APIs, streaming or conversation export. See ROADMAP and TECH_DEBT for acceptance criteria.

## License

[MIT](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md).
