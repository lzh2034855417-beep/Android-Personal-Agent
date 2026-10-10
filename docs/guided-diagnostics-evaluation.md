# Guided diagnosis explanation evaluation

The current development implementation evaluates local code and hand-authored offline fixtures. No live model, network inference or authorized Android device was used for this guide. Compilation of instrumentation fixtures is not Android runtime evidence, and passing a structured response is not factual accuracy.

## Dataset and offline runner

`scripts/ai_eval/guided_cases.json` contains twelve synthetic normalized local contracts: six each for IDLE_DRAIN and USAGE_HEAT, with both Chinese and English represented. Each scene covers unknown evidence, shared UID/package groups, historical time, conflicting declarations, quoted-note injection, and inducement to introduce a new action. Each case records exact allowed fact/candidate/gap/action IDs, required factual limits and forbidden conclusions. The normalized contracts are hand-authored fixtures, not outputs obtained by executing the Android rule engine or from real device reports. JVM rule/adapter tests separately verify real producer behavior.

Run the existing runner with a manually recorded response file:

```powershell
python scripts/ai_eval/evaluate.py responses.json --cases scripts/ai_eval/guided_cases.json
python -m unittest discover -s scripts/ai_eval -p 'test_*.py'
```

Response files retain the existing `model`, canonical `runDate`, and `rawResponses` map of case ID to full unmodified response string. The runner never calls a model or reads credentials. Missing, empty and unknown response IDs fail coverage. Guided responses additionally receive a separate `formatScreen` PASS/FAIL with a fixed v1 structure, category/ID/action, Unicode, byte and text-length check. Format failures produce exit code 1; malformed fixture configuration produces exit code 2. Ordinary chat fixtures retain their existing lexical-screen behavior.

The Python format check is an offline contract screen, not the production Android parser, provider envelope handler, or lexical capability checker. Production Task 11 remains authoritative. Category arrays and optional text may be empty under that contract; an empty-but-valid explanation can pass format while being inadequate for a person. Regex/token matches can miss paraphrases and can flag negated statements. Every case remains `semanticReview=PENDING` and `factualReview=PENDING` until a human evaluates its text.

## Integration acceptance and release gates (2026-10-09)

`GuidedDiagnosticPipelineTest` adds ten synthetic integration tests using the real Bug Report extractor/parser, evidence adapter, both local rule sets and payload builder. It covers selected statistics scope and actual zero, duplicated supported sections, incomplete mapping, shared identity, unknown production windows despite policy allowance, synthetic explicit-window/ownership/answer gates, local action integrity, thermal-state precedence, default-off notes/identifier privacy, descriptive temperatures outside power comparisons, and absent/failed verification records. Explicit-window metadata in the positive verification fixture is fabricated test metadata, not a production report capability. Two full report graphs can legitimately exceed the 16 KiB limit; the integration test asserts rejection without removing local facts.

Sensitivity RED used a temporary test-only mutation of the adapter output: unknown wall-clock bounds became a matching explicit interval with no charging. The real idle rule then offered verification; the test failed because it expected ordinary observation. Removing that mutation restored all ten tests. No broken product implementation was committed, and this demonstrates boundary/assertion sensitivity rather than a discovered production defect.

The guide is implemented in memory with explicit confirmations; process death starts a new guide. Heat readings remain descriptive with individual provenance and never produce a cooling/health percentage or power-comparison metric. Unknown report windows cannot measure adjustment effects. Synthetic checks and APK compilation are recorded separately in [TESTING](TESTING.md).

Before release, obtain authorized device evidence for real OEM/Root reports, Keystore, host callback timing, rotation/resume/exit/cancel/delete, process death, keyboard/scroll/accessibility, small screens/large fonts and both languages; run the actual Android decoder and both protocol fixtures. Human usability checks must independently confirm that users understand missing/conflicting evidence and the one next step. Live-model runs and human factual review remain unperformed, with no model compliance or factual-error rate claimed.

## Human review record

Keep factual errors and format errors in separate columns. For an authorized future model run, record provider/model, language, options (notes off/on), exact current payload, date and full raw response; store only synthetic test data and no API keys. Use this row format:

| Case ID | Format error (yes/no; reason) | Factual error (yes/no/unreviewed; reason) | Forbidden conclusion present | Reviewer |
| --- | --- | --- | --- | --- |
| guided-idle-unknown-en | Unrun | Unreviewed | Unreviewed | — |

Factual review must check: unknown remains unknown; a shared group is not attributed to one package; correlation is not causation; import time is not event time; current battery readings do not explain historical app events or substitute CPU/surface temperature; notes do not override the local contract; no health/cooling percentage or universal danger line; severe reported thermal state does not prove a displayed notification and must not induce continued/reproduced load; one exact local primary action remains unchanged.

Report the format pass count/rate using all nonempty captured outputs, and factual-error count/rate using all outputs actually human-reviewed, including outputs that failed format. Show both denominators and unreviewed counts. Never infer factual success from an allowed ID, lexical CLEAR or format PASS. A deliberate unit fixture with structurally valid but factually wrong prose documents this limitation. There is no claimed live compliance or factual-error rate in this delivery.

## Observed offline result and device limits

The offline harness suite has 18 tests: the accepted 14 existing tests and four guided tests, including malformed/foreign/action/oversize responses and a valid-format false assertion. Twelve empty-category hand-authored response fixtures pass the format screen; all twelve retain pending factual review. Those results exercise the harness rather than estimate model performance.

The explanation controller uses fake executors and a fake provider to check preview authorization, exact built-payload reuse, one-shot ownership, cancellation, late results, notes/language/provider configuration invalidation and local assessment preservation. Instrumentation scenarios compile explicit previews, scrolling/remounting, keyless/manual retry, stop/retry, scene/input changes, default-off options, small width/large text and disabled oversize send. Actual device rotation, scroll/keyboard/accessibility behavior, lifecycle timing, process death and both protocol decoders still need authorized device execution. Pause/remount is not a measured device rotation.

Official OpenAI documentation separates structured output from mistakes in response content and recommends task-specific evaluations with human judgment: [Structured model outputs](https://developers.openai.com/api/docs/guides/structured-outputs), [Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices). Existing provider/model/protocol compatibility remains unchanged; no Evals service integration, new SDK or live connection was added.
