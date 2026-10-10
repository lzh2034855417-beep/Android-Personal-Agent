# 待机掉电与使用发热诊断向导 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付两种无 Key 可用的问题诊断流程，明确证据、缺口和唯一主动作，并提供隔离的可选 AI 解释。

**Architecture:** 修复现有证据与存储边界，再通过有类型摘要适配现有采集、导入和续航观察。两个纯 Kotlin 规则集共用会话和 UI，由本地规则决定事实与动作，模型只能补充解释。热观察仅在会话内作描述性并列，不扩展现有耗电比较引擎。

**Tech Stack:** Kotlin、Jetpack Compose、ViewModel、现有 Gradle wrapper、JUnit、Android 仪器测试；沿用已有加密存储和云端传输，不增加依赖。

**Spec:** `docs/superpowers/specs/2026-10-03-guided-diagnostics-design.md`（用户已确认）。

## Global Constraints

- 代码基线 `b4bd6cd`；用户已确认本计划，正在逐项实施和审查。工作目录为已核对的隔离副本 `C:/Users/ROG/Documents/APA/work/current`；不直接覆盖 `D:/AndroidPersonalAgent`。
- 每会话最多两份已确认摘要、每份最多二十个应用组，每条备注最多五百个 Unicode 码点；结构化证据白名单序列化后每份上限 48 KiB。
- 待验证解释最多三项；一次只展示一个主动作；无可靠动作时结束，不编造结论。
- 载荷不超过 16 KiB UTF-8，超限阻止发送，不静默截断。自由备注默认关闭；没有普通聊天历史、原始报告或唯一设备标识。
- 第一版向导仅保存在页面级 ViewModel 内存中，配置变化保留，进程死亡后重新开始；不新增独立持久化归档。
- 不做后台监控、自动采样提醒、自动系统修改、压力测试、新权限、新服务商、通用温度危险线或健康百分比。
- 所有界面、规则、拒绝理由与错误支持中文和英文。未知保持未知；事实值和主动作由本地渲染。
- 延续用户选择：每项由子代理实施，经独立规格审查和代码审查通过后推进；最后做整体验收。共享文件的任务串行，不让实施代理自审代替独立审查。
- 每项先失败测试再实现；修复审查意见后重跑相关测试。只提交本任务文件；作者身份未配置时保留变更并报告，不伪造身份、不改全局配置、不推送。

## Review Focus

1. 未知统计标题、重复区段及实际零值：数值与窗口同源，不从后续段落补值；Task 1。
2. 清空已返回成功但旧任务尚未进入存储层：不能复活记录，重试／放弃不能误删已保存版本；Task 3。
3. Unicode 代理对、单独代理字符与中文 UTF-8 字节膨胀：不产生非法编码或绕过容量门控；Tasks 4、6、11。
4. 报告来自过去／其他设备、用户修改回答、相同输入重复点击：不能静默换样本、重复操作或接收旧模型结果；Tasks 6、9、10、12。
5. 仅有体感、单位不明、热状态未知及已发生系统警告：不虚构温度或诱导继续负载；Tasks 8、10、11。

## 文件与执行约定

以下文件路径相对仓库。缩写 `M=app/src/main/java/com/aegis/apa`、`T=app/src/test/java/com/aegis/apa`、`I=app/src/androidTest/java/com/aegis/apa`；这是精确路径前缀，不是建议搜索范围。

新纯模型放 `M/model/GuidedDiagnostic.kt`；规则、适配、会话控制与模型边界分别放 `M/agent/Guided*.kt`、`IdleDrainRuleSet.kt`、`UsageHeatRuleSet.kt`。Compose 宿主放 `M/Guided*.kt`；双语文案放 `M/localization/GuidedDiagnosticCopy.kt`。仅在必要入口修改 MainActivity，不扩展成通用工作流平台。

