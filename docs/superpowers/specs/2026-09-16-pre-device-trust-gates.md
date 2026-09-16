# Pre-device trust gates

## Goal

Before the v0.2.0 device matrix, make the Agent page represent actual capabilities and actual data disclosure instead of treating a user-selected label as proof of authorization.

## Requirements

- Level 0 is always available.
- Level 1 is unavailable until APA implements and verifies a live Shizuku connection and authorization.
- Level 2 advanced advice is available only after a Root read succeeds, not merely because an `su` binary exists.
- An unavailable or stale selected level must be reduced to Level 0 before prompt construction and diagnostic recommendation rendering.
- Local mode must not offer an application-report attachment that it does not consume.
- Online disclosure must name the level report, optional application/usage reports, selected system diagnostic, and bounded conversation history. It must explain that Root raw text is excluded while derived Root fields may be included.
- Level 0 cloud output must reject Magisk, LSPosed, shell/command, and the existing advanced-action terms without erasing unrelated safe sentences on the same line.

## Non-goals

- Implementing Shizuku binding or authorization in this batch.
- Redesigning the whole Agent screen.
- Changing Root command allowlists or adding automatic system actions.
