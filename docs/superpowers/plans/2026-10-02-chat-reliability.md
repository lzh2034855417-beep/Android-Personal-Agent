# Chat Reliability Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; the user has authorized implementation. Preserve progress in progress.md.

**Goal:** Persist chat locally and support stop/retry while correcting stale version documentation.
**Architecture:** Bounded encrypted archive and a cancellable per-request connection handle; existing ViewModel generations prevent late writes. Existing chat routing handles retry against current evidence.
**Tech Stack:** Kotlin, Compose, Android Keystore, Java binary streams, JUnit, Gradle.
**Spec:** ../specs/2026-10-02-chat-reliability-design.md

## Global Constraints

- Android API 26; no new dependencies, live API requests or release signing.
- No plaintext history fallback or raw report/credential persistence.
- Existing provider and advice-scope partitions remain intact.

## Review Focus

- Stop during connect/read must release the UI and ignore late callbacks.
- Cancel before connection registration must prevent the request.
- Clear followed by activity disposal must not resurrect disk history.
- Unsupported/truncated archives must not restore partial sensitive history.
- Retry after changing attachments/provider must use current choices without duplicating a user turn.

### Task 1: Version and telemetry verification
- [ ] Verify real source commit and clean baseline; correct README version claims.
- [ ] Run TelemetryBoundaryTest; existing guards satisfy the accepted requirement.

### Task 2: Encrypted history
Files: agent/ConversationArchive.kt, agent/ConversationStore.kt, MainSessionViewModel.kt, MainActivity.kt, ConversationArchiveTest.kt, ConversationStoreTest.kt.
- [ ] RED: round-trip provider/scope; invalid version/truncation/oversized count/field; expiry; encryption failure retains previous archive; clear deletes.
- [ ] GREEN: bounded binary codec, AES/GCM disk gateway with atomic writes, serialized lifecycle-independent saves and single restore.
- [ ] Integrate restore/loading/save error UI and clear. Full JVM suite.

### Task 3: Cancellation and retry
Files: agent/CloudRequestCancellation.kt, CloudLlmProvider.kt, MainActivity.kt, AgentRetryPolicy.kt, cancellation/retry JVM tests.
- [ ] RED: cancelled registration disconnects; active connection disconnects once; terminal response cannot append after interruption; retry selects only last failed user turn and drops its error without duplicate question.
- [ ] GREEN: per-request handle, stop UI, current-evidence retry via existing send routing.
- [ ] Full JVM suite, assembleDebug and lintDebug; review diff and document verification limits.
