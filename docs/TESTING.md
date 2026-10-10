# 测试与验证记录

## 问题驱动诊断向导全链路验收（2026-10-09，开发副本）

**已实现**：待机掉电／使用发热本地规则、显式回答／样本确认、版本化内存会话、单一手动主动作，以及独立可选解释的预览／确认／备注默认关闭／取消／过期结果保护。向导只在页面 ViewModel 内存保留，配置变化保留，进程死亡重新开始；最多两份已确认摘要。热读数按各自来源和时间描述性并列，不进入耗电比较、不计算散热改善或健康百分比；生产窗口起止未知不能计算调整效果。

**合成验证**：新增 `GuidedDiagnosticPipelineTest` 十项，从真实 Bug Report extractor/parser → adapter → 两套 evaluate → payload，覆盖选区／actual=0／重复区段拒绝、不完整映射／共享 UID、未知窗口／归属／回答门控、本地主动作不可替换、报告热状态优先级、备注及标识隐私、温度不进耗电比较和记录缺失／保存失败不伪造成功。正向验证动作只使用显式标注的合成窗口元数据；真实生产报告没有该能力。两份完整报告的解释载荷可超限，测试验证拒绝发送且不截断本地事实。

最终审查修复补充十项 JVM 测试：Activity 共用初始化器保留主动选择的无 Key 会话；整体耗电使用独立 `OVERALL_POWER_MAH` 覆盖，不升级应用 `POWER_MAH`；真实进程／恢复观察经 adapter→发热规则→payload 保留未知充电窗口及粗略掉电速度；共用主动作／手动重试／主动刷新事件分发与基础快照交付边界验证实际分发次数、完整版本键、在途任务和两样本替换上限。任务已结束但会话未结束时可主动刷新；已识别系统警告仍优先，实际结束会话不可复活。新读数仍须预览确认，无自动采样／重试。确认勾选项改为合并语义的带标签行，仪器断言检查标签及 checkbox role，仅编译未上机。

门控敏感性 RED：临时仅在测试的 adapter→rule 边界给真实 ROOT 样本伪造匹配的明确窗口和无充电状态；10 项中 1 项预期行为断言失败（期待 `idle.observe_condition`，实际 `idle.verify_background`）。移除变异后 10/10 通过。它证明断言能抓住错误窗口授权，不是发现或提交了生产缺陷。首次夹具运行的三项失败已纠正（重复支持标题应拒绝、缺失可选来源单独保留、双完整报告应超限），不计作产品行为 RED。

最终离线命令使用现有 Android Studio JBR、Gradle 缓存与 wrapper，在 `C:/Users/ROG/Documents/APA/work/current` 执行：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'C:/Users/ROG/.gradle'
./gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest --offline
$env:TEMP = 'C:/Users/ROG/Documents/APA/work/current/.superpowers/sdd/2026-10-03-guided-diagnostics/eval-temp'
$env:TMP = $env:TEMP
& 'C:/Users/ROG/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B -X utf8 -m unittest discover -s scripts/ai_eval -p 'test_*.py' -v
```

最终审查修复后的 Gradle 退出 0（36 秒），`:app:testDebugUnitTest` 实际执行；新鲜 XML 为 **693 项／90 套件，0 failure/error/skipped**，时间范围 `2026-10-09T15:47:34.297Z` 至 `15:47:38.101Z`。原 pipeline 十项保留；本轮新增十项，另强化既有重复动作断言。Python **18/18** 通过，临时目录运行后为空。Debug 与 Android 仪器 APK 均重新构建，仪器 Kotlin 源码实际编译；这些结果不代表设备运行。

Lint XML：Debug **0 错误／35 警告**；Release **0 错误／33 警告**。共同警告为 `GradleDependency` 3、`ModifierParameter` 4、`OldTargetApi` 1、`RedundantLabel` 1、`UnusedResources` 12、`UseKtx` 9、`UseTomlInstead` 3；Debug 另有 `NewerVersionAvailable` 2（既有机型名称库／Compose 插件依赖）。这些位置在原有构建配置、Manifest、资源、聊天／诊断界面和偏好／分享工具；没有 `Guided*.kt`、两套新规则或新 pipeline 文件告警。未升级依赖或添加 suppression。报告：`app/build/test-results/testDebugUnitTest/TEST-*.xml`、`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/lint-results-{debug,release}.{xml,html,sarif}`。

本地 APK：`app/build/outputs/apk/debug/APA-v0.3.0-debug.apk`（SHA-256 `A94974BB80785DB097491C591B2EB7361A158BDC220BB7422821425792ACC28E`）；已编译仪器 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`（`4F12741BC663B10F8FBFD3F7DF8B615BA125374807EA290C4D9AA507E037A26C`）。均未安装／发布。

**设备验证：未运行**。发布前需另行授权执行 OEM／Root 输出、真实 Keystore、MainActivity 回调时序、旋转／后台返回／退出／取消／删除／进程死亡、小屏／大字体／IME／滚动／读屏与中英文，以及实际 Android 解码器及两种协议仪器测试。分别用缺失和冲突样本做人工可用性验收，确认用户能说明未知项与下一步。

