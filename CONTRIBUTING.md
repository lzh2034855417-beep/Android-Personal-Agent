# Contributing to APA

感谢你愿意改进 Android Personal Agent。APA 是一个本地优先、证据驱动的 Android 诊断实验项目；功能数量不能优先于数据含义、用户知情和权限边界。

## 开始之前

1. 搜索现有 Issues、[ROADMAP](ROADMAP.md) 和 [TECH_DEBT](docs/TECH_DEBT.md)。
2. Bug 请使用 Bug report 表单；新能力先说明真实使用场景、所需数据与权限风险。
3. 不要在 Issue、PR、测试夹具或提交中放入 API Key、签名文件、完整 Bug Report、Root 原始输出、账号数据或唯一设备标识。
4. 结构化遥测变更先阅读 [DATA_MODEL](docs/DATA_MODEL.md)；架构背景见 [ARCHITECTURE](docs/ARCHITECTURE.md)。

## 本地验证

项目要求 Android 8.0 / API 26+，使用仓库中的 Gradle Wrapper。当前维护环境使用 Java 21 和 Android SDK 36.1。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest '-Papa.signingProperties=missing-local-signing.properties'
```

macOS/Linux 使用 `./gradlew` 执行同一组任务。正式签名材料不进入仓库，贡献者不需要发布密钥。

## Pull Request 要求

- 一次 PR 聚焦一个问题，说明包含和明确不包含的范围。
- 行为修复或新功能应附回归测试；配置和文档变更应给出对应的实际验证结果。
- 区分 JVM、Lint、构建、仪器测试、真机手测和真实模型服务验证，不要统称“全部测试通过”。
- 新字段必须保留未知状态、采样时间和数据来源；缺失值不能伪装成零。
- 模型不能生成并直接执行任意 Shell。Root/Shizuku 操作必须是固定、有限、可审计的能力，并在需要时由用户明确确认。
- 原始系统报告与 Root 输出默认留在本机；新增发送内容必须在界面和文档中清楚列出。

English contributions are welcome. A PR should explain its scope, verification evidence, untested paths, data sources and permission/privacy impact. The requirements above apply regardless of language.