PowerShell 验证环境使用已有 Android Studio JBR、Android SDK 与项目 wrapper。每个测试步骤运行 `./gradlew.bat testDebugUnitTest --tests 'com.aegis.apa.<TestClass>' --offline`；期望第一次为行为断言失败，第二次为该类全部通过。新增类型尚未存在时编译失败仅是第一轮红灯，补出最小接口后应确认行为测试确实会失败。本地依赖缺失时记录阻塞，不把下载失败当功能失败。

Android 测试编译：`./gradlew.bat assembleDebugAndroidTest --offline`。有用户授权的设备时才运行 `./gradlew.bat connectedDebugAndroidTest --offline`，否则记录“未运行”。不自动连接真实模型、不安装或发布 APK。

每项末尾的提交必须先通过独立审查；检查点包括接口兼容、现有测试回归及无范围外改动。Tasks 1–5 是前置修复，可独立交付；Tasks 6–10 构成同一向导的本地闭环；Tasks 11–12 是可选解释扩展，因此共用一份计划，不并行另建重复框架。

### Task 1：耗电数值与窗口保持一致，保留真实零值

**Files:** 修改 `M/tool/BatteryStatsEvidenceParser.kt`、`M/tool/DiagnosticWindowMetadataParser.kt`；新建 `M/tool/BatteryStatisticsSection.kt`；测试 `T/BatteryStatsEvidenceParserTest.kt`、`T/DiagnosticWindowMetadataParserTest.kt`、`T/PowerDiagnosticPipelineTest.kt`。

**Interfaces:** 保持 `parseDrainWindow(output: String): BatteryDrainWindowEvidence?` 和元数据解析接口；新增 `BatteryStatisticsSection.sinceLastCharge(output: String): String?`，唯一匹配区段止于下一条 Statistics 标题；重复匹配返回 null。

- [ ] 写测试 `drainCannotBorrowLaterActualValue`：首段 1h/computed=100、后段 30m/actual=12，断言首段 100、estimated、3600000ms；`zeroActualWinsOverComputed` 断言 actual=0/computed=10 返回 0、actual；`zeroComputedIsPresent` 断言 computed=0 不为空。补重复标题、未知后续标题、负数、NaN、Infinity；容量和时长仍要求正有限值。
- [ ] 运行上述三个测试类，确认现有串值和零值断言失败。
- [ ] 实现共享区段选择器，两条解析路径共用它；actual/computed 允许有限非负值，不跨段回退。检查应用指标解析是否也越过区段，若有同类问题在该任务补真实输入测试并限制到对应区段。
- [ ] 重跑三个类；确认生产窗口起止仍为未知，没有用 duration 推算时间。
- [ ] 独立审查后提交 `fix: preserve battery statistics provenance and zero drain`。

### Task 2：包名归属完整性参与应用指标门控

**Files:** 修改 `M/agent/VerificationSummaryProjector.kt`，必要时修改 `M/tool/PowerDiagnosticParser.kt` 的映射覆盖状态；测试 `T/VerificationSummaryProjectorTest.kt`、`T/PowerDiagnosticPipelineTest.kt`。

**Interfaces:** 保持 `VerificationSummaryProjector.project(...)` 现有接口；数值来源与 `packages` 来源／`UID_PACKAGES` 覆盖共同决定应用指标状态，整体耗电不依赖应用映射。

- [ ] 写 `truncatedMappingCannotProduceParsedAppMetric`：从 RawDiagnosticSection 经真实解析到 projector，共享 UID 映射仅剩 A，断言 A 不为 PARSED；整体数值仍保留。补映射缺失／失败、完整 A+B 映射、重复 UID、数值来源截断。
- [ ] 运行两个测试类，确认不完整映射测试失败。
- [ ] 合并身份和数值覆盖门控；完整共享 UID 保持一个组，不复制／均摊耗电；未知身份不能被用户声明修复。
- [ ] 重跑测试，检查现有应用组上限和目标应用保留逻辑不变。
- [ ] 独立审查后提交 `fix: gate app evidence on identity completeness`。

