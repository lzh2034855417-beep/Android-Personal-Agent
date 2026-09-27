# 测试与验证记录

## v0.2.0 正式发布验证（2026-09-27）

- 发布范围固定为 `v0.2.0-rc1` 的源码，加上稳定版 Changelog 与 README 元数据；不包含后续 Root 可靠性、友好应用名称或硬件评分开发。
- `testDebugUnitTest` 共 **243 项，0 failure、0 error、0 skipped**；`lintDebug` 与 `lintRelease` 各 0 错误、25 个非阻断警告。
- `assembleDebug`、`assembleDebugAndroidTest` 与 `assembleRelease` 均成功；本轮没有运行 `connectedDebugAndroidTest`，不得把测试 APK 编译成功表述为本轮真机自动化通过。
- 签名 Release APK：`APA-v0.2.0-release.apk`，包名 `com.aegis.apa`，`versionCode 4`，`versionName 0.2.0`。
- `apksigner verify` 通过：APK Signature Scheme v2、1 个签名者；签名证书 SHA-256 与 GitHub `v0.1.2` Release APK 一致，可覆盖升级。
- APK SHA-256：`4447EB0A785A57028AEBF6D56AA6820B9610DD45B17B6D965F4671ED54BB0685`。
- 本轮未调用真实模型服务。TD-02 的 OEM 备份/迁移实测和 TD-03 的仓库 CI/无人干预 UI 自动化仍未关闭；维护者接受这些已披露的兼容与流程风险，不把它们表述为已验证能力。

下文按日期保留开发期验证记录；若构建产物、警告数量或发布状态与本节冲突，以本节的正式发布验证为准。

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

## 通用发布检查

每次发布都必须逐项评估 [TECH_DEBT](TECH_DEBT.md) 的 P1 风险，完成与声明支持范围相称的设备/权限/配置变更测试，递增版本，用发布密钥生成 APK/AAB，验证签名和 SHA-256，记录实际模型服务验证范围，并对照发行标签更新 README/CHANGELOG。未关闭项必须在测试记录或发布说明中披露并由维护者明确接受，不能把预览构建或未测能力包装成已验证结果。v0.2.0 的具体决定与残余风险见本文件顶部正式发布验证。
