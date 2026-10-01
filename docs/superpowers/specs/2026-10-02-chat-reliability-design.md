# Chat reliability

The user approved version alignment, invalid telemetry handling, conversation persistence, and stop/retry controls. The actual source repository is D:/AndroidPersonalAgent, commit 7756a7b (0.3.0); work/stability is an obsolete snapshot. Work in an isolated clone, work/current, on codex/chat-reliability.

- Keep 0.3.0 / versionCode 5; correct README source-version claims without asserting a remote release exists.
- Invalid RAM/storage readings already have guards in both languages; run their regression tests rather than repeat the implementation.
- Store up to 100 recent messages in a versioned, bounded binary archive encrypted with Android Keystore AES/GCM in noBackupFilesDir. Preserve role, provider, advice scope, source and attachment label. Do not store credentials, attachment bodies, raw diagnostics, or retry callbacks. Keep seven days from the last save; corrupted/unsupported/expired archives recover to an empty conversation with a visible notice. Clear removes the archive. Never fall back to plaintext. Serialize writes and finish them even when UI composition is disposed.
- Restore once per ViewModel. Disable sending while restoring. Make save failures visible; do not claim persistence when saving fails. Disk history contains displayed text, which may include device details; explain that in the UI and README.
- Stop cancels the coroutine, closes its active HTTP connection, invalidates its generation, and immediately unlocks the UI. A late completion must not enter the new conversation. Cancellation cannot revoke processing already accepted by a service.
- Retry only the most recent failed/interrupted question, including restored displayed questions. Route it through the existing preflight and current attachments, re-sample current device data, and revalidate provider/key. Replace the failed turn rather than duplicating its question. Do not persist retry callbacks or silently restore old attachments. Do not auto-retry paid requests.
- Preserve Android API 26 support, English/Chinese UI, current provider/scope history isolation, existing diagnostic bounds and response validation.

Verification: failing behavioral JVM tests before new implementations; full JVM suite, debug APK and lint. No live provider requests or release signing. Emulator/device validation is reported separately.