### Task 3：清空屏障与保存失败恢复

**Files:** 修改 `M/agent/VerificationStore.kt`、`VerificationController.kt`、`M/VerificationViewModel.kt`、`VerificationPanel.kt`、`VerificationRecordDetail.kt`；测试 `T/VerificationStoreTest.kt`、`VerificationControllerTest.kt`、`I/VerificationPanelUiTest.kt`。

**Interfaces:** 新增 store 的不透明 `VerificationMutationToken`、`mutationToken(): VerificationMutationToken`、`upsert(record: VerificationRecord, token: VerificationMutationToken): VerificationLoadResult`；所有写调用迁移，不能保留自动捕获新 token 的生产旁路。controller 和 ViewModel 新增 `retrySave(id: String, expectedRevision: Long)`、`discardUnsaved(id: String, expectedRevision: Long)`。

- [ ] 写 `queuedCreateCannotSurviveOtherControllerClear`，用可控 executor 断言清空后旧任务执行仍为 0 条；写 `failedCloseCanRetrySameCandidate`、`discardFailedCloseRestoresPersistedRecord`，临时制造 pending 路径不可写后恢复，断言 retry 保存 CLOSED、discard 恢复已保存态。补过期 revision、双击 retry、删后 retry、失败首次创建后 discard。
- [ ] 跑两个 JVM 类确认失败，并为未保存 CLOSED 状态编写可见重试／放弃按钮 UI 测试。
- [ ] 在操作入队时捕获共享 token，锁内验证；失败候选可重试，不重新走 CLOSED 状态转移。放弃只清候选，不删除已保存版本。清空成功才推进屏障；重复请求不产生额外 revision。
- [ ] 重跑 JVM 并编译 UI 测试；确保失败／保存中／已保存文案不混淆。
- [ ] 独立审查后提交 `fix: make verification writes recoverable and clear-safe`。

### Task 4：Unicode 安全文本边界

**Files:** 新建 `M/model/BoundedUnicodeText.kt`；修改 `M/model/DiagnosticVerification.kt`、`M/VerificationForms.kt`；测试 `T/VerificationModelTest.kt`、`VerificationArchiveTest.kt`、`VerificationControllerTest.kt`。

**Interfaces:** `boundedUnicodeText(value: String, maxCodePoints: Int): String`，控制字符替换为空格，孤立代理字符替换 U+FFFD，按码点限制；现有标题 80、备注 500，归档字节上限不变。

- [ ] 写 `emojiAtTitleBoundaryRoundTrips`：79 个 a 加 emoji，可保存／读取且码点数为 80；补中文、组合字符、孤立高低代理、500 码点备注和编码后归档超限拒绝。
- [ ] 跑三个 JVM 类确认原实现失败。
- [ ] 统一模型与输入框边界；不得改变归档 wire schema，不按字形簇宣称长度。
- [ ] 重跑测试及旧归档兼容用例，严格 UTF-8 编码通过。
- [ ] 独立审查后提交 `fix: bound verification text without breaking unicode`。

### Task 5：AI 解释不随列表项滚动销毁

**Files:** 修改 `M/VerificationViewModel.kt`、`VerificationPanel.kt`、`VerificationRecordDetail.kt`、`VerificationExplanationPanel.kt`，按需修改 `M/agent/VerificationExplanationController.kt`；测试 `T/VerificationExplanationControllerTest.kt`、`I/VerificationExplanationUiTest.kt`。

**Interfaces:** 现有 explanation controller 由 ViewModel 持有；panel 改为接收状态与预览／发送／停止回调。记录 ID、revision、level、language 组成请求键，不将 credentials 放入可保存 UI 状态。

