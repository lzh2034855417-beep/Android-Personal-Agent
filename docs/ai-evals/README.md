# Offline bilingual AI response evaluation / 离线双语回答评估

This kit screens user-supplied answers to 14 fixed synthetic cases (seven scenarios, each in Chinese and English). It does not call a provider, access a network, read API keys, change the Android app, or judge semantic accuracy automatically. Reports and all app/service names in the cases are fictional, hand-authored data, not licensed datasets or real user records.

本工具包含 14 个固定合成用例（7 个场景，中英文各一份），只检查用户提供的回答。不会访问网络、请求模型、读取密钥或修改应用。所有报告均为手工构造，不包含真实用户数据。词法检查不是准确率评估，语义必须人工复核。

## Run / 运行

Run from the repository root with Python 3.9+; only the standard library is used. Use your local Python executable in the commands below. No additional package installation or Gradle build is required.

```powershell
python -B scripts/ai_eval/evaluate.py path/to/responses.json
python -B -m unittest discover -s scripts/ai_eval -p 'test_*.py'
python -B scripts/ai_eval/evaluate.py docs/ai-evals/fixtures/synthetic-good.json
python -B scripts/ai_eval/evaluate.py docs/ai-evals/fixtures/synthetic-bad.json
```

The good fixture is expected to exit 0 with 14 non-empty cases, zero lexical flags, and 14 manual reviews pending. The bad fixture is expected to exit 1 with 10 lexical token flags across four cases and 14 manual reviews pending. Ten deliberately incorrect answers in the bad fixture contain no forbidden tokens and remain `CLEAR`, including both overlapping-window verification answers that falsely claim causal savings; this demonstrates the need for manual review. Both fixtures are **NOT model runs** and provide no evidence about any provider's performance.

好样例预计退出码 0，覆盖 14 例、词法标记 0、人工待审 14。坏样例预计退出码 1，4 例共 10 个词法标记、人工待审 14；另有 10 个错误回答没有命中词法标记，包括两例窗口重叠却声称调整节电的因果错误。两个文件都是手工样例，**不是模型运行结果**。

Input format:

```json
{
  "model": "actual model identifier recorded by the evaluator",
  "runDate": "2026-10-02",
  "rawResponses": {
    "invented-attribution-zh": "Unedited raw answer",
    "invented-attribution-en": "Unedited raw answer"
  }
}
```

The example above is incomplete and exits 1; supply every id from `cases.json` for full coverage. Values must be strings. `model` must be non-empty and `runDate` a valid ISO date. Duplicate JSON keys and invalid case configuration are rejected. `--cases path/to/cases.json` can select another synthetic configuration with the same schema. Each case supplies its report, question, capability level, intention, human-reviewed required facts, and an explicit list of forbidden invented tokens.

Exit codes: 0 = complete/non-empty known-id coverage and no configured lexical hits; 1 = missing/empty/unknown ids or lexical hits; 2 = unreadable/malformed JSON or invalid configuration/metadata. Output is JSON on stdout, including friendly errors without a Python traceback. `coverage.provided` counts known ids only; unknown ids are reported separately. `manualPending` always counts all configured cases, including missing answers. The CLI does not ingest manual verdicts or turn them into automatic PASS.

退出码：0 仅表示词法筛查及覆盖检查通过；1 表示缺失、空白、未知 id 或词法命中；2 表示文件、JSON 或配置错误。`manualPending` 始终包含所有配置用例，即使回答缺失也仍待审。

## Capture protocol / 采集协议

For an authorized real run, preserve the exact report/question, language, level and intention, model identifier, run date, generation settings, and app version alongside the answers. Also capture the exact system prompt, final model-bound prompt/messages after all app transformations, and conversation history supplied to the model. A case report/question alone does not reproduce the application's actual prompt pipeline; record these artifacts to establish which pipeline was evaluated. This CLI only reads saved answers; it does not execute that run. Test each language independently using its own case context. Do not provide expected facts or forbidden-token lists to the model as part of the question: these are review material.

Capture **raw provider output and displayed app output separately**, linked by case id. Put untouched raw output in `rawResponses`; save the displayed output in a separate companion file (for example, `displayed-responses.json`), and record the sanitizer/app version and any difference. Never replace raw output with sanitized display text. Screen raw answers first and manually assess both artifacts: display sanitization may hide an unsafe provider answer and does not make that raw answer safe. Review Markdown/rendering changes or truncated output as part of displayed-answer review. Neither stage has been tested on a provider by these fixtures.

真实运行需获得相应授权，并保存报告/问题、语言、等级、意图、模型、日期、生成参数及应用版本，还需记录精确系统提示词、经应用转换后最终发送给模型的提示词/消息以及实际提供的对话历史。仅保存用例报告和问题无法复现应用的完整提示词流程；这些记录用于确认评估的是哪条实际流程。模型输入不要包含评分答案或禁止词列表。**原始模型回答与界面显示回答分别保存**，通过 case id 关联；`rawResponses` 必须保留未经清洗的原文。另存显示文本及清洗器/应用版本，人工比较两者。界面清洗可能掩盖原始回答风险，不能只用显示结果替代原始评估。

## Lexical limits / 词法限制

Verification cases must capture the independent explanation payload and fixed system prompt, with one untrusted user message and no chat history. Evaluate raw and displayed model answers against the local gate, actual/estimated labels, unknown side effects and independently verifiable windows. A lexical CLEAR result never upgrades NOT_COMPARABLE or establishes causal savings. No live verification evaluation was performed.

验证用例须记录独立解释 payload、固定 system 提示词及单条不可信 user 消息（无聊天历史），人工核对本地门控、实际/估算、未知副作用及独立窗口。词法 CLEAR 不能改变 NOT_COMPARABLE，更不能证明因果节电；未运行真实模型验证评测。

The only automatic answer-content checks are case-insensitive literal substring matches against `forbiddenHallucinatedTokens`. These are deliberately specific invented names or unsupported exact-number strings, not general words such as “kill”, “Root”, “health”, “background”, or “abnormal”. Empty token lists are intentional for semantic-only cases. Required facts are included in the output for the reviewer, never enforced by substring matching. No accuracy percentage or semantic PASS is emitted.

Negative disclaimers such as “I cannot kill apps or read kernel wakelocks” / “当前无法终止进程或读取内核唤醒锁” must be acceptable. The checker does not ban these terms. A quoted/negated forbidden invented token may still trigger a lexical flag; a human must inspect context rather than treat that hit as a proven hallucination. Conversely paraphrases, different numbers, translated names, omission, contradictory statements and unsupported capability claims can evade the screen. This is a small regression set, not an exhaustive benchmark, causal diagnosis or safety certification.

自动检查仅做禁止词列表的忽略大小写字面子串匹配，不对必需事实做关键词“判对”，也不封禁否定性能力说明。引用或否定一个特定禁止词仍可能被标记，须人工判断上下文；改写、其他数字、遗漏和语义矛盾可能漏检。详见 [人工评分准则](manual-rubric.md)。