**真实模型验证：未运行**。离线十二例是手工合成契约；格式 PASS 和合法 ID 不能证明准确，全部人工事实核查仍待执行。未来授权模型运行须保留精确载荷及原始响应，分别记录格式错误和人工事实错误的分母与未审数量，见 [guided-diagnostics-evaluation](guided-diagnostics-evaluation.md)。本轮不连接设备／服务、不下载依赖、不推送或发布；此开发副本验证不能替代主目录同步后的独立复验。

## 双语回答过滤与离线评测（2026-10-02）

- 基线 `7547b7c`，分支 `codex/bilingual-ai-evals`。补齐英文及混合语言的 L0/普通聊天高级动作过滤、已附报告重复索取过滤和英文健康任务字段清洗；按句段保留普通设置、否定性提醒与报告事实。
- 新增 19 项 JVM 回归。独立审查提出的应用停用、非 com 包名、普通后台数据设置、名词解释、混合语言否定、报告上传事件、项目符号及中文“报告提供了”均先复现失败再修复。过滤仍是保守词法规则，不保证拦截所有改写或证明证据准确。
- 全量 JVM **381 项，65 个套件，0 failure/error/skipped**；Debug 与仪器测试 APK 构建成功；Debug/Release Lint 各 0 错误、35 个既有警告。仪器测试未上机执行，远端 CI 未运行。
- 新增 12 个合成中英文场景（6 类风险）、标准库离线检查器和强制人工评分准则；CI 增加工具单测。Python **14 项通过**，包含 ISO 周日期先失败后修复的严格日期校验。
- 手工好样例退出 0：12/12 覆盖、0 个词法标记、12 项人工待审；手工坏样例退出 1：12/12 覆盖、4 例共 10 个标记、12 项人工待审。另 8 个语义错误样例仍可词法 CLEAR，明确展示自动筛查的局限。它们不是模型运行，不代表任何准确率。
- 真实评测要求保存精确系统提示词、最终模型消息、历史、参数、应用版本，以及分别保存原始/显示回答。本轮没有读取 Key、请求真实模型、安装手机、推送或发布。复核后的补丁无剩余重要审查意见。

## AI 定向回答约束（2026-10-02）

- 基线 `61034e7`，分支 `codex/focused-ai-output`。用户确认方向：回答优先点名有证据支持的对象，引用数值/单位/来源/窗口，只给一个优先动作，并说明副作用、验证与设置回退；不能靠加重语气伪装确定性。
- 中英文共用定向策略：区分总耗电、后台耗电与同窗口异常，服从本地裁决及最高建议级别；并列或证据不足时不强行选“元凶”，不编造置信度百分比、基线、阈值或设置路径。健康、速率、归因和普通设备问题分别约束；未附报告的自由聊天不套诊断格式。
- 英文健康任务和中文一致：不发送无关应用/耗电诊断正文；英文 L0 诊断正文不夹带 Scene 操作。速率证据不足不再转去解释无关的应用排行。
- 新增 7 项真实提示词输出契约测试：先复现 5 项失败，修改后补充速率重定向测试并复现其失败。全量 JVM **362 项，0 failure/error**；Debug 与仪器测试 APK 构建、双变体 Lint 通过，Lint 各 0 错误、35 个既有警告。
- 这些测试验证发送给模型的指令/数据边界，不证明真实模型一定遵守，也不等于诊断准确率提升。没有读取 Key、调用真实模型服务、执行仪器测试、推送或发布；真实回答效果仍需使用代表性报告与问题核对。
- 独立审查发现速率任务仍继承了后台排行/Scene 操作/主要耗电字段的旧指令。扩展 L2 回归并先复现失败后，将这些指令限定为归因任务，速率字段改为实测速率；审查复核没有剩余重要问题。

## 长对话、Root 采集与 CI 整合（2026-10-02）

- 基线 `ec83969`，分支 `codex/performance-root-ci`。长对话改用带稳定 UI 标识的 LazyColumn，初始最近 30 条、每次加载 30 条，内存与加密归档统一最近 100 条；阅读历史时不自动跟随新消息，提供返回最新入口。聊天页面及发送前检查从 MainActivity 拆到 AgentChatScreen。
- Root/普通硬件命令并行排空 stdout/stderr，采集输出限长；超时、取消、线程中断均清理进程/流，取消后不进入降级命令。界面提供停止读取，并阻止失效任务写回新会话状态。
- 中英文诊断统一结论、证据、样本与下一步；来源失败、覆盖缺口、未知原文和原始电量窗口保留，不把缺少证据表述为没有异常。
- 新增 GitHub PR/push JVM、双变体 Lint、Preview/仪器测试 APK 检查，以及独立 Android 16 模拟器任务。工作流仅完成静态检查，尚未推送或在 GitHub 运行；细节见 CI.md。
- 本地全量 JVM 355 项通过（0 failure/error）；Debug 与仪器测试 APK 构建成功，Debug/Release Lint 各 0 错误、35 个警告。最终审查后的复验以本节后续记录为准。
- 当前 adb 无设备、无 AVD、无系统镜像。仪器测试仅编译，未执行；Root 授权/OEM 输出、长对话滚动与字体放大、停止读取/旋转仍须上机确认。未调用真实模型服务、未覆盖手机正式版、未发布。
- 最终审查后复验：同一全量命令退出 0，63 个 JVM 套件共 355 项，0 failure/error；双变体 Lint 仍各 0 错误、35 警告，新增长对话与双语诊断 UI 测试已编译。审查修正自动滚动反馈、失效 L2 测试假设、懒加载离屏断言及测试会话隔离；模拟器配置启用软键盘。