- [ ] 写真实 LazyColumn 父容器测试 `scrollAwayRetainsExplanation` 和 `scrollAwayDoesNotCancelInFlightRequest`；断言滚动回来响应仍在、provider 调用只有一次；controller 测试记录切换／关闭／revision 变更使迟到回调失效。
- [ ] 先跑 controller 类，UI 在具备设备条件时确认原实现失败；否则明确记录运行缺口。
- [ ] 上移请求状态所有权；退出详情主动通知 owner，旋转不视为退出；onCleared 取消。原预览、备注开关和凭据时机保持不变。
- [ ] 重跑 controller 和 UI 编译；设备可用时重跑滚动与旋转测试。
- [ ] 独立审查后提交 `fix: retain verification explanation across lazy item disposal`。

### Task 6：向导模型、白名单摘要和证据适配

**Files:** 新建 `M/model/GuidedDiagnostic.kt`、`M/agent/GuidedEvidenceAdapter.kt`、`GuidedSummaryCodec.kt`、`T/GuidedEvidenceAdapterTest.kt`、`T/GuidedDiagnosticFixtures.kt`。

**Interfaces:** 以下是后续任务共用合同，不使用展示文案作机器判断。

- `GuidedScenario { IDLE_DRAIN, USAGE_HEAT }`；`GuidedStage { ANSWERING, NEEDS_EVIDENCE, READY, AWAITING_USER_ACTION, ENDED }`；声明复用 `VerificationConfirmation` 的 YES/NO/UNKNOWN。
- `GuidedAnswers`：`startedAt: Instant?`、`endedAt: Instant?`、`charging/mostlyIdle/rebooted/stillHot: VerificationConfirmation`、`activity: GuidedActivity`（UNKNOWN/GAME/VIDEO/NAVIGATION/CHARGING_USE/OTHER）、`note: String`；区间反转产生缺口，不自动交换端点。
- `GuidedInput(diagnostic: PowerDiagnosticSnapshot?, device: DeviceSnapshot?, observation: BatteryObservationResult?)` 为临时适配输入，不存原始报告。
- `GuidedEvidence`：`id: String`、`kind: GuidedEvidenceKind`、`value: GuidedValue?`（Number(value: Double, unit: String) 或 Code(value: String)）、`source: String`、`sourceStatus: DiagnosticSourceStatus`、`fieldStatus: EvidenceFieldStatus`、`estimate: VerificationEstimate`、`packages: List<String>`、`collectedAt: Instant`、`reportedAt: Instant?`、`window: MetricWindow?`。kind 枚举覆盖现有 VerificationMetricKind 及 BATTERY_TEMPERATURE、THERMAL_STATUS、CHARGING、OBSERVATION_RATE；不暴露任意输入字符串为指标类型。
- `GuidedSample(id: String, evidence: List<GuidedEvidence>, answersAtConfirmation: GuidedAnswers)`；用户声明只放 answers，不冒充系统 evidence。
- `GuidedEvidenceAdapter.adapt(input: GuidedInput, sampleId: String, answers: GuidedAnswers): GuidedSampleResult`；结果为 Ready(sample) 或 Rejected(reasonId)。`GuidedSummaryCodec.encode(sample: GuidedSample): ByteArray` 提供确定性白名单序列化，只用于大小检查与载荷，不创建归档。
- `GuidedCandidate(ruleId: String, evidenceIds: List<String>, gapIds: List<String>)`；`GuidedGap(id: String, evidenceIds: List<String>)`；`GuidedAction(id: String, kind: GuidedActionKind, reasonIds: List<String>)`；`GuidedAssessment(facts: List<GuidedEvidence>, candidates: List<GuidedCandidate>, gaps: List<GuidedGap>, primaryAction: GuidedAction)`。kind 为 ANSWER/IMPORT_REPORT/COLLECT/OPEN_OBSERVATION/OBSERVE_CONDITION/OPEN_VERIFICATION/END/VIEW_SYSTEM_WARNING，最后一项是系统提示导航而非新采集能力。

