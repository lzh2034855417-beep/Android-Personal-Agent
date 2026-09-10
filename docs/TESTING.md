# 测试与验证记录

审计日期：2026-09-09，实机记录核对至 2026-09-10。本次未发布、未覆盖手机正式版、未调用真实模型服务；经用户授权在手机安装独立 Preview 执行测试。

## 页面状态修复（2026-09-10，当前代码）

- JVM：80 项，0 failure/error/skipped，新增 3 项请求中断/重试代次保护及在线重试提示测试。
- Debug/Release Lint 和 Preview/androidTest 构建成功，Lint 各 0 错误、23 个已有警告。
- 新增 4 项仪器测试：草稿/等级/Scene 跨切页和重建、本地对话及清空跨重建、待完成分析重建后的中断提示及重试入口，以及设置页服务选择恢复后对应凭据槽位匹配（仅隔离合成凭据）。仅编译通过，当前没有连接设备，未执行这些新增用例；下方前轮 11/11 不能作为本次状态修复的实机证据。
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
| SceneCsvParserTest | 严格数字、越界百分比、科学计数法、显式单位、重复语义列、错列数、BOM/中文/引号 |
| SceneCsvInputTest | UTF-8 中文无损读取、超限拒绝、异常 UTF-8 拒绝 |
| SceneReportBuilderTest | 时间不足不推断，中途电量回升不当成持续耗电，区间速率保留限制 |
| CloudHistoryPolicyTest | 本地 Scene/提问排除、切换服务商隔离、先过滤后截取历史 |
| RootBatteryParserTest | sysfs 单位、带符号电流、数字循环数、非法读数、0 次循环 |
| TelemetryBoundaryTest | 未知电量、无效 RAM/存储不评价正常 |
| Level0ReportBuilderTest | 直接省略开关验证生产默认值；已授权的合成使用标签在关闭时排除、开启时保留，基础报告不含高级等级 |
| BackupPolicyTest | 实际打包 manifest 禁用备份，旧版云备份及 Android 31+ 云备份/设备迁移排除凭据文件；不触发真实恢复 |
| 既有 Usage/Profile/Prompt/Key 等测试 | 保留原有回归保护；完整列表见 app/src/test |

本次调度测试先复现 2 项失败，默认报告范围测试先复现 1 项失败；修复后通过。前轮新增异常输入用例曾在原行为下出现 9 项失败；独立历史策略测试再复现 3 项失败；Root 无效节点测试再复现 1 项失败。修复后最终全部通过。类型、格式等兼容测试也覆盖本轮迁移，不把所有新增测试都描述为曾复现缺陷。

## 仪器测试维护

`StabilityUiTest` 使用语义输入框和当前 Scene 导入入口；设置返回采用真实 Back 事件，不再重新启动不匹配的 Activity。增加软键盘弹出/收回、输入栏可见、距键盘不超过 48 dp、导航恢复和草稿保留断言，不点击在线发送。

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