审计日期：2026-09-16，实机记录核对至 2026-09-10。本次未发布、未覆盖手机正式版、未调用真实模型服务；经用户授权在手机安装独立 Preview 执行测试。

## 上机前可信度门控（2026-09-16，当前开发代码）

- 新增真实能力门控：L0 始终可用；L1 在尚未实现 Shizuku 服务连接与授权读取前不可选；L2 必须先在能力页成功读取 Root 数据，不能再把仅检测到 `su` 或用户点选等级当作已授权。
- 本地模式不再展示不会被本地分析消费的“应用报告”开关。在线发送说明现在列出等级报告、可选应用/使用习惯/系统耗电报告、同服务最近最多 12 条历史，并明确 Root 原始输出不发送但读取出的容量、循环、温度等摘要可能随 L2 等级报告发送。
- L0 响应保护补充 Magisk、KSU、LSPosed、Xposed、Zygisk、shell/命令等高级动作词，并按句段过滤，避免一个高级词删除同一行里无关的普通设置建议。
- TDD 红灯分别复现策略类缺失、附件策略缺失、同一行高级建议漏网，以及“尚未读取 Root 被误当作无错误读取”；修复后完整 JVM 共 **243 项，49 个测试套件，0 failure、0 error、0 skipped**。
- `assembleDebug`、`assembleRelease`、`lintDebug`、`lintRelease`、`compileDebugAndroidTestKotlin` 均成功。Debug/Release Lint 各 0 错误、26 个警告；本轮只完成仪器测试源码编译，没有连接设备运行。
- Debug APK：`APA-v0.2.0-debug.apk`，SHA-256 `714B30DF67ADD324D1480F176A24842689BA5343F682D3B31B1E9C894552447F`。Release APK 仅作为本地构建产物验证，不代表已签名发布。
- 待真机确认：Root 成功读取后 L2 解锁、拒绝/超时后保持 L0、旧会话高级等级自动降级、窄屏下禁用按钮与说明文案布局，以及在线发送前取消系统耗电报告。

## 普通用户续航观察（2026-09-14，当前开发代码）

- 新增两点式本地观察：拔电后记录起点，至少 30 分钟后记录终点，计算区间电量下降与平均 `%/小时`；不把结果冒充应用归因。
- JVM 共 195 项，0 failure、0 error。新增覆盖有效速率、测量可靠性分级、起止及中途充电、用户确认、未知电源状态、短区间电量反升、无可测下降、重复开始、过早结束继续观察、单调时钟、磁盘检查点编解码与恢复后固定粗略，以及电池语境、误匹配和混合速度/归因问法。
- `compileDebugAndroidTestKotlin`、`lintDebug`、`assembleDebug` 成功；Lint 0 错误，既有告警保留。新增仪器测试源码覆盖入口和有效结果展示，但本轮没有安装 APK 或运行真机仪器测试。
- 续航观察回答固定在本机生成；即使配置了模型 Key，也不会自动上传精确观察时间或电量轨迹。系统 Bug Report / Root 诊断摘要仍遵循独立的用户选择边界。
- 进行中的观察只把一个起点和已检测到的充电标记写入独立本地偏好；不保存轨迹、不依赖常驻服务、不进入系统备份。应用进程存活时由 Application 级接收器记录插电，进程回收或重启后恢复时忽略开机相对时钟，结束前强制询问是否充过电并固定降级为粗略。待真机验证后台 30 分钟以上、中途插拔电源、进程回收、重启、字体放大和任务移除。
- Bug Report 主报告改为流式逐行解析，只保留限长后的耗电白名单段落；导入回归覆盖标准主报告、FS 二进制附件、带载荷目录、`systrace.txt`、超过旧 8 MiB 上限的主报告、Android 9+ CRITICAL/HIGH 服务段落、旧式 ZIP MIME、重命名文件，以及既有的路径穿越、嵌套压缩包、条目数量、大小、损坏和取消边界。主报告与跳过附件使用独立预算，跳过任何大条目时也会持续检查取消信号。原始报告仍不落盘、不发送模型。
- 真实格式解析回归覆盖 AOSP 的 `Uid/UID` 耗电行和 CPU `fg/bg/fgs` 状态时长、含 `*ACTIVE*` 的新旧 Alarm Stats 包级嵌套块、`dumpsys package` 的 Package/userId/appId 块与工作资料 UID，并从合成完整 Bug Report 一路验证到本地排行与可回退 Scene 建议。额外覆盖超长 UID、时长和累计计数，确认无崩溃、无整数回绕且溢出状态不能被后续数据洗白。

