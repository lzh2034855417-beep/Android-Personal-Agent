# Android Personal Agent (APA)

Understand your Android phone's battery use, heat and hardware readings. Free and open source, with no account required. Basic features work without Root or a model API key.

[![Latest release](https://img.shields.io/github/v/release/lzh2034855417-beep/Android-Personal-Agent)](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#download-and-install)
[![MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**[Download the latest APK](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)** · [中文](README.md) · [User guide (Chinese)](docs/USAGE.md) · [Privacy](docs/PRIVACY.md#english-summary)

## What can it help with?

| Your question | What APA provides |
| --- | --- |
| What is my phone's current state? | Battery level, temperature, RAM, storage and device readings |
| Which apps consume the most battery? | System-report estimates, background clues and their reporting window |
| How fast did the battery drop? | Valid system-report statistics or a rough unplugged observation |
| Who made the RAM or storage? | Identification from readable hardware fields; unavailable fields stay unknown |
| What does the report mean? | Local explanations, plus optional AI explanations using your own provider |

A high consumption ranking does not prove an anomaly. One temperature or battery reading cannot establish a fault. APA explains missing evidence and does not automatically clean files, freeze apps or modify the system.

## Download and install

1. Open **[Releases](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)** and download `APA-vVERSION-release.apk` under **Assets**. The `Source code` archives are not installers.
2. Open the APK on your phone and follow Android's installation prompts.
3. Open APA → **Device**. For an update, prefer installing over the existing official app. If Android reports a signature mismatch, report it before uninstalling; uninstalling may remove local records.

Requires **Android 8.0 or newer**. The current stable release is **[0.3.1](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/tag/v0.3.1)**. Available readings depend on the device, permissions and report contents.

## Getting started

- **Device readings:** open Device. No additional setup is needed.
- **App battery consumption:** open Agent, import your phone's Android Bug Report ZIP or TXT, then inspect the result and ask a question. The app includes report-generation instructions.
- **Optional online AI:** open Settings, choose a provider and save your own API key. Return to Agent, review the attachments and send. Provider charges may apply; local features remain available without a key.

## Before you use it

Root is optional. Device readings, Bug Report import and rough battery observations do not require it. Shizuku currently supports installation detection and navigation only.

Missing vendor fields remain unknown; APA does not infer a hardware vendor from the phone model. Compatibility varies across devices.

Raw Bug Reports and raw Root output are not sent to model providers. Online requests send selected summaries and permitted conversation content directly to your configured provider. Review attachments before sending. See the [privacy summary](docs/PRIVACY.md#english-summary).

## Feedback and contributions

[Report a problem](https://github.com/lzh2034855417-beep/Android-Personal-Agent/issues/new/choose) with the phone model, Android version, APA version, reproduction steps and sanitized screenshots. **Do not post full Bug Reports, API keys or unique device identifiers.**

APA is maintained as a personal open-source project. [Contributing](CONTRIBUTING.md) · [Build instructions](docs/DEVELOPMENT.md) · [Documentation](docs/README.md) · [Roadmap](ROADMAP.md)

To build a published APK's source, check out its release tag. The default and development branches may differ from the installed release. [MIT license](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)
