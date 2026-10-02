# Diagnostic Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户主动的“保存诊断 → 记录一项手动调整 → 复测 → 阅读有证据边界的变化”，本地完整可用，AI 解释可选。

**Architecture:** 从现有诊断投影出最小化结构，独立保存验证记录；纯 Kotlin 引擎负责共同与逐指标门控。独立状态控制器协调持久化，Compose 面板接入既有诊断入口；独立、无历史 AI 请求只消费本地比较结果。

**Tech Stack:** 既有 Kotlin/JVM、Android API 26+、Compose、ViewModel、协程、JUnit4、Android Keystore AES/GCM；不增加依赖、不升级构建配置。

**Spec:** `docs/superpowers/specs/2026-10-02-diagnostic-verification-design.md`（用户于 2026-10-02 确认）。起点提交 `ccea1fc`；设计的产品代码基线为 `558d859`。

## Global Constraints

- 第一版接入已有 Bug Report 导入及 Root 只读诊断，只比较同来源数据；不增加后台监控、自动修改系统、自动上传或新 Shizuku 链路。
- 第一版每条记录最多一份基线、一项调整、一份复测；不实现多轮实验树。
- 上限：20 条记录，单条明文编码最多 48 KiB，全归档最多 1 MiB；每条自创建起保留 30 天，修改不延长期限。
- 标题最多 80 字符；调整说明、原设置/回退、使用场景与副作用备注各最多 500 字符；最多保存目标组及按耗电排序的另外 19 组。
- 双方有效统计窗口及电池运行时长至少 30 分钟；双方电池运行时长比值须在 0.8..1.25 范围内。
- 导入时间不得充当报告/统计时间；未知不得补零或猜窗口；第一版不做累计值差分重建窗口、不执行统计重置命令。
- Keystore AES/GCM、独立 alias、noBackupFilesDir；不保存原始报告、日志、Key、账号、唯一硬件标识或全量应用列表。
- 验证附件最多 16 KiB，超限阻止发送，不静默截断；自由文本默认不发送；解释请求不携带/写入普通聊天历史。
- 明确区分合成测试、APK 构建、仪器测试编译、设备运行、真实模型评测；不推送、不发布、不安装设备或调用真实模型，除非用户另行要求。

## Review Focus

- 设备型号相同但报告来自另一台手机：导入归属必须用户确认，缺失报告设备上下文不能被当前设备替代（任务 2）。
- 同值但不同 UID/包名排序，及重复报告换文件名：规范化白名单指纹不依赖文件名或显示名，共享组变更不可比较（任务 1、3）。
- 修改调整时间后旧比较仍可见：清除复测，旧异步回调必须因记录 revision 不匹配被忽略（任务 5）。
- 删除后排队保存或 AI 请求晚到：存储变更按队列顺序处理，UI 只接收当前记录/revision 的结果，不复活记录（任务 4、5、7）。
- 中文多字节、不可见字符与超长备注：字段字符限额及归档/发送 UTF-8 字节限额分别验证，不破坏单位/门控信息（任务 1、4、7）。

## 文件地图与执行约定

源码前缀记为 `S = app/src/main/java/com/aegis/apa`，JVM 测试前缀 `T = app/src/test/java/com/aegis/apa`，仪器测试前缀 `I = app/src/androidTest/java/com/aegis/apa`。下文路径均以仓库根为准，S/T/I 展开后是实际路径，不是另建目录。

新增文件按职责拆分，任务中列全；不整体重构 MainActivity、CloudLlmProvider 或旧归因引擎。所有新类型属性使用稳定英文名，所有时间用 Instant，数值单位进入字段/类型名。类型定义由任务 1 锁定，后续任务不得自行换名。

在当前隔离 checkout 执行；开始实现时先按 using-git-worktrees 技能确认隔离与 Git 状态，不盲目重建工作区。每项完成一次 TDD 和局部提交；不并发运行 Gradle。若发现设计需要新依赖/权限/后台服务，停止并回到设计确认。