### 明日 Android Studio 真机手测

1. 手机连接 Android Studio 后直接运行 `app` 的 Debug 变体；确认安装的是独立的 `APA Preview / 0.2.0-preview`，不用覆盖稳定版。
2. 进入 **Agent → 选择报告 → 系统耗电诊断**，确认“导入系统报告”在最前；展开 **辅助：粗略续航测量**。拔掉充电器后开始；若仍在充电，必须拒绝建立起点。
3. 正常开始后立刻点“结束观察并计算”，必须先出现“期间是否充过电？”；选择“没有充过电”后，因不足 30 分钟应继续观察且保留原起点。
4. 取消并重新开始，退出 APA，期间插一次电再拔掉；重新打开并结束时必须判定“观察期间检测到连接电源”，不能给出 `%/小时`。
5. 再做一次干净观察：开始后从最近任务划掉 APA，至少 30 分钟且下降至少 1 个百分点，再打开并选择“没有充过电”。结果应显示平均掉电速度、可靠性为“粗略”，并提示依赖用户确认。再以“充过电或不确定”结束一轮，必须作废。
6. 输入“今天耗电快吗”或“电量一小时掉了 5%，正常吗”：应直接出现 `LOCAL · BATTERY OBSERVATION` 的本地确定性回答，且可以“复制全文”，不能发出模型请求。
7. 输入“哪个软件最费电”：没有系统诊断时必须要求导入 Bug Report；仅有续航观察不能回答应用归因。输入“哪个软件占内存最多”或“游戏帧率掉了”则不应误触耗电拦截。
8. 输入“为什么今天耗电这么快”：这是速度加归因的混合问题，应提示拆成两个问题，不能漏答其中一半。
9. 展开“怎么生成系统报告”，检查小米拨号码、标准 Android 开发者选项路径和敏感数据提示；这里只读说明，不应自动打开拨号盘、修改系统或上传文件。
10. 用开发者选项生成一份真实 Bug Report ZIP，必要时把副本改成不以 `bugreport` 开头的名字；导入后应显示“本地读取完成”，并至少出现一个可用来源。再选一张图片或 PDF，必须拒绝为不支持格式。

## 系统耗电诊断分支验收（2026-09-11）