- [ ] 写 `adapterPreservesProvenanceAndBounds`，断言零值保留、有限数检查、未知 report 时间不等于导入时间、温度有 battery 类型、无原始文本／唯一 ID、21 组缩至固定排序前 20 并标注省略；不得从省略组生成候选。重复 ID 拒绝、48*1024 字节允许而多一字节拒绝，中文／emoji 通过同一编码器检查。
- [ ] 跑 `GuidedEvidenceAdapterTest`，补最小接口后确认门控断言失败。
- [ ] 实现以上模型和适配。耗电投影复用修复后的来源门控；温度与热状态保留独立时刻。只有 observation.isUsableEvidence 才映射速率；无效观察保留拒绝原因，不算速率。报告设备归属未知保留缺口，不由本机基础快照补齐。摘要缺少适用证据时允许 Ready 空证据，由规则说明缺口。
- [ ] 重跑测试；增加源对象变化不影响已确认样本的防御性复制断言。
- [ ] 独立审查后提交 `feat: add typed guided diagnostic evidence`。

### Task 7：待机掉电规则与确定性动作排序

**Files:** 新建 `M/agent/IdleDrainRuleSet.kt`、`GuidedActionPolicy.kt`、`T/IdleDrainRuleSetTest.kt`；复用 Task 6 fixtures。

**Interfaces:** `IdleDrainRuleSet.evaluate(answers: GuidedAnswers, samples: List<GuidedSample>): GuidedAssessment`；`GuidedActionPolicy.select(actions: List<GuidedAction>): GuidedAction` 按系统警告、冲突、关键缺口、观察／验证、结束排序，类内按固定 ID 顺序，不按模型文本排序。

- [ ] 写表驱动测试覆盖设计第 5 节全部分支；仅当前电量不能给速率，未知窗口不能给具体应用调整，共享 UID 不拆分，充电／重启不明不宣称整段息屏；断言 candidates.size <= 3、只有一个主动作且所有引用可解析。对不支持的来源重复评估不循环推荐同一采集动作。
- [ ] 跑 `IdleDrainRuleSetTest` 确认失败。
- [ ] 实现规则：缺回答优先 ANSWER；量化不足 OPEN_OBSERVATION；要应用归属且无报告 IMPORT_REPORT；不支持且没有可替代来源 END。支持“后台活动线索”的候选必须注明相关性，OPEN_VERIFICATION 还须经过现有建议安全边界，不自行放宽系统／支付等保护。
- [ ] 重跑测试；加入从 Task 1 真实报告 fixture 到 adapt/evaluate 的端到端断言。
- [ ] 独立审查后提交 `feat: add evidence-gated idle drain guidance`。

### Task 8：使用发热规则与两份观察并列

**Files:** 新建 `M/agent/UsageHeatRuleSet.kt`、`T/UsageHeatRuleSetTest.kt`；必要时增加 `M/agent/GuidedThermalStatus.kt` 作为经过核实的显式状态映射。

**Interfaces:** `UsageHeatRuleSet.evaluate(answers: GuidedAnswers, samples: List<GuidedSample>): GuidedAssessment`；保持 Task 6 合同。两份读数分别作为 facts，不产生热百分比、优化分数或跨类型差值。

- [ ] 写表驱动测试覆盖设计第 6 节：体感无数值、仅电池温度、未知状态码、当前温度＋历史报告、同时充电使用、任务结束后读数变化、缺单位／来源冲突；已识别警告 primaryAction 为 VIEW_SYSTEM_WARNING，绝不 COLLECT 压力样本或建议继续游戏。温度 0 是值而非缺失；NaN/Infinity 不成为事实。
- [ ] 跑 `UsageHeatRuleSetTest` 确认失败。
- [ ] 实现独立规则，不调用耗电比较引擎比较温度；状态映射实施时查适用 Android 官方契约并在代码／测试记录依据，未知编码保持未知。不得以 time 接近自动证明两个样本是同事件，不以当前低温反证历史没热过。
- [ ] 重跑测试，与待机规则一起运行，确认同一输入在两场景给出不同且合理的主动作。
- [ ] 独立审查后提交 `feat: add conservative usage heat guidance`。

