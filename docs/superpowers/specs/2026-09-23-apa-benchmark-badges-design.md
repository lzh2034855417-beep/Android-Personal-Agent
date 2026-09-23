# APA Benchmark Badges Design

Date: 2026-09-23
Status: approved concept, implementation design for review

## Intent

Turn APA's share card into a result people want to show: a familiar bronze, silver, or gold badge backed by a short, local, reproducible benchmark. Flagship phones must still differ through an APA score, one-to-three stars, dimension scores, and a positive specialty title.

The feature must work without Root. A successful Root profile read may add a `ROOT VERIFIED` ribbon and extra technical context, but Root never raises the score.

## Goals

- Produce a complete result in about 15 seconds on a healthy modern phone.
- Keep all workloads and scoring local and offline.
- Make results comparable only when they share the same score version.
- Show the scoring evidence instead of presenting an unexplained rank.
- Preserve the existing dark share-card style and preview-before-share flow.
- Avoid false claims about component suppliers, national rankings, charger power, or real-world game frame rate.

## Non-goals

- No friend battle, QR comparison, leaderboard, account, or backend.
- No inference of RAM generation, storage vendor, panel supplier, or component batch.
- No automatic background benchmark.
- No use of installed-app lists or private user data.
- No benchmark result included in cloud prompts unless a future feature asks for it explicitly.

## User experience

The device overview keeps one primary card. Before a run it shows the device name, a short explanation, and `运行 APA 测试 · 约 15 秒`. The benchmark runs only after a tap.

During a run, the card shows the current phase and progress: warm-up, CPU, memory, storage, and stability. Navigation away cancels cleanly. The test keeps temporary data inside the app cache and deletes it on success, cancellation, or failure.

After a run, the card shows:

- metal badge and stars;
- APA score from 0 to 100;
- specialty title;
- CPU, memory, storage, and stability dimension scores;
- concise raw evidence such as multi-core throughput, memory throughput, storage read/write, and three-round decline;
- model, RAM/storage capacity, refresh rate, sample time, and score version;
- `ROOT VERIFIED` only when the attached device profile was successfully collected through Root.

Example:

```text
Xiaomi 17 Pro Max
顶级金标 ★★ · APA 92
极限爆发型
CPU 96 · 内存 89 · 存储 94 · 稳定性 82
三轮衰减 4.2% · 电池温升 3.1°C
16 GB + 512 GB · 120 Hz
APA Score v1 · 本机离线测试 · ROOT VERIFIED
```

Unavailable optional evidence is omitted instead of filling the card with `未获取到` lines. A failed required phase produces no badge and keeps the previous successful result visible with an explicit failure message.

## Benchmark protocol

Every timed phase performs an untimed warm-up first. Monotonic elapsed time is used. Workloads return a checksum consumed by the caller so the runtime cannot discard the work.

1. **CPU, 35 points.** A deterministic integer-mixing workload measures single-worker and multi-worker operations per second. Multi-worker count is capped at eight to avoid rewarding unusually high logical-core counts without bound. CPU dimension weight is 15 points single-worker and 20 points multi-worker.
2. **Memory, 20 points.** Repeated copy and transform operations over reusable 32 MiB byte arrays measure effective throughput. Allocation and garbage collection are kept outside timed sections.
3. **Storage, 20 points.** A 64 MiB file in the app cache is written sequentially, synced, then read sequentially. Write and read contribute 10 points each. The run refuses this phase when safe free-space headroom is unavailable.
4. **Stability, 15 points.** The multi-worker CPU workload runs three equal rounds. The score uses the final-round throughput divided by the best-round throughput. Battery-temperature change is displayed as context but does not alter the score because battery temperature is not SoC temperature.
5. **Configuration, 10 points.** RAM capacity contributes up to four points, storage capacity up to three, and maximum supported display refresh rate up to three. Capacity is bucketed using marketing-size tolerance so a nominal 12 GB or 512 GB device is not penalized for reserved/system space.

The benchmark records charging state, battery level, battery temperature, and whether Android reports low memory. These are run conditions, not secret score multipliers. The UI warns when low-memory state or charging may reduce comparability.

## Scoring and labels

Each performance measurement is converted to 0-100 by a versioned, monotonic normalization curve in `ApaScoreProfile.V1`. Dimension scores are weighted into the 0-100 APA score using the points above. Values above the V1 upper reference are capped at 100; weak results remain distinguishable rather than being rounded to zero.

V1 reference constants are fixed in source after calibration on the connected test phone and the existing deterministic fixtures. Changing workloads, weights, or reference constants requires a new score version. Cards always print the score version; APA never compares different versions as equivalent.

Metal labels:

- 85-100: `顶级金标`
- 65-84: `高级银标`
- 0-64: `实用铜标`

Gold stars:

- 85-89: one star
- 90-94: two stars
- 95-100: three stars

Silver and bronze show no stars in V1. The specialty title comes from the strongest dimension only when it leads the next dimension by at least five points:

- CPU: `极限爆发型`
- memory: `内存疾速型`
- storage: `存储闪电型`
- stability: `冷静持久型`
- otherwise: `全能均衡型`

## Architecture

- `benchmark/ApaBenchmarkModels.kt`: immutable run conditions, raw measurements, dimension scores, final badge, and score-version models.
- `benchmark/ApaBenchmarkWorkloads.kt`: isolated CPU, memory, and storage workloads with injected clock/executor/files for tests.
- `benchmark/ApaBenchmarkRunner.kt`: phase orchestration, cancellation, cleanup, progress, and result assembly.
- `benchmark/ApaScoreCalculator.kt`: pure normalization, weighting, metal/star assignment, capacity tolerance, and specialty-title selection.
- `benchmark/ApaBenchmarkStore.kt`: persists only the latest successful result as versioned JSON in private app preferences. Corrupt or incompatible data is ignored.
- `QuickReportPanel.kt`: renders pre-run, progress, result, and error states; builds the share-card model.
- `QuickReportCardRenderer.kt`: renders the same share-card model used by the Compose preview so screen and PNG content cannot diverge.

`MainActivity` supplies device identity, RAM/storage capacity, display refresh, battery conditions, and the optional Root-backed profile. It does not contain benchmark calculations.

## Failure and safety behavior

- Cancellation and exceptions always delete the temporary storage file.
- A storage test never writes outside the app cache.
- A run checks free space before allocating/writing and fails with a useful message when unsafe.
- The previous successful result is not overwritten by a failed or cancelled run.
- Results older than seven days remain viewable but are marked `历史成绩`; users must rerun before creating a fresh share card.
- The run does not claim to be a laboratory benchmark. The result screen states that background load, temperature, power mode, and charging can affect measurements.

## Testing

- Pure unit tests cover normalization boundaries, weights, metal thresholds, stars, title selection, capacity tolerance, version mismatch, and missing optional fields.
- Workload tests cover deterministic checksums, nonzero throughput, worker caps, cancellation, low-space refusal, and temporary-file cleanup.
- Runner tests use fake workloads and a fake clock to cover phase order, progress, success, cancellation, and preservation of the previous result on failure.
- Renderer tests cover omission of unavailable fields and inclusion of model, score version, evidence, and Root ribbon only after verified Root collection.
- Android UI tests cover initial CTA, progress state, result state, preview, and rerun.
- Physical-device acceptance requires three consecutive runs with no crash or leaked temporary file and a total-score spread no greater than five points under similar conditions.

## Release boundary

The first release is explicitly `APA Score v1`. It ships without leaderboards or population percentiles. The score and badge are configuration-and-workload references, not an absolute statement of device quality. README and the in-app explanation publish the weights, conditions, and versioning rule.