- 分支：`codex/system-power-diagnostics`；验收提交前代码：`bb5d2f9`。
- 命令：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`，Gradle 退出码 0。
- JVM：91 项，0 failure、0 error、0 skipped。覆盖报告上限、解析、关键包保护、Root 白名单、超时/截断分类、部分成功、会话选择和云端提示边界。
- Debug Lint 成功；Debug APK 生成；Android 仪器测试源码编译成功。源码搜索未发现 `batterystats --reset`、force-stop、卸载、`settings put` 或 sysfs 写入命令；Manifest 未增加存储权限。
- `adb devices` 无设备。本分支没有执行 Root 授权、OEM dumpsys 格式、真实采集时长、界面滚动和 connectedDebugAndroidTest；不得将以前的 Xiaomi 测试结果算作本功能的真机通过。
- 未请求真实模型服务、未读取用户 Key、未改变 Scene 或系统设置。第一版只显示诊断和手动建议。

## 模型响应可靠性修复（2026-09-10，当前代码）

- JVM：89 项，0 failure/error/skipped；新增 9 项测试。异常正文和密钥对象输出的两项测试在旧代码下失败，修复后通过。
- Debug/Release Lint 各 0 错误、23 个已有警告；Preview 与 androidTest APK 构建成功。
- 合成流验证中文 UTF-8、恰好 1 MiB、超限只多读 1 字节即停止、非法 UTF-8、读失败后关闭；错误测试验证认证/额度/服务异常分类及本地提示。
- 未请求真实服务，未做手机或完整 HTTP 服务端联调；现有 15 项仪器测试本轮仅编译。页面状态修复的 4 项新增真机回归仍待手机连接。
- 响应限流、脱敏不代表实现了网络取消；阻塞 HTTP 取消和协议/状态码端到端契约测试仍是技术债。
- 独立审查因额度限制中断，没有取得独立审查结论；主线程已复核实际调用路径、错误映射及流关闭路径，不将此记为独立审查通过。

## 页面状态修复（2026-09-10，前轮代码）

- JVM：80 项，0 failure/error/skipped，新增 3 项请求中断/重试代次保护及在线重试提示测试。
- Debug/Release Lint 和 Preview/androidTest 构建成功，Lint 各 0 错误、23 个已有警告。
- 仪器测试覆盖草稿/等级/系统耗电诊断跨切页和重建、默认不发送、本地对话及清空、分析中断和设置状态。当前仅完成编译，本分支没有连接设备，不能把前轮真机结果当作新诊断的实机证据。
- 会话内容存于 Activity 级 ViewModel；页面 SaveableStateHolder 只保存滚动等轻量状态。API Key 和大段报告/对话不写入 SavedState Bundle。彻底退出和进程回收不恢复会话。
- 独立审查后修正了自动滚动覆盖恢复位置、设置页恢复后的服务/凭据槽位错配。网络层仍使用阻塞 HTTP，重建时会话停止接收旧结果，但旧连接可能继续至响应或超时；在线中断提示明确重试是新请求，不声称已撤回请求。真实网络取消测试仍待传输层改造。
- 测试先添加；首轮 JVM 因新状态类尚未存在而编译失败，不将其描述为已在旧版设备复现。手机连接后需运行完整 connectedDebugAndroidTest（现 15 项），保持独立 Preview 前台。

## 前轮架构及发布收尾结果

| 检查 | 结果与限制 |
| --- | --- |
| 基线 JVM 测试 | 52 项，包含一个模板加法测试；初始任务成功 |
| 最终 JVM 测试 | **77 项，0 failure、0 error、0 skipped**；本次收尾在前轮 75 项基础上新增 2 项使用习惯范围测试 |
| Debug / Release Lint | 任务均成功，各 0 错误、23 警告；不声称零警告 |
| Debug APK | assembleDebug 成功，预览包名 com.aegis.apa.preview |
| 仪器测试 APK | 编译成功；2509FPN0BC / Android 17 本轮 **11/11 通过**，0 failure/error/skipped；启动前台需人工辅助，见下文 |
| 缺少发布密钥的源码检查 | 指定不存在的签名配置后，JVM/Lint/Debug/androidTest 编译全部成功 |
| 缺少发布密钥的打包 | aggregate assemble 与 bundleRelease 被签名检查按预期拒绝；不是成功产出 Release |
| Gradle 配置缓存 | 检查和负向打包均可使用；修复了第一次负向验证中发现的脚本对象捕获问题 |
| 差异检查 | git diff --check 无空白错误 |

此前测试受锁屏、其他应用前台影响，主动终止过的 Process crashed 不作为 APA 自发崩溃证据。2026-09-10 收尾轮最终 JUnit XML 记录 11 项全部通过：4 项备份资源契约、2 项 FileProvider、5 项 UI。Gradle `connectedDebugAndroidTest` 为 BUILD SUCCESSFUL，耗时 4m 8s。设置返回使用真实系统 Back；键盘测试保持报告选择区展开，验证真实 IME、输入框距键盘 0–48 dp、发送按钮和收键盘后导航恢复。

这轮在 UI 测试启动时，预览 Activity 数次未自动进入前台；通过匹配 MAIN/LAUNCHER 的 adb 启动预览版后继续。没有代替脚本点击设置返回、输入或断言步骤，但不能称为无人干预运行。自动启动兼容性仍待独立模拟器/设备验证。未调用真实模型、未覆盖正式版；测试后移除本轮临时 Preview/测试包，保留正式安装与原有配置。

## 回归保护

| 测试 | 保护的行为 |
| --- | --- |
| DevicePublicNameTest / DeviceIdentityTest | 错误厂商不能套用映射；系统占位符、土耳其 locale、原值保留、数据库失败、重复品牌、未知 codename |
| PowerDiagnosticParserTest | UID/包名映射、共享 UID、来源截断、power/idle/thermal 保守解析 |
| PowerDiagnosticFindingEngineTest | 多证据置信度、单证据只观察、关键包不成为冻结候选 |
| PowerDiagnosticReportTest / PowerDiagnosticPromptTest | 事实与建议分离、16 KiB 上限、缺失来源、默认不发送和显式附件边界 |
| RootCommandRunnerTest / SystemPowerDiagnosticsCollectorTest | 固定只读白名单、失败输出不泄露、截断、部分成功及完整来源遍历 |
| CloudHistoryPolicyTest | 本地 Scene/提问排除、切换服务商隔离、先过滤后截取历史 |
| RootBatteryParserTest | sysfs 单位、带符号电流、数字循环数、非法读数、0 次循环 |
| TelemetryBoundaryTest | 未知电量、无效 RAM/存储不评价正常 |
| Level0ReportBuilderTest | 直接省略开关验证生产默认值；已授权的合成使用标签在关闭时排除、开启时保留，基础报告不含高级等级 |
| BackupPolicyTest | 实际打包 manifest 禁用备份，旧版云备份及 Android 31+ 云备份/设备迁移排除凭据文件；不触发真实恢复 |
| 既有 Usage/Profile/Prompt/Key 等测试 | 保留原有回归保护；完整列表见 app/src/test |

本次调度测试先复现 2 项失败，默认报告范围测试先复现 1 项失败；修复后通过。前轮新增异常输入用例曾在原行为下出现 9 项失败；独立历史策略测试再复现 3 项失败；Root 无效节点测试再复现 1 项失败。修复后最终全部通过。类型、格式等兼容测试也覆盖本轮迁移，不把所有新增测试都描述为曾复现缺陷。

## 仪器测试维护

`StabilityUiTest` 使用语义输入框和系统耗电诊断入口，断言旧 Scene CSV 入口不存在且诊断默认不发送；设置返回采用真实 Back 事件。新增诊断用例尚未在设备执行。

`ExampleInstrumentedTest` 已用真实 FileProvider 读文件及拒绝缓存目录外文件测试替换模板包名断言，两项已在上述设备通过。测试只创建/删除自己的合成临时文件。

待覆盖：旋转与页面往返丢状态、长摘要/大字体/横屏及其他屏幕尺寸、独立无人干预启动、完整分享 Intent 流程、所有权限拒绝/撤销、Root 大输出/超时/取消、OEM 备份恢复、真实网络两种协议、各品牌机型。

## 可复现命令

使用 Android Studio 自带 JBR 和匹配 SDK。在 Windows PowerShell 中：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat --offline --no-daemon --console=plain testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest '-Papa.signingProperties=missing-audit-signing.properties'
```