### Task 9：可测试的会话控制器和 ViewModel

**Files:** 新建 `M/agent/GuidedDiagnosticController.kt`、`M/GuidedDiagnosticViewModel.kt`、`T/GuidedDiagnosticControllerTest.kt`；在 Task 6 模型文件补会话状态合同。

**Interfaces:** `GuidedDiagnosticSession(id: String, scenario: GuidedScenario, revision: Long, sampleRevision: Long, answerRevision: Long, answers: GuidedAnswers, samples: List<GuidedSample>, stage: GuidedStage)`；`GuidedSessionKey(id: String, revision: Long, sampleRevision: Long, answerRevision: Long)`；`GuidedUiState(session: GuidedDiagnosticSession?, assessment: GuidedAssessment?, availableSampleId: String?, errorId: String?)`。

controller 暴露 `start(scenario)`、`updateAnswers(answers)`、`offerSample(sample: GuidedSample)`、`confirmSample(sampleId: String, replaceSampleId: String?)`、`markActionStarted(actionId: String)`、`end()`、`restart(scenario)`、`dispose()`、只读 state；start/restart 参数均为 GuidedScenario。ViewModel 转发；不把摘要放 SavedStateHandle。

- [ ] 写测试：仅 offer 不改 confirmed；第三份必须显式选择替换哪份；重复 confirm 同 ID 幂等；answers 改动失效评估；旧版本回调被丢弃；restart 清空全部；end 取消任务但保留可读本次结果，重新进入新会话不伪恢复旧会话。新 ViewModel 模拟进程重建为空。
- [ ] 跑 `GuidedDiagnosticControllerTest` 确认失败。
- [ ] 实现纯控制器与 Android owner；样本候选仅保留一份，新的 offer 替代候选需界面显示；确认时绑定当前回答版本并重新预览，不继承旧勾选。返回聊天采集属于暂停当前流程，不自动 end；主动关闭向导取消请求。
- [ ] 重跑控制器测试及 Task 7/8 规则测试；校验状态转换与错误状态独立。
- [ ] 独立审查后提交 `feat: add versioned guided diagnostic sessions`。

### Task 10：双语向导界面和现有能力接线

**Files:** 新建 `M/GuidedDiagnosticScreen.kt`、`GuidedDiagnosticForms.kt`、`localization/GuidedDiagnosticCopy.kt`、`I/GuidedDiagnosticUiTest.kt`、`T/GuidedDiagnosticCopyTest.kt`；修改 `M/MainActivity.kt`、`PowerDiagnosticPanel.kt`，必要时 `PowerDiagnosticEntryPoints.kt`。

**Interfaces:** `GuidedDiagnosticScreen(state: GuidedUiState, onEvent: (GuidedUiEvent) -> Unit)`；sealed `GuidedUiEvent` 对应 Task 9 操作，额外 `RequestAction(actionId: String)`、`RequestExplanation`。主 Activity 用 actionId 查当前 assessment 并校验 session key 后路由已有导入／采集／观察／验证入口；不得将模型字符串作为导航参数。

- [ ] 写 UI 测试：无 Key 两场景均能完成本地评估；所有声明默认 UNKNOWN；切场景／替样本须确认；四部分与来源标签可读；Root／网络 mock 调用数在未点击时为 0；仅热证据禁用耗电验证入口。补大字体、小宽度、IME、中英文、语义标签和返回采集后候选预览。
- [ ] 跑文案 JVM 测试并编译 UI 测试；设备可用时先观察未接线导致的失败。
- [ ] 实现表单和界面；动作说明、前提、停止条件、返回判据均来自双语 ID 映射，所有规则 ID 必须有两种文案。导航导入后只 offer，不自动确认；验证记录创建仍走原预览／保存流程，明确“仅验证耗电”。新增本地基础采样动作须由用户点击，不自动重复采样。
- [ ] 重跑文案测试及 UI 编译；设备测试单独记录实测条件，确认滚动、旋转和重新开始行为。
- [ ] 独立审查后提交 `feat: connect idle and heat guidance to local diagnostic UI`。

