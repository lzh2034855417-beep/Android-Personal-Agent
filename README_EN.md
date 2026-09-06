# Android Personal Agent (APA)

An experimental open-source personal agent for Android devices.

[中文](README.md) | [English](README_EN.md)

![Version](https://img.shields.io/badge/version-v0.1.1-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![License](https://img.shields.io/badge/license-MIT-yellow)

> Current public release: `v0.1.1`
>
> APA is currently in early public testing. AI-generated output is for reference only and should not be the sole basis for device repair, health assessment, or system modification.

APA reads device information only after the user explicitly selects the data scope. The selected report can then be interpreted by the local analyzer or a user-configured cloud model. The project emphasizes informed consent, minimal data transfer, and clearly separated capability levels. It does not upload device reports automatically in the background.

## A Note from the Author

> I am an undergraduate student from mainland China. APA is a personal open-source project built purely out of interest, as a way to explore the combination of Android system capabilities and personal AI agents. The project is still young, and developers, testers, and Android enthusiasts are welcome to help make it more reliable, transparent, and useful.

## Testing Requirements

- Level 0: Any Android device running Android 8.0 or later
- Level 1: A device with Shizuku installed and running
- Level 2: A rooted device managed through KernelSU, Magisk, or a compatible solution

Capability levels describe the scope of accessible data. They are not device-performance or user ranks. Neither Root nor Shizuku is required for the basic APA experience.

> KernelSU is a Root management solution and therefore belongs to Level 2. Level 1 refers to Shizuku.

## Contact and Collaboration

Please use this repository's **GitHub Issues** for bug reports, feature requests, and development discussions. Developers working with Android, Kotlin, system utilities, or LLM applications are welcome to open Issues and Pull Requests.

Do not publish API keys, phone numbers, QQ accounts, device serial numbers, or other sensitive information in Issues, screenshots, or logs. No personal QQ or Telegram account is currently published, so project maintenance does not become tied to a private social identity.

## Features

- Uses AI to provide a basic assessment, supporting evidence, and non-binding suggestions based on device and application reports explicitly attached by the user
- Reads the device model, Android version, battery, memory, and storage state
- Detects common Root managers, frameworks, and regular applications
- Lists launchable applications and basic application details
- Lets the user select Level 0, Level 1, or Level 2 reports for each Agent request
- Reads selected low-level battery data after Root authorization
- Experimentally collects a read-only device profile with SoC, kernel, CPU topology, frequency policies, and relevant thermal nodes without changing system scheduling
- Supports multi-turn Agent conversations and per-message report labels
- Stores API keys independently for each provider, encrypted for seven days by default

## Capability Levels

| Level | Data source | Current status |
| --- | --- | --- |
| Level 0 | Standard Android APIs | Device, display, instantaneous battery, memory, storage, and basic app data are implemented; Usage Access can optionally summarize today's app foreground time |
| Level 1 | Shizuku | Installation detection and navigation are implemented; service state, permission state, and advanced APIs are still in development |
| Level 2 | Root | `su`, KernelSU/Magisk detection, selected low-level battery data, and read-only scheduling profile collection are implemented |

Level 0 works independently when no advanced permission is available.

## Cloud Models

| Provider | Default model | API |
| --- | --- | --- |
| DeepSeek | `deepseek-v4-flash` | DeepSeek Chat Completions |
| OpenAI | `gpt-5.6-terra` | OpenAI Chat Completions |
| Anthropic | `claude-sonnet-5` | Anthropic Messages API |
| Xiaomi MiMo | `mimo-v2.5` | MiMo OpenAI-compatible API |
| Kimi | `kimi-k3` | Kimi China Chat Completions |

Providers other than DeepSeek still need broader testing across accounts and devices. Model names and service availability may change at the provider's discretion.

## Privacy and Security

- Device data is read locally by default.
- A selected report is sent only when the user actively submits a message.
- API keys are encrypted with AES/GCM backed by Android Keystore.
- Keys are stored independently for each provider and expire after seven days by default.
- Android system backup is disabled to keep credential files out of device backups.
- No developer or tester API key is included in the source code.
- APA does not operate a proxy server. Cloud requests are sent directly to the provider selected by the user.

When a cloud model is used, the selected report is subject to that provider's privacy policy and data-processing terms. Review the report scope before sending it.
Usage Access is entirely optional. APA reads activity events from today and yesterday to recover sessions crossing midnight, then clips their durations to today's midnight through the sampling time. Missing or delayed events and overlapping multi-window apps limit accuracy: these are estimates, not exact screen-on time. No records means unavailable, not zero. Returning from permission settings refreshes the data automatically.

Local and cloud analysis collect a fresh basic snapshot on a background thread before each request. Root battery and device profiles retain their own timestamps and require manual rereading. Each cloud request checks the key's local expiration. Settings display the installed version and build type.

## Requirements

- Android 8.0 (API 26) or later
- Network access is used only for cloud requests initiated by the user
- Usage Access, Shizuku, and Root are optional capabilities

## Building Locally

Android Studio, the Android SDK, and a compatible JDK are required.

Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

macOS / Linux:

```bash
./gradlew :app:assembleDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/APA-v0.1.1-debug.apk
```

## Model Configuration

1. Open the Settings page in APA.
2. Select a model provider.
3. Enter an API key issued by that provider.
4. Select **Save encrypted for 7 days**.
5. Return to the Agent page, choose the reports for this turn, and send a question.

Each provider uses an independent key. Switching providers does not reuse another provider's credentials.

## Known Limitations

- Level 1 does not yet use the real Shizuku Binder permission or system APIs.
- The Scene one-day battery report currently has a selection entry but no file import or parser.
- Conversation history is kept only in the current application process.
- Cloud requests do not yet support streaming, cancellation, or automatic retry.
- Application detection relies on known package names and Android package visibility.
- Official Release APK signing must be configured by the project maintainer.

## Risk Notice

Root operations may cause system instability, data loss, or warranty issues. APA currently reads only limited low-level information and does not automatically clean data, grant permissions, or change system settings. Never grant Root or Shizuku access to software you do not trust.

AI output may be incomplete or incorrect. Independently verify any suggestion involving Root, system components, data deletion, or battery repair.

APA's AI assessment is based only on the limited reports attached to the current request. It is basic information analysis, not a professional inspection, repair conclusion, or long-term device health diagnosis.

## Roadmap

- Integrate real Shizuku service and permission states
- Import and parse Scene one-day battery reports
- Add streaming responses, stop generation, and regeneration
- Persist and export conversation history
- Expand automated tests and device coverage

## Testing and Bug Reports

When opening an Issue, please include:

- Device brand and model
- Android and system version
- Whether KernelSU, Magisk, Shizuku, or LSPosed is in use
- Reproduction steps
- Screenshots or error text with personal information removed

Never include an API key in an Issue, screenshot, or log.

## Contributing

1. Fork the repository.
2. Create a feature branch from `main`.
3. Keep the change focused and document the test device and verification result.
4. Open a Pull Request.

If you are new to open-source collaboration or unfamiliar with GitHub, start by opening an Issue describing your idea.

## License

This project is licensed under the [MIT License](LICENSE).