签名缺失负向测试（**预期退出码 1**，原因必须是缺签名配置，不能是 Kotlin 编译或配置缓存错误）：

```powershell
.\gradlew.bat --offline --no-daemon --console=plain --continue assemble bundleRelease '-Papa.signingProperties=missing-audit-signing.properties'
```

`missing-audit-signing.properties` 应不存在，不要在这里填写真实凭据。正式签名配置不进 Git，负向测试不读取用户 Key。当前项目未注册 `testReleaseUnitTest`，不要把不存在的任务写入验证流程；Release 编译由 lintRelease 检查。

有独立测试设备或模拟器且启用软键盘后：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

该命令会安装 APA Preview 和测试包，使用独立测试环境执行。不要为了自动化测试覆盖正式安装或删除用户数据。首次无依赖缓存时去掉 `--offline`。

报告路径：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/test-results/testDebugUnitTest/`、`app/build/reports/lint-results-debug.html` 和 `lint-results-release.html`。生成报告不提交 Git。

## 发布前门槛

关闭 [TECH_DEBT](TECH_DEBT.md) 的 P1 项；完成独立设备/权限/配置变更测试；递增版本；用发布密钥生成 APK/AAB；验证签名和 SHA-256；记录实际模型服务验证范围；对照发行标签更新 README/CHANGELOG。不把本轮预览 APK 当作已审定的下一公开版本。
# Chat reliability — 2026-10-02

Source baseline: `7756a7b`, source version `0.3.0` / `versionCode 5`. Implementation on `codex/chat-reliability`.

Validated with Android Studio JBR, installed SDK 36.1 and cached Gradle dependencies:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

- 332 JVM tests; 0 failures, 0 errors, 0 skipped.
- Debug preview APK and Android test APK built successfully.
- Debug lint: 0 errors, 34 warnings.
- Encrypted archive: provider/scope round-trip, last-100 retention, seven-day expiry, malformed/truncated/oversized archives, ciphertext tampering, failed encryption preserving prior history, and deletion on clear.
- Behavioral regressions reproduced before their fixes: queued clear must precede a subsequent restore; cancellation during lazy output-stream acquisition must send zero body bytes. Both pass after correction.
- Transport contracts: separate OpenAI-compatible/Anthropic authentication, successful connection cleanup and HTTP failure without reading or echoing an error body. No real service was contacted.

Limitations: `adb devices -l` reported no devices. Android Keystore, OEM atomic replacement, process termination/rotation, keyboard/layout and real network cancellation remain device checks. Saves run asynchronously; the UI shows saving/errors, and killing the process before completion can lose the latest change. Retention is enforced when reopening history; no background service deletes an unused archive at an exact seven-day wall-clock instant. A stopped request may already have reached the provider; retry can incur another request. Drafts and report objects are not restored, so retry uses current report choices.

## Earlier validation

## Diagnostic verification — local phase A (2026-10-02)

Task 6 connects the local Compose flow without a model Key: retained-summary preview → baseline → one manually confirmed adjustment/rollback note → current-diagnostic retest preview → original values/refusal reasons → close/delete. Synthetic instrumentation fixtures cover UNKNOWN declarations, affirmative declarations unable to supply missing windows, explicit old-retest removal, damaged-archive clear authorization, saving/failed-save indicators, and bounded lazy scrolling at 320dp / 1.5x font with a focused text field/IME. They use a temporary test archive and synthetic AES key, never a personal archive.

Required offline validation (Android Studio JBR, cached Gradle):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --offline --console=plain
```