### Task 11：有界 AI 载荷、结构化解释校验和协议适配

**Files:** 新建 `M/agent/GuidedExplanationPayload.kt`、`GuidedExplanationValidator.kt`、`GuidedExplanationProvider.kt`、`T/GuidedExplanationPayloadTest.kt`、`GuidedExplanationValidatorTest.kt`、`I/GuidedExplanationProviderTest.kt`；复用现有 CloudHttpTransport/CloudResponseReader，不改普通聊天协议行为。

**Interfaces:** `GuidedExplanationOptions(includeNotes: Boolean = false)`；`GuidedExplanationPayload.build(session: GuidedDiagnosticSession, assessment: GuidedAssessment, options: GuidedExplanationOptions, language: AppLanguage): GuidedPayloadResult`，Ready 持有 systemPrompt、userPrompt、sessionKey、允许 ID 集合和 language；Rejected 持有 reasonId。

`GuidedExplanationText(factNotes: List<GuidedReferenceNote>, candidateNotes: List<GuidedReferenceNote>, gapNotes: List<GuidedReferenceNote>, actionNote: String)`，`GuidedReferenceNote(ids: List<String>, text: String)`；`GuidedExplanationValidator.validate(raw: String, payload: GuidedPayloadResult.Ready): GuidedExplanationResult`（Accepted(text) / Rejected(reasonId)）；`GuidedExplanationProvider.explain(payload: GuidedPayloadResult.Ready, credentials: StoredApiKey, cancellation: CloudRequestCancellation): GuidedExplanationResult`。

- [ ] 写载荷测试：默认不含备注，开启再构建，16384 字节通过／16385 拒绝，多字节按 UTF-8 计数，注入备注是数据；无历史／原始输出／凭据字段。写 validator 测试：要求 version=1、factNotes/candidateNotes/gapNotes/actionId/actionNote；缺字段、额外动作、未知 ID、跨会话引用、类型不符、过长文本拒绝。
- [ ] 跑两个 JVM 类；JSON 解析如需 Android org.json，将解析器契约测试置于 I，纯校验测试保留 JVM，不把 mock org.json 当真实协议验证。
- [ ] 实现严格 schema：每类 notes 最多 20/3/20 项，每项最多 20 个同类 ID、text 最多 500 码点；actionNote 最多 500 码点，actionId 必须等于唯一主动作；解包后 raw UTF-8 最多 16 KiB；未知键拒绝。JSON 解码使用现有依赖支持的实现，不新增库。模型数值和动作文字不取代本地正文，沿用能力限制检查；合法引用也不宣称语义安全。
- [ ] 编写两种协议 fixture（mock post，无网络），验证请求无聊天历史、credentials 只用于传输、服务商响应先有界读取再严格解析；取消、空内容、非法 JSON、过期 key 均类型化失败。重跑 JVM 与仪器测试编译，真实 Android JSON 运行另记。
- [ ] 独立审查后提交 `feat: constrain guided AI explanations to local evidence and actions`。

### Task 12：解释预览、生命周期和离线对抗用例

**Files:** 新建 `M/agent/GuidedExplanationController.kt`、`M/GuidedExplanationPanel.kt`、`T/GuidedExplanationControllerTest.kt`、`I/GuidedExplanationUiTest.kt`；修改 `M/GuidedDiagnosticViewModel.kt`、`GuidedDiagnosticScreen.kt`；新建 `scripts/ai_eval/guided_cases.json`、`docs/guided-diagnostics-evaluation.md`，仅在需要时扩展现有 `scripts/ai_eval/evaluate.py`、`test_evaluate.py`。