测试命令（PowerShell，仓库根目录）：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='C:\Users\ROG\.gradle'
# 下文 UNIT(ClassName) 展开为：
.\gradlew.bat :app:testDebugUnitTest --tests com.aegis.apa.ClassName --offline --console=plain
# 下文 FULL 展开为：
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --offline --console=plain
```

首次定义缺失时可有编译红灯，但引擎/存储/控制器的行为回归需看到实际断言失败，再修复。绿灯要求退出 0，XML 无 failure/error；提交仅包含本任务明确文件及其必要测试。

## 阶段 A：本地闭环（任务 1–6）

### Task 1: 类型化摘要、投影和状态转移

**Files:** Create `S/model/DiagnosticVerification.kt`, `S/agent/VerificationSummaryProjector.kt`, `S/agent/VerificationRecordReducer.kt`; Test `T/VerificationModelTest.kt`, `T/VerificationSummaryProjectorTest.kt`; shared test factory `T/VerificationFixtures.kt`。

**Interfaces:**

- `VerificationRecord(id: String, schemaVersion: Int, ruleVersion: Int, scopeId: String, createdAt: Instant, updatedAt: Instant, revision: Long, title: String, state: VerificationState, target: VerificationTarget, baseline: DiagnosticVerificationSummary, change: VerificationChange?, retest: DiagnosticVerificationSummary?, closeReason: VerificationCloseReason?)`。
- `VerificationState` 固定四值 `BASELINE_SAVED/CHANGE_RECORDED/RETEST_SAVED/CLOSED`；结束原因 `COMPLETED/ROLLED_BACK/ABANDONED`；确认类型 `YES/NO/UNKNOWN`；场景 `IDLE/LIGHT/GAME/VIDEO/OTHER`，都带显式 wireId。
- `VerificationTarget` 为 `Overall` 或 `AppGroup(packageNames: List<String>)`；包名去重排序，不以 UID/displayName 作键。
- `VerificationDeviceContext(manufacturer: String?, modelCode: String?, apiLevel: Int?, buildVersion: String?, ownership: VerificationOwnership)`，归属值 `LOCAL_COLLECTION/USER_CONFIRMED/UNKNOWN`。
- `VerificationSampleContext(device: VerificationDeviceContext, scenario: VerificationScenario, scenarioNote: String, noCharging: VerificationConfirmation)`。
- `VerificationChange(description: String, rollback: String, confirmedAt: Instant, similarScenario: VerificationConfirmation, noOtherChanges: VerificationConfirmation, sideEffects: String)`。
- `MetricWindow(source: String, scope: VerificationMetricScope, startedAt: Instant?, endedAt: Instant?, batteryDurationMillis: Long?, resetId: String?, charging: VerificationConfirmation)`；scope 至少区分 `SINCE_LAST_CHARGE/SOURCE_CUMULATIVE/EXPLICIT_WINDOW/UNKNOWN`。
- `VerificationMetricKind`：整体/应用总耗电、应用前台/后台耗电、前台/后台运行时长、唤醒锁时长、唤醒/闹钟/任务次数；`VerificationMetricKey(kind, packageNames)`，整体包名为空；`VerificationMetric(key, value: Double?, unit: VerificationUnit, estimate: VerificationEstimate, window: MetricWindow, status: EvidenceFieldStatus)`，单位 `MAH/MILLIS/COUNT`，类别 `ACTUAL/ESTIMATED/UNKNOWN`。
- `DiagnosticVerificationSummary(inputSource: DiagnosticInputSource, generatedAt: Instant, reportedAt: Instant?, context: VerificationSampleContext, sources: Map<String, DiagnosticSourceStatus>, metrics: List<VerificationMetric>, fingerprint: String)`。
- 投影 `VerificationSummaryProjector.project(snapshot: PowerDiagnosticSnapshot, metadata: DiagnosticVerificationMetadata, context: VerificationSampleContext, target: VerificationTarget): DiagnosticVerificationSummary?`；无法生成有效摘要返回 null。
- reducer `recordChange(record, change, now): VerificationRecord`、`recordRetest(record, summary, now): VerificationRecord`、`close(record, reason, now): VerificationRecord`；非法转移抛类型化异常。基线不可修改，每次有效变更 revision+1，修改调整清除 retest。
- 测试工厂 `VerificationFixtures.record(): VerificationRecord` 构造 1 小时、明确独立窗口、同设备/同来源、全部用户确认和 100→80 mAh 的样本；`snapshot(): PowerDiagnosticSnapshot`、`metadata(): DiagnosticVerificationMetadata`、`context(): VerificationSampleContext` 提供对应输入。

- [ ] **Step 1: 写红灯测试。** `targetSurvivesTopTwentyLimit`：21 组中低耗电目标仍保留且总组数为 20；`packageOrderDoesNotChangeFingerprint`：相同来源/窗口/数值、乱序包名、不同 UID/displayName 得到相同指纹；`changeEditInvalidatesRetest`：revision 增加、retest=null、状态 CHANGE_RECORDED。控制字符清理、80/500 字符上限、未知保留 null、共享 UID 不拆分同时断言。
- [ ] **Step 2: 运行** `UNIT(VerificationModelTest)` 和 `UNIT(VerificationSummaryProjectorTest)`，先见缺失类型/断言红灯。模型空壳建立后，对过滤/指纹/reducer 断言复验红灯。
- [ ] **Step 3: 实现** 上述接口及元数据类型 `DiagnosticVerificationMetadata(reportedAt: Instant?, device: VerificationDeviceContext?, windowsBySource: Map<String, MetricWindow>)`。SHA-256 指纹基于来源、明确窗口与规范化数值/包名，排除备注、导入时间、文件路径和显示名。投影仅保存类型化状态与白名单数值，不保存 findings 文本、原始错误或日志。
- [ ] **Step 4: 复验** 两个 UNIT，全部通过；既有 `PowerDiagnosticPipelineTest`、`PowerDiagnosticParserTest` 保持通过。
- [ ] **Step 5: 提交** `feat: model diagnostic verification summaries and transitions`，只暂存本任务文件。

### Task 2: 保守窗口元数据与采集接线

**Files:** Create `S/tool/DiagnosticWindowMetadataParser.kt`; Modify `S/model/PowerDiagnostic.kt`, `S/tool/BugReportSectionExtractor.kt`, `S/tool/PowerDiagnosticPipeline.kt`, `S/tool/SystemPowerDiagnosticsCollector.kt`、`S/MainActivity.kt` 的导入成功接线；Test `T/DiagnosticWindowMetadataParserTest.kt`, `T/BugReportSectionExtractorTest.kt`, `T/PowerDiagnosticPipelineTest.kt`。

**Interfaces:** 消费任务 1 元数据类型；提供 `DiagnosticWindowMetadataParser.parse(sections: List<RawDiagnosticSection>, headerFields: Map<String, String>): DiagnosticVerificationMetadata`。`PowerDiagnosticSnapshot` 末尾新增 `verificationMetadata: DiagnosticVerificationMetadata? = null`；`BugReportReadResult.Success` 追加默认空的 `verificationHeaderFields: Map<String,String>`，旧调用兼容；pipeline `analyze` 追加默认空 `verificationHeaderFields` 参数。

- [ ] **Step 1: 写红灯测试。** `legacyCumulativeReportDoesNotAcquireWindowFromImportTime` 断言既有 Time on battery/Capacity 报告的 startedAt/endedAt 保持 null；`foreignModelIsNotReplacedByCurrentDevice` 断言报告型号冲突保留冲突且投影不以当前型号覆盖；`batteryWindowIsNotCopiedToAlarm` 断言 alarm 窗口未知。重复/冲突头、非法时间、无时区、截断来源、取消、超限也拒绝补齐。
- [ ] **Step 2: 运行** `UNIT(DiagnosticWindowMetadataParserTest)` 及既有 extractor/pipeline 测试，确认新增契约红灯而非网络/设备失败。
- [ ] **Step 3: 实现** 仅保留主报告中精确匹配的非唯一设备属性 `ro.product.manufacturer/ro.product.model/ro.build.version.sdk/ro.build.version.incremental`；单值限 256 UTF-8 字节，头字段总限 4 KiB，重复冲突使对应值未知，不保存 fingerprint/序列号/账号或任意属性。现有 streaming/ZIP/取消上限不变。窗口解析沿用既有区段含义，保留 SINCE_LAST_CHARGE 和电池运行时长，但没有来源明确起止时一律未知。**不创造生产格式让合成数据冒充支持的 OEM 格式**；当前仓库无已验证的独立窗口格式，因此第一版生产入口允许窗口未知，明确窗口由任务 1 的纯模型合成夹具验证计算。后续新增格式必须有真实脱敏来源样本并另行审查。Root 上下文从当前基础设备字段取得，采集时钟只填 generatedAt，不作统计窗口。
- [ ] **Step 4: 复验** 三个测试类，既有大文件/恶意 ZIP/取消和诊断数值输出不变；新增元数据为空时旧 report/prompt 契约不变。
- [ ] **Step 5: 提交** `feat: preserve conservative verification source metadata`。

### Task 3: 可比性门控与逐指标计算

**Files:** Create `S/agent/VerificationComparisonEngine.kt`, `S/agent/VerificationResultFormatter.kt`; Test `T/VerificationComparisonEngineTest.kt`, `T/VerificationResultFormatterTest.kt`。

**Interfaces:** `compare(record: VerificationRecord): VerificationComparison`；`VerificationComparison(ruleVersion: Int, commonReasons: List<VerificationReason>, metrics: List<VerificationMetricComparison>)`；指标结果包含 key、双方原指标、`status`、reasons、`beforeRate/afterRate/delta/relativeChange: Double?`、`rateUnit`。status 固定设计四值；stable reason 至少区分缺复测/调整、来源/设备/归属、用户确认、重复、缺窗口/重叠/时间次序、短窗口、时长比、缺字段/坏值/估算类别/单位/统计范围/组变化。`VerificationResultFormatter.format(record, comparison, language: AppLanguage): String`。

- [ ] **Step 1: 写红灯测试。** 使用工厂记录：

```kotlin
val result = VerificationComparisonEngine.compare(VerificationFixtures.record())
val metric = result.metrics.first { it.key.kind == VerificationMetricKind.OVERALL_DRAIN }
assertEquals(100.0, metric.beforeRate!!, 0.000001)
assertEquals(80.0, metric.afterRate!!, 0.000001)
assertEquals(-20.0, metric.delta!!, 0.000001)
assertEquals(-0.2, metric.relativeChange!!, 0.000001)
```

参数化覆盖设计全部门槛，比例 0.8/1.25 含边界、稍超边界拒绝；双方窗口及电池时长分别不足 30 分钟拒绝。电池时长大于窗口时长拒绝，Long 溢出/NaN/Infinity 不传播。一个字段失败时另一个仍可计算；零基线无相对变化；共享组变化拒绝、UID 改变不拒绝。格式器只写“本次观察值减少”，不出现“已节电/显著/元凶”且副作用保留。
- [ ] **Step 2: 运行** 两个 UNIT，确认算式/门槛行为红灯。
- [ ] **Step 3: 实现** 门控先于算术：共同原因阻断所有数值结论，逐指标原因只阻断对应项；原值仍保留。以电池运行小时为分母；MILLIS 转分钟，COUNT 按次；精确相等才 NO_NUMERIC_CHANGE，不自定显著性阈值。实际/估算标签贯穿结果，group 按完整包名集合匹配。
- [ ] **Step 4: 复验** 两 UNIT 及 `UNIT(LocalPowerAttributionEngineTest)`，中英文数值、原因一致。
- [ ] **Step 5: 提交** `feat: compare only verifiable diagnostic windows`。

### Task 4: 有界加密归档与串行变更

**Files:** Create `S/agent/VerificationArchive.kt`, `S/agent/VerificationStore.kt`, `S/agent/AndroidVerificationStore.kt`; Test `T/VerificationArchiveTest.kt`, `T/VerificationStoreTest.kt`。

**Interfaces:** `VerificationArchive.encode(scopeId: String, records: List<VerificationRecord>): ByteArray` 与 `decode(bytes: ByteArray, now: Instant): VerificationArchiveData`（scopeId、有效 records、expiredIds）；`VerificationStore(file: File, key: () -> SecretKey, clock: () -> Instant)`，同步 `load(): VerificationLoadResult`、`upsert(record): VerificationLoadResult`、`delete(id: String): VerificationLoadResult`、`clear(): VerificationLoadResult`。结果含 records、scopeId、failure: VerificationStorageFailure?、expiredIds；failure 区分 DAMAGED/UNSUPPORTED/KEY_UNAVAILABLE/LIMIT/WRITE_FAILED。所有 mutation 内部读取最新状态，禁止 UI 提交旧完整列表覆盖存储。

- [ ] **Step 1: 写红灯测试。** 第 21 条/48 KiB 单条/1 MiB 归档拒绝且旧文件未变；30 天边界过期、编辑不延期、未知 schema/wireId/负长度/尾随垃圾拒绝；中文多字节限额按字节计算。保存密文不含包名/备注；篡改、错误密钥、目录写失败保持错误；损坏归档不被 upsert 覆盖。提交排队 upsert→delete，最后 load 不含记录；过旧 revision 的 upsert 不复活已删记录（store 的串行协调器使用删除 tombstone/generation，只在同一进程内维护，不记录敏感正文）。
- [ ] **Step 2: 运行** 两个 UNIT；JVM 临时目录与测试 AES key，无 Android KeyStore/真实文件依赖。
- [ ] **Step 3: 实现** schema v1 稳定 ID、严 UTF-8、全局/字段读取限额，创建时生成 scopeId 并持久于同一归档；过期后保留 scopeId。独立 alias `apa_verification_aes_v1`、文件 `apa_verification.enc` 与同目录 pending 文件；AES/GCM 12-byte IV + 16-byte tag，明文最多 1 MiB，加密文件最多 1 MiB+28。串行队列/锁保护 load-modify-atomic-replace；fsync 后原子替换；无不安全回退。损坏/未知版本仅 clear 明确授权后重建，空记录集也保留 scopeId；删除失败显示失败，不宣布已删除。
- [ ] **Step 4: 复验** 两 UNIT 和 `UNIT(ConversationArchiveTest)`，确认未改变旧聊天存储；过期清理持久化失败仍标明未保存，不覆盖原文件。
- [ ] **Step 5: 提交** `feat: persist bounded encrypted verification records`。

### Task 5: 可测试流程控制器与生命周期适配

**Files:** Create `S/agent/VerificationController.kt`, `S/VerificationViewModel.kt`; Test `T/VerificationControllerTest.kt`。

**Interfaces:** 控制器注入任务 4 store、clock、可控 `Executor`；`createBaseline(title, target, summary)`、`recordChange(id, expectedRevision, change)`、`recordRetest(id, expectedRevision, summary)`、`close(id, expectedRevision, reason)`、`delete(id)`、`clear()`，均通过 `onState: (VerificationUiState) -> Unit` 发布状态。`VerificationUiState` 包含 records、selectedId、loading/saving、storageFailure、unsavedRecord；`select(id: String?)` 更新选择；`dispose()` 失效回调。`VerificationViewModel` 将状态桥接 Compose，在 IO 执行 store，用 Main 发布，onCleared dispose；控制器不引用 Android。

- [ ] **Step 1: 写红灯测试。** 控制测试 executor，`deleteDuringQueuedSaveCannotRestoreRecord`、`oldRetestAfterChangeEditIsIgnored`、`selectionSwitchDoesNotReceiveOldResult`、`doubleSubmitDoesNotCreateTwoRecords`；未保存显示 unsavedRecord，成功才显示 saved；CLOSED 不接复测。进程恢复加载已保存状态，不能恢复原 URI/诊断输出；清空聊天不触发 store.clear。
- [ ] **Step 2: 运行** `UNIT(VerificationControllerTest)`，看到竞态断言红灯，不新增协程测试依赖。
- [ ] **Step 3: 实现** 与 task 1 reducer 对接，generation/id/revision 三重检查；所有 mutation 经过 task 4 队列；确认时间由用户点击时 clock 提供，不用复测采样时间覆盖。保存/删除/清空等待结果；控制器停止后不通知旧页面，保留已提交合法持久变更。
- [ ] **Step 4: 复验** controller UNIT 与 `UNIT(MainSessionPowerDiagnosticTest)`；普通会话状态不回归。
- [ ] **Step 5: 提交** `feat: coordinate verification lifecycle and persistence`。

### Task 6: 本地 UI 接入与可读结果

**Files:** Create `S/VerificationPanel.kt`, `S/VerificationRecordDetail.kt`, `S/VerificationForms.kt`; Modify `S/PowerDiagnosticPanel.kt`, `S/AgentChatScreen.kt`, `S/MainActivity.kt` 的依赖/回调接线；Test `I/VerificationPanelUiTest.kt`, existing `I/PowerDiagnosticPanelUiTest.kt`。

**Interfaces:** `VerificationPanel(state: VerificationUiState, currentDiagnostic: PowerDiagnosticSnapshot?, onCreateBaseline: (VerificationTarget, VerificationSampleContext, String) -> Unit, onSelect: (String?) -> Unit, onChange: (String, Long, VerificationChange) -> Unit, onRetest: (String, Long, VerificationSampleContext) -> Unit, onClose: (String, Long, VerificationCloseReason) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit)`；新增主诊断按钮回调 `onOpenVerification: () -> Unit = {}`，旧调用可默认。细节/表单内部复用任务 3 formatter，不自己算指标。

- [ ] **Step 1: 写 UI 红灯测试。** 无 Key 流程：创建前展示保存范围→手动确认调整→选择当前复测→查看不可比理由/原值→结束/删除。导入设备归属、场景相似、无其他调整、无充电均默认 UNKNOWN，不能默认勾选。修改调整确认清复测；损坏文件只显示错误和明确清空确认；清空聊天保留验证记录提示。加入屏幕宽 320dp、fontScale 1.5、长结果/IME 布局回归，使用假 store/context/diagnostic，不用真实个人归档。
- [ ] **Step 2: 运行** `:app:assembleDebugAndroidTest` 验证测试编译；有可用测试设备且用户授权才执行 `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aegis.apa.VerificationPanelUiTest`，先证实缺少控件/流程失败。无设备则标注仪器红绿运行待测，不假称 UI 断言通过。
- [ ] **Step 3: 实现** 记录列表→详情→独立表单；独立 ViewModel，不把新状态堆进 MainSessionViewModel。创建/复测前投影当前诊断并预览摘要；明确 unknown 窗口意味着无法计算效果。标题/备注控件显示限额；保存中禁止重复提交；删除/清空确认；关闭不自动设置设备。界面中英文对应，长列表懒加载、有界滚动。
- [ ] **Step 4: 运行 FULL** 和 task 1–5 局部测试，保留 XML/Lint/APK 路径；设备缺失单独注明。阶段 A 可单独交付：无 Key 完整本地闭环、未知窗口的事实并列、明确合成窗口的引擎计算均完成。
- [ ] **Step 5: 提交** `feat: expose local diagnostic verification workflow`；同步 `docs/DATA_MODEL.md`、`docs/TESTING.md`、`ROADMAP.md` 的已实现/未设备测范围，不宣称支持未知 OEM 窗口。

## 阶段 B：可选 AI 解释（任务 7，依赖阶段 A）

### Task 7: 独立受限请求与解释界面

**Files:** Create `S/agent/VerificationExplanationPayload.kt`, `S/agent/VerificationExplanationProvider.kt`, `S/agent/VerificationExplanationController.kt`, `S/VerificationExplanationPanel.kt`; Modify `S/VerificationRecordDetail.kt`; Test `T/VerificationExplanationPayloadTest.kt`, `T/VerificationExplanationControllerTest.kt`, `I/VerificationExplanationProviderTest.kt`, `I/VerificationExplanationUiTest.kt`; extend `docs/ai-evals/cases.json` and both fixtures/tests if adding verification eval IDs。

**Interfaces:** `VerificationExplanationOptions(includeNotes: Boolean = false)`；`VerificationExplanationPayload.build(record, comparison, options, selectedLevel: String?, language: AppLanguage): VerificationPayloadResult`（Ready(systemPrompt, userPrompt) 或 TooLarge，不静默裁剪）；`VerificationExplanationProvider.explain(payload, credentials: StoredApiKey, cancellation: CloudRequestCancellation): AgentReport`，构造器注入 `post: (CloudProviderConfig, StoredApiKey, String, CloudRequestCancellation) -> String`，默认四参数 lambda 调用 `CloudHttpTransport.post(config, key, body, cancellation)`，不能用五参数函数的直接引用冒充四参数函数。控制器 `explain(recordId, revision, options, credentials)`、`cancel()`、`dispose()`，输出只存内存，隔离聊天和验证归档。

- [ ] **Step 1: 写红灯测试。** 本地 NOT_COMPARABLE 原因与指标缺失进入双语约束；默认不含备注/回退 sentinel，显式开启才含；16 KiB 与多字节越界返回 TooLarge，不能掉单位/理由。假 post 捕获两种协议 JSON：只有 system + 单条 user（Anthropic system 字段 + 单条 user），无历史，无基础全量快照；未知协议/空正文/401/429/取消/过期 Key 类型化失败。恶意备注作为数据，不成为 system 指令。旧记录回复晚到/删除/改 revision/取消都不显示且不保存；无 Key 不触发 post。
- [ ] **Step 2: 运行** payload/controller 两个 UNIT，先看到边界/回调断言失败。两协议 JSON 测试使用 Android 自带 org.json，放在仪器测试，不引入 JVM JSON 依赖或手写 JSON 解析器；先编译仪器 APK，有已授权设备时运行对应 provider 测试类并验证红灯，无设备则明确协议运行待测。全部使用假 post/合成回复，不读取实际 Key、不联网。
- [ ] **Step 3: 实现** 复用服务商目录、CloudHttpTransport、CloudResponseReader 与既有取消机制；单独组 JSON/解析响应，不扩大旧 CloudLlmProvider 接口或自动向历史追加消息。当前能力从既有策略核定；提示词明确不可比、相关性、实际/估算和未执行动作，响应调用已有能力词法过滤，同时在 AI 区域始终显示本地结论与“模型解释不能替代本地门控”。记为模型解释而非验证结论，无语义安全保证。点击后先预览发送范围，超限/无 Key 明确提示，错误不改验证状态。
- [ ] **Step 4: 验证** FULL + 离线评测工具 unittest；新增评测必须同步配置和手工好坏样例，包含窗口重叠却声称节电的语义错误，仍保留人工 PENDING。仪器测试编译不等于实际运行；真实模型效果待另行授权评测。
- [ ] **Step 5: 提交** `feat: explain verification results without implicit history sharing`；更新 README/README_EN、设计落地状态、测试结果/限制和评测说明。整分支最后做独立审查，修复重要意见后重跑 FULL 与工具单测；本地主项目同步前确认其无未提交变动且基线未变，不推送/发布。

## 计划自检与交接

- 设计 1–6 节由任务 1–3、5–6 落实，存储/隐私由任务 4–6，AI 由任务 7；全部参数集中 Global Constraints，没有将未知生产窗口包装为已支持。
- 各任务有文件、跨任务接口、红灯断言、实现动作、复验与局部提交；五项 Review Focus 已分配到具体测试。
- 当前只是计划。应用功能、测试运行数量、实际窗口兼容与设备结果都不得引用本计划当作完成证据。
- 推荐 Native：七项任务依赖相邻接口，主实现者顺序执行更利于保持类型/生命周期一致，最后单独审查整个分支。用户也可选 Subagent-driven：逐任务交给子代理并逐项审查，独立检查更密集，需更多上下文与协调。
- 用户审阅本计划并选择执行方式后才开始实施；下一个技能分别为 executing-plans 或 subagent-driven-development。
