# 测试与验证记录

审计日期：2026-09-09，实机记录核对至 2026-09-10。本次未发布、未覆盖手机正式版、未调用真实模型服务；经用户授权在手机安装独立 Preview 执行测试。

## 结果

| 检查 | 结果与限制 |
| --- | --- |
| 基线 JVM 测试 | 52 项，包含一个模板加法测试；初始任务成功 |
| 最终 JVM 测试 | **75 项，0 failure、0 error、0 skipped**；新增 24 项有效测试，删除 1 项模板加法测试 |
| Debug / Release Lint | 任务均成功，各 0 错误、23 警告；不声称零警告 |
| Debug APK | assembleDebug 成功，预览包名 com.aegis.apa.preview |
| 仪器测试 APK | 编译成功并在 2509FPN0BC / Android 17 执行；最新一轮日志仅有前 5 项完成，不能认定最终全套通过 |
| 缺少发布密钥的源码检查 | 指定不存在的签名配置后，JVM/Lint/Debug/androidTest 编译全部成功 |
| 缺少发布密钥的打包 | aggregate assemble 与 bundleRelease 被签名检查按预期拒绝；不是成功产出 Release |
| Gradle 配置缓存 | 检查和负向打包均可使用；修复了第一次负向验证中发现的脚本对象捕获问题 |
| 差异检查 | git diff --check 无空白错误 |

最初没有设备；用户随后连接并解锁手机。运行中遇到锁屏和其他应用占前台，测试曾暂停并被主动终止（因此产生 Process crashed 结果，不据此判断 APA 自发崩溃）。在人工恢复 Launcher 前台的一轮中曾有 7/7 成功，但随后修正了设置返回脚本，须以修正后的结果为准。2026-09-10 核对的最新日志：两个 FileProvider 测试、设置构建标识、Scene 导入入口、本地分析重新采样已通过；设置返回测试已开始但未记录结束，键盘测试未见最终结果。待手机保持空闲时完成无干预整轮验证。

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
| 既有 Usage/Profile/Prompt/Key 等测试 | 保留原有回归保护；完整列表见 app/src/test |

新增异常输入用例曾在原行为下出现 9 项失败；独立历史策略测试再复现 3 项失败；Root 无效节点测试再复现 1 项失败。修复后最终全部通过。类型、格式等兼容测试也覆盖本轮迁移，不把所有新增测试都描述为曾复现缺陷。

## 仪器测试维护

`StabilityUiTest` 使用语义输入框和当前 Scene 导入入口；设置返回采用真实 Back 事件，不再重新启动不匹配的 Activity。增加软键盘弹出/收回、输入栏可见、距键盘不超过 48 dp、导航恢复和草稿保留断言，不点击在线发送。

`ExampleInstrumentedTest` 已用真实 FileProvider 读文件及拒绝缓存目录外文件测试替换模板包名断言，两项已在上述设备通过。测试只创建/删除自己的合成临时文件。

待覆盖：旋转与页面往返丢状态、报告展开/长摘要/大字体/横屏、输入框距键盘的精确间距、完整分享 Intent 流程、所有权限拒绝/撤销、Root 大输出/超时/取消、OEM 备份恢复、真实网络两种协议、各品牌机型。

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