Preimplementation test compilation failed at the expected missing `VerificationPanel` / `onOpenVerification` APIs. Compilation after implementation succeeds. This is test-first compile evidence, **not instrumentation runtime RED/GREEN**. No device/emulator install or connected test is authorized or performed; layout, IME, Keystore lifecycle, and real OEM report handling remain pending device checks. Production statistics bounds are unknown, so no real adjustment-effect measurement is claimed. AI explanation/model evaluation is deferred.

Both full offline runs succeeded: 484 JVM tests, 0 failures/errors/skips, and debug preview / Android test APKs built. Final targeted build and lint after switching dialog sizing to `LocalWindowInfo` succeeded; debug and release lint each report 0 errors / 35 existing warnings. Unit XML and lint HTML/XML remain under the report paths documented above.

## Diagnostic verification — optional explanation (2026-10-02)

Task 7 implements a separate request with a fixed system prompt and one untrusted user payload, without chat history or the full device snapshot. Its explicit scope preview requests credentials only on confirmed send. All free-text title/scenario/change/rollback/side-effect notes default off; the exact UTF-8 attachment limit is 16 KiB with rejection rather than truncation. Model explanations remain in memory and never enter either archive. Local gates remain visible and errors do not modify the verification record; lexical capability filtering is reused without a semantic safety guarantee.

TDD evidence: the first focused payload/controller run executed 6 tests and failed 5 against minimal API scaffolds (missing notes projection, local reasons, byte boundary, credential failure, provider execution). A later queued-deletion regression failed at `expected 0 provider calls, actual 1`, before the pre-worker identity/revision/capability/language check. Final focused tests pass 9/9. Late-reply tests assert an active request and run fake provider work before withholding publication, then invalidate selected id/deletion/revision/cancel/dispose; separate tests cover capability/language changes, expired/no key, oversize and duplicate click.

Final offline command above succeeded: **493 JVM tests, 0 failures/errors/skips**; debug APK and Android test APK built; debug and release lint each **0 errors, 35 existing warnings**. Python `-B -X utf8 -m unittest discover -s scripts/ai_eval -p test_*.py -v` passes **14/14**. The good/bad synthetic evaluation fixtures cover all 14 ids, preserve 14 manual PENDING verdicts, and include Chinese/English overlapping-window answers falsely claiming causal savings that remain lexical CLEAR.

`VerificationExplanationProviderTest` uses Android's org.json with injected fake post and synthetic credentials. It asserts both request protocols, no history, separated system/user roles, 401/429, missing/expired key, unknown provider/protocol, empty/invalid/oversized response and cancellation. `VerificationExplanationUiTest` uses fake credentials/provider/executors and asserts preview before credential access/post, opt-in notes, no-key/read-failure errors and visible local gate. These **compile only**; neither protocol nor Compose instrumentation was executed on a device. No real Key was read, no provider was called, and no device install, live eval, push or release was performed. Keystore/OEM lifecycle, actual layout/IME, runtime JSON behavior and model compliance remain pending separate authorized checks. A cancelled accepted request may still be processed by the provider.

## Diagnostic verification — final review fixes (2026-10-03)

The final offline command above completed successfully: **500 JVM tests in 75 suites, 0 failures/errors/skips**, debug and Android test APKs built, debug/release lint each **0 errors / 35 existing warnings**. The bundled Python runtime ran `-B -X utf8 -m unittest discover -s scripts/ai_eval -p test_*.py -v`: **14/14 passed**.

Two real synthetic projector → controller → encrypted store flows first failed at retest save with `LIMIT`, after baseline and manual change saved. The complete 20-group fixture (`com.example.app01` through `20`, all nine finite PARSED app metrics plus overall power, production-shaped unknown bounds) now persists all **181 metrics** in both summaries; the plaintext record is **26,083 bytes**. An absent selected target reserves its slot and retains at most **19 other groups / 172 retest metrics**, with no invented target value; that record is **25,462 bytes**. Reopened records equal the original projected summaries, including values, units, statuses, windows, context and fingerprints.

The schema v1 compact metric section uses an explicit marker and bounded package/window dictionaries; stable enum string IDs remain. A golden intermediate-preview v1 archive still decodes, reloads encrypted and remains editable. This is preview-format compatibility, not a released-format migration claim; no real device archive was accessed. Strict UTF-8, negative/unknown references, unknown markers, dictionary/metric counts and trailing garbage remain bounded/rejected. The existing exact **49,152-byte** record acceptance / **49,153-byte** rejection and 20-record / 1 MiB bounds, retention, tamper, key failure, clear and tombstone tests remain passing.

Additional regressions cover a mismatched map-key/window source and bilingual tiny nonzero raw values/rates/deltas/percentages. The new failed-baseline Back → visibly unsaved list entry → reopen Compose regression **compiled only**, along with the existing instrumentation suite; device execution and visual/IME/Keystore/OEM checks remain pending. No real Key, network/model call, device/install command, push or release occurred in this fix wave.

### Main-project delivery verification (2026-10-03)

