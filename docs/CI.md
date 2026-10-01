# Android continuous integration

`.github/workflows/android-ci.yml` runs on pull requests and pushes. Its two independent Ubuntu 24.04 jobs provide separate check results; instrumentation does not depend on lint or JVM tests succeeding.

The checks job runs `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:lintRelease`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest`. Gradle's `--continue` attempts independent tasks after a failure while retaining a failed job result. APKs are the debug preview and its test APK, signed with the normal generated debug key. CI never requests release packaging, supplies signing secrets or accesses a release keystore. The existing release signing guard remains in the app build script.

The instrumentation job builds its own APKs and runs `:app:connectedDebugAndroidTest` on one headless Google APIs Android 16 / API 36 x86_64 emulator. It checks `/dev/kvm`, grants the runner access using an ACL, and requires acceleration. Boot has a five-minute limit, instrumentation has a twenty-minute limit, and the overall jobs have 35/45-minute limits. It disables animations for Compose tests and collects emulator output, device inventory and logcat before stopping the emulator. This checks one Android runtime; it is not a full minSdk-to-targetSdk device matrix and does not verify root or Shizuku on a physical device.

Reports and emulator diagnostics are uploaded with `always()` and retained for 14 days; APKs are retained for 7 days. Missing output is a warning because setup/build failures may precede report creation. A cancelled or forcibly terminated runner may not finish cleanup or artifact upload. Superseded runs of the same pull request or branch are cancelled. The token has only `contents: read`, checkout does not persist credentials, and no publishing or remote-message steps run. Pull requests use `pull_request`, not a privileged `pull_request_target` execution. No build scans or dependency submission are configured.

## Verified toolchain and sources (2026-10-02)

Repository inputs are AGP **9.3.1**, the checksum-protected Gradle wrapper **9.5.0**, compile SDK **36.1**, target SDK **36**, and min SDK **26**. CI uses **Temurin JDK 21**. AGP 9.3 requires Gradle 9.5.0 and at least JDK 17, with build tools 36.0.0. The official release notes also record a JDK 17 lint crash fixed in AGP 9.3.2 (`List.removeLast()`); JDK 21 avoids that documented runtime mismatch while preserving this repository's AGP version. The Gradle Java compatibility table supports running Gradle 9.5 on Java 21.

- [AGP 9.3 compatibility and patch fixes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html): Java 21 is supported for running Gradle 8.5 and later.
- [Official Ubuntu 24.04 runner inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md): Android SDK environment, platform `android-36.1`, build tools `36.0.0`, command-line tools and ACL utility.
- [Android sdkmanager documentation](https://developer.android.com/tools/sdkmanager): install syntax, package listing and licenses. The workflow explicitly installs `platforms;android-36.1`, `build-tools;36.0.0` and `platform-tools` rather than relying only on preinstalled versions.
- [Google Chromium Android 16 emulator configuration](https://chromium.googlesource.com/chromium/src/+/475183bbe7445a0e737eb06e4d87caf4a8480b7a/tools/android/avd/proto/android_36_google_apis_x64.textpb): authoritative use of `system-images;android-36;google_apis;x86_64`. Compile platform 36.1 and runtime image 36 are intentionally separate SDK packages.
- [Android emulator command-line options](https://developer.android.com/studio/run/emulator-commandline) and [acceleration on Linux](https://developer.android.com/studio/run/emulator-acceleration): headless startup, graphics, acceleration and KVM.

All actions are maintained by GitHub and pinned to complete commit SHAs verified through the official release page's commit link:

| Action | Release | Commit |
| --- | --- | --- |
| [actions/checkout](https://github.com/actions/checkout/releases/tag/v6.0.2) | 6.0.2 | `de0fac2e4500dabe0009e67214ff5f5447ce83dd` |
| [actions/setup-java](https://github.com/actions/setup-java/releases/tag/v6.0.0) | 6.0.0 | `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` |
| [actions/upload-artifact](https://github.com/actions/upload-artifact/releases/tag/v6.0.0) | 6.0.0 | `b7c566a772e6b6bfb58ed0dc250532a479d7789f` |

The release labels are explanatory; SHA pins control execution. Package revisions, JDK patch releases and runner images can evolve even with action pins. Review official release/commit provenance when updating pins. No third-party emulator setup action or additional local dependency installation is required.

## Validation and limits

The emulator explicitly enables the soft keyboard even with a virtual hardware keyboard so the IME visibility test cannot silently skip keyboard behavior. Stability tests reset their Preview conversation and language before each test; do not run them against a Preview installation whose chat history you need to keep.

Local validation passed static assertions for triggers, least permissions, independent jobs, the six requested Gradle tasks, absence of secrets/release packaging and complete action SHA pins. Git Bash `bash -n` passed for all five multiline run blocks, and `git diff --check` passed. Report paths and the app's release packaging guard were inspected. No existing YAML parser or `actionlint` was available in the local or bundled runtimes, so YAML parsing and GitHub expression/schema validation were not run; no parser dependency was installed. These checks do not substitute for GitHub's workflow engine or an emulator run on a hosted Linux runner. Local JVM/lint/APK and device test results are recorded separately in the session's aggregate verification report.

No push, GitHub workflow dispatch or hosted CI execution was performed as part of adding this workflow. Actual Linux package downloads, KVM access, emulator boot, connected test execution and artifact upload remain unverified until an authorized push or pull request runs the workflow. An SDK setup failure should be investigated using `sdkmanager --list` on that runner; a passing APK build alone does not establish that instrumentation passed.