**Interfaces:** controller 的 `preview(options: GuidedExplanationOptions)`、`send(expectedKey: GuidedSessionKey)`、`cancel()`、`invalidate()`、`dispose()`；构造依赖 current session/assessment、credentials supplier、provider、executor/publishExecutor，沿用现有可控 executor 测试方式。UI 接收状态和回调，不自行持有请求。language 与 options 变化同样失效预览及旧解释。

- [ ] 写测试：发送前必须确认与当前 key/options/language 匹配的预览；滚动保留结果；样本替换／回答变化／场景切换／退出后回调不生效；停止后可手动重试，双击只发一次；无 Key 不影响本地 assessment。摘要超限按钮禁用并说明原因。
- [ ] 跑 `GuidedExplanationControllerTest`，编译 UI 测试，确认最小接口尚未具备所需行为。
- [ ] 实现页面级 owner、备注默认关闭和发送范围预览；凭据只在用户确认发送时读取。provider 输出只能出现在“可选解释”区，拒绝时保留完整本地四部分，不显示部分不合格回复。
- [ ] 添加每场景至少 6 个离线用例，包含未知／共享 UID／历史时间／冲突／备注注入／新增动作诱导；记录期望 ID 和禁止结论。适配现有 eval harness 后执行 `python -m unittest discover -s scripts/ai_eval -p 'test_*.py'`；说明离线 fixture 检查不是实际模型遵从率，人工评测列事实错误和格式错误两列。
- [ ] 重跑相关 JVM、UI 编译和离线评测；设备及真实模型仅在具备授权条件时进行，不伪造结果。独立审查后提交 `feat: add explicit guided AI previews and lifecycle guards`。

### Task 13：全链路验收与交付说明

**Files:** 新建 `T/GuidedDiagnosticPipelineTest.kt`；修改 `README.md`、`README_EN.md`、`ROADMAP.md`、`docs/TESTING.md`、`docs/TECH_DEBT.md`、`docs/guided-diagnostics-evaluation.md`。

**Interfaces:** 不增加产品接口；组合 Tasks 1–12 对外接口，验证导入到本地评估再到模型载荷的最终语义和隐私边界。

- [ ] 先写 pipeline tests：真实 parser → adapter → 两套 evaluate → payload，断言区段／零值／映射门控完整传递，主动作始终来自本地，温度不进耗电比较；复测验证记录缺失不能伪造成功链接。
- [ ] 运行新测试确认能检测预期门控回归（可用临时测试替身制造错误，不提交破坏性产品改动），修复后通过。
- [ ] 执行 `./gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest --offline` 和离线 eval 测试；检查实际报告的通过数、失败与警告，不能沿用之前的 500 测试计数。
- [ ] 独立整体验收审查整个变更范围，补审跨任务接口、删除／取消、隐私和翻译；审查发现修复后重跑受影响测试。
- [ ] 更新文档，将“已实现、合成验证、设备验证、真实模型验证”分开；列明进程死亡不恢复向导、发热只描述性并列、窗口未知不能计算效果。未运行的授权设备和人工可用性验证列为发布前门槛，不把功能编译成功标成已完成这些门槛。
- [ ] 提交文档与集成测试 `test: verify guided diagnostic workflows and document limits`；报告文件、测试证据、剩余限制。不自动合并主目录、发布或推送。

## 自审与交接

规格覆盖：章节 1–4 → Tasks 6/9/10；章节 5 → Task 7；章节 6 → Task 8；章节 7 → Tasks 11/12；章节 8 → Tasks 1–6/9；章节 9 → 各任务测试与 Task 13；章节 10 → 本计划依赖顺序。Review Focus 五项已落到对应失败测试。

执行方法保留用户指定的“子代理逐项实施、逐项审查”。用户确认本计划后才开始 Task 1；无需重复选择执行方法。Git 作者身份缺失不阻止本地编写与验证，但提交需要合法配置，不能把未提交状态报告为已提交。