After the approved branch was locally synchronized to `D:/AndroidPersonalAgent`, the same full offline command succeeded again (33 seconds): **500 JVM tests / 0 failures, errors or skips**, both APKs built, and the Python tool suite passed **14/14**. This delivery-directory lint run reported **0 errors / 36 warnings per variant**, versus 35 in the isolated checkout. Report comparison identified only availability warnings for the unchanged device-marketing-name dependency and Kotlin Compose plugin; the two dependency configuration files have no feature-branch changes. No dependency upgrade or warning suppression was made. Actual instrumentation/device/model execution remains pending.

## 0.3.1 候选代码留存（2026-10-10）

用户确认本轮只整理和保留下一正式版本，不立即发布。配置更新为 `0.3.1` / `versionCode 6`，Debug 显示 `0.3.1-preview`。发布时间与剩余验收见 [RELEASE_0.3.1](RELEASE_0.3.1.md)。

在当前 Android Studio 副本 `C:/Users/ROG/Documents/APA/work/current` 上重新运行：

```powershell
./gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest --offline --console=plain
& 'C:/Users/ROG/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B -X utf8 -m unittest discover -s scripts/ai_eval -p 'test_*.py' -v
```

- Gradle `BUILD SUCCESSFUL`；95 个 JVM 套件、750 项测试，0 failures/errors/skips。
- Python 离线评测工具单测 18 项通过，不调用真实模型。
- Debug Lint：0 错误、35 警告；Release Lint：0 错误、33 警告。
- Debug 与 AndroidTest APK 编译成功；APK 元数据确认 `versionCode 6`、`versionName 0.3.1-preview`。
- 预览 APK：`app/build/outputs/apk/debug/APA-v0.3.1-debug.apk`，SHA-256 `20588E2CCA56A8FD7C6261276C1C74CBDA94E1E0AC698CCA1863CA7255DBAB5D`。
- `git diff --check` 通过。此轮未安装手机、未执行真机／真实模型测试、未生成签名正式 APK、未创建正式标签或 GitHub Release。

## K60 Pro 报告兼容修复（2026-10-10）

- 只读检查用户手机当天报告的 ZIP 结构和白名单字段：`actual drain: 962-1006`，同窗口 `Computed drain: 1.00`；硬件附件为 `bootlog_copy.txt`，UFS 厂商字段为 `SAMSUNG`、规格为 4.0。原始归档和日志正文没有存入仓库。
- 区间值之前回退到 1.0 mAh，导致错误速率、排行分母和冲突提示。六项新增回归先全部失败，修复后通过；区间保留上下限，占比保留范围，超过总量上限才标记冲突，复测标量归档不保存区间端点为精确值。
- 复制版 bootlog／文本厂商端到端测试先因缺少硬件段落失败，再修复；RAM 厂商／类型没有找到可识别证据，保持未知。
- 独立只读审查无阻断问题，提醒科学计数法负指数会被区间检查拒绝；新增 `1e-3` 回归先失败后修复。
- 最终命令：`testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease --offline --console=plain`，使用现有本地正式签名配置。`BUILD SUCCESSFUL`；96 套件、758 项 JVM 测试，0 failures/errors/skips；Debug Lint 0 错误／35 警告，Release Lint 0 错误／33 警告。
- 最终正式 APK SHA-256：`0E5FCF70593DF70010882207B38257E436529ADD86C61509CE2F22F1C19F4344`；签名验证通过，发布证书不变。
- `adb install -r` 在 K60 Pro 成功，保留应用数据；MainActivity 冷启动 `Status: ok`，安装版本 0.3.1 / versionCode 6。未在手机 Download 保留 APK，未公开发布。
- 用户原报告的重新导入、厂商实际页面和新 AI 回答仍需人工复测；本轮没有执行仪器测试或真实模型评测，不能把安装启动视为完整验收。

### K60 Pro 身份匹配补验（2026-10-10）

- 用户反馈修复后仍显示报告无法匹配。定位为标准标题 `SYSTEM PROPERTIES (getprop)` 未被识别，导致身份白名单为空；新增嵌套 ZIP 回归先失败后通过，覆盖匹配／不匹配、序列号排除及段落外型号不被读取。
- 使用生产提取器、身份匹配和耗电／硬件解析器读取用户提供的完整 `bugreport-2026-10-10-175802.zip`，端到端验证通过：本机身份匹配、三星 UFS 4.0、实际耗电 962–1006 mAh；RAM 厂商仍无证据。原始报告没有加入 Git。
- 同一全量 Gradle 命令通过：759 项 JVM 测试，0 failures/errors；Debug/Release Lint 与三个 APK 编译通过。
- 签名验证通过，证书不变；最终 APK SHA-256 `44C409A53A0EEF5BBED7F118F472B487B42909034A8308A35C68CF179E92ECCA`。K60 Pro 覆盖安装成功，启动 `Status: ok`，保留数据，不在手机 Download 放安装包。
- 手机界面需重新导入原 ZIP 并确认“是本机报告”；未执行自动化仪器测试或真实模型评测，不把解析器验收替代人工页面验收。
