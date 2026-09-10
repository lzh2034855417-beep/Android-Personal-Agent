# Android Personal Agent (APA)

An experimental Android app that explains battery, heat and resource readings using data the phone actually provides.

[中文](README.md) · [Roadmap](ROADMAP.md) · [Architecture audit](docs/ARCHITECTURE.md) · [Data contract](docs/DATA_MODEL.md) · [Technical debt](docs/TECH_DEBT.md) · [Testing](docs/TESTING.md)

> The source version remains `0.1.1`; this working branch contains unreleased changes. Consult the relevant [GitHub release tag](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases) for downloadable features. This audit did not publish a release.

## Features

- Local device/display/battery/RAM/storage readings, limited local rules and previewable PNG report cards, without a model key.
- Structured device identity: original system identifiers, resolved display name and lookup source. Unknown models fall back to the system value.
- User-triggered cloud analysis with a personally configured provider key; limited local rules when no valid configuration is available.
- Local Scene CSV import and interval summaries. Scene is not an online attachment.
- Optional Usage Access, Shizuku and Root; ordinary Android APIs work independently.

APA returns explanations; it does not execute model suggestions or change scheduling, clean files or flash devices. A single snapshot cannot establish battery lifetime or a replacement decision.

## Getting started

1. Open Device to inspect local readings without a key or Root.
2. Open Agent, choose a report and ask a question. Without a configured key, the response uses local rules.
3. For online analysis, open Settings, choose a provider, enter your key and save it encrypted for seven days. Return to Agent and explicitly send.
4. Check the report-card preview before choosing a sharing app.

The configured catalog includes DeepSeek, OpenAI, Anthropic, MiMo and Kimi. See [CloudProviderCatalog](app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt) for configured model/endpoint values. These defaults are not proof of account availability; this audit made no live provider requests.

### Scene import

Save a Scene CSV to the phone, then use **Agent → Select report → Import Scene CSV**. The import count and summary appear locally. UTF-8 (including BOM), comma-separated, at most 2 MiB. Common Chinese/English headers are supported. Numeric units belong in headers: mA/uA/A and mW/W are converted explicitly. Screenshots, PDFs and multiline quoted fields are unsupported; not every Scene export version has been tested. See the [synthetic example](docs/examples/scene-example.csv) and [data contract](docs/DATA_MODEL.md).

## Capability levels

| Level | Implemented | Limits |
| --- | --- | --- |
| 0 | Ordinary APIs; optional daily foreground-use estimate | Foreground duration is not screen-on time or health certification |
| 1 | Shizuku installation detection and launch | No Binder connection, authorization or advanced API integration yet |
| 2 | Known Root indicators; manually read selected battery/CPU nodes | Manager detection is not authorization; OEM compatibility varies |

App visibility can limit enumeration. Root readings retain separate timestamps and require manual refresh. Local analysis mainly consumes the basic snapshot; choosing an advanced level does not mean all attachments are analyzed locally.

## Data handling

- Basic readings and visible apps are collected **locally on entry and Activity resume**. Usage events are read if Usage Access was granted. Collection and transmission selection are different boundaries.
- Explicit online Send triggers a direct provider request with a basic snapshot, selected Level report and optional app report; there is no APA relay server.
- Level 0 usage rankings have an independent send toggle, off by default. Granting Usage Access does not select transmission. The base report excludes grades derived from advanced profiles. The report picker explains transmission scope; historical answers may reference earlier data, and clearing the conversation removes that history.
- Local messages, including Scene summaries, are excluded from cloud history. Switching providers does not forward another provider's history. Up to 12 eligible messages from the same provider can be included, potentially containing previous report details in answers.
- Keys are encrypted per provider with Android Keystore AES/GCM and expire locally after seven days by default. This does not revoke the provider-issued key.
- `allowBackup=false` is configured. Legacy cloud backup, Android 31+ cloud backup and device transfer explicitly exclude the credential preferences file. Actual OEM restore behavior remains unverified.
- Conversations/imports live in memory and may be lost on Activity recreation; leaving Agent may also lose a draft.

Do not post keys, account details or unique device identifiers in issues, logs or screenshots. Treat model output as a limited explanation, not a repair verdict.

## Build and test

Android 8/API 26+; compile SDK 36.1, target SDK 36. Use the committed Gradle wrapper and a compatible Android Studio JBR/JDK with the required SDK. Local validation used Android Studio's bundled JBR.

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
```

On Windows use `gradlew.bat` and configure JAVA_HOME/ANDROID_HOME. Initial dependency downloads require network; use `--offline` only with a populated cache.

- Debug package: `com.aegis.apa.preview` (APA Preview); APK: `app/build/outputs/apk/debug/APA-v0.1.1-debug.apk`.
- Release package: `com.aegis.apa` (APA). The two installations have separate data.
- Debug and source checks require no release credentials. Release APK/AAB packaging requires complete local signing properties and an existing keystore.
- Ignored `keystore.properties` contains storeFile, storePassword, keyAlias, keyPassword. Optional `-Papa.signingProperties=local-file` selects another properties file; quote the whole argument in PowerShell. Relative storeFile paths resolve from the app module.
- Maintainers must separately increment versions, verify signatures/hashes and complete device checks before publishing. See [Testing](docs/TESTING.md) for evidence and limitations.

## Contributing

This interest-driven project is maintained by an undergraduate in mainland China. Use [GitHub Issues](https://github.com/lzh2034855417-beep/Android-Personal-Agent/issues) for bugs and suggestions. Include model, Android/API, app version/build type, permission state, reproduction steps, expected/actual results and sanitized screenshots. PRs should follow the data contract and report checks and untested paths.

The next milestone closes high-priority audit items before adding Shizuku APIs, streaming or persistence. See ROADMAP and TECH_DEBT for acceptance criteria.

## License

[MIT](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md).
