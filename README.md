# Android Personal Agent（APA）

用手机实际提供的数据，解释电池、发热和资源状态的 Android 开源实验项目。

[English](README_EN.md) · [路线图](ROADMAP.md) · [架构审计](docs/ARCHITECTURE.md) · [数据规范](docs/DATA_MODEL.md) · [技术债](docs/TECH_DEBT.md) · [测试](docs/TESTING.md)

> 当前源码版本号仍为 `0.1.1`，此工作分支包含未发布变更。下载版具体功能以 [GitHub Releases](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases) 的对应标签为准。本轮没有发布新版本。

## 能做什么

- **免 Key 本地查看**：设备、屏幕、电池即时状态、内存和存储；本地规则解释与可预览的 PNG 分享卡片。
- **设备识别**：保留系统型号，优先精确机型映射和离线名称库；展示名称来源，未知型号不猜商品名。
- **Agent 对话**：自行配置模型服务 Key 后，主动发送问题与报告；没有 Key 时使用有限的本地规则。
- **Scene CSV**：本机导入、解析采样区间摘要。目前不作为在线附件发送。
- **分级能力**：普通 API 可独立使用；Usage Access、Shizuku、Root 均为可选项。

APA 不会执行模型建议，也不修改系统调度、自动清理或刷机。单次快照不能判断真实电池寿命或确定是否该换电池。

## 快速使用

1. 安装 APK，打开“设备”查看本地数据，无需 Key 或 Root。
2. 在“Agent”选择报告并提问。没有有效配置时，结果来自本地规则，不是云端大模型。
3. 如需在线分析，进入“设置”，选择服务商，输入自己的 Key，点击“加密保存 7 天”，回到 Agent 主动发送。
4. 分享本地报告时，先检查“预览分享卡片”，再选择分享应用。

当前配置目录包含 DeepSeek、OpenAI、Anthropic、MiMo、Kimi；具体默认模型和接口见 [CloudProviderCatalog](app/src/main/java/com/aegis/apa/agent/CloudLlmProvider.kt)。默认字符串不保证账号可用，本轮未请求真实服务验证。

### 导入 Scene

在 Scene 中取得 CSV 文件并保存到手机，再打开 **APA → Agent → 选择报告 → 导入 Scene CSV**。看到“已导入 × 条样本”后查看摘要。

支持 UTF-8 CSV（含 BOM），上限 2 MiB；时间、电量、温度、电流、功耗、前台应用的常见中英文列名。数值单位放表头，支持 mA/uA/A 和 mW/W 转换。不是所有 Scene 版本都已实测兼容；不支持截图、PDF 或带换行字段。可先使用[合成示例](docs/examples/scene-example.csv)核对格式，详见[数据规范](docs/DATA_MODEL.md)。

## 能力等级与限制

| 等级 | 当前实现 | 不代表什么 |
| --- | --- | --- |
| Level 0 | 普通 API 快照；可选 Usage Access 当天应用前台时长估算 | 不是专业健康检测；前台时长不是亮屏时间 |
| Level 1 | Shizuku 安装检测和跳转 | 尚未连接 Binder 服务或取得高级能力 |
| Level 2 | 已知 Root 线索、主动读取部分电池节点/CPU 档案 | 不保证所有机型兼容；检测到管理器不代表已授权 |

应用列表受系统包可见性限制。Root 报告需手动重读并保留独立采样时间。本地分析目前主要消费基础快照，选择高级等级不代表本地规则已分析所有附件。

## 数据和隐私

- 打开应用及 Activity 恢复时，**自动在本机采集/刷新**基础设备与可见应用数据；开启 Usage Access 后还读取使用事件。这与发送范围是两件事。
- **点击在线发送**才请求所选模型服务；没有 APA 中转服务器。本次请求包含基础快照、所选 Level 报告和可选应用报告。
- Level 0 目前包含已授权的使用习惯汇总，“未勾选应用报告”不等于没有使用排行。报告范围预览与更细开关属于发布前待办。
- 本地消息（含 Scene 摘要）不进入在线历史；不同服务商之间不转发历史。同服务商最多携带最近 12 条允许发送的消息，可能包含之前回答中的数据。
- Key 按服务商用 Android Keystore AES/GCM 加密保存，默认本机有效期 7 天；过期不等于撤销服务商签发的 Key。
- `allowBackup=false` 已设置；细化系统迁移排除和恢复测试仍是技术债，不声称所有厂商迁移路径都已验证。
- 当前对话及导入只在内存，Activity 重建也可能清空；切换 Agent 页面可能丢草稿。

请勿在 Issues、日志或截图中公开 Key、账号信息或唯一设备标识。模型输出仅供参考；涉及维修和系统修改需独立核实。

## 开发与验证

Android 8.0 / API 26+；当前 compile SDK 36.1、target SDK 36。Gradle/插件版本固定在仓库；使用可运行该 wrapper 的 Android Studio JBR/JDK（本地验证使用 Android Studio 自带 JBR），并安装对应 Android SDK。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
```

macOS/Linux 使用 `./gradlew` 执行相同任务。首次构建需要下载依赖；仅在已有缓存时加 `--offline`。

- Debug：`com.aegis.apa.preview` / APA Preview，APK：`app/build/outputs/apk/debug/APA-v0.1.1-debug.apk`。
- Release：`com.aegis.apa` / APA。预览版和正式版可同时存在，数据互不合并。
- Debug 构建和 Lint/单元测试不需要发布密钥。APK/AAB 正式打包要求本地完整签名配置，不能把 Key 提交到 Git。
- 本地 `keystore.properties` 使用 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；可用 `-Papa.signingProperties=本地文件` 指定替代配置，PowerShell 中整项加引号。相对 storeFile 按 app 模块目录解析。
- 发布维护者运行 `assembleRelease`，必须另做版本递增、签名/校验值核验和设备测试；详见[测试说明](docs/TESTING.md)。

## 项目状态与协作

本项目由一名中国大陆本科生出于兴趣维护，欢迎 Android、Kotlin、系统工具及模型应用方向的贡献。问题与建议优先提交 [Issues](https://github.com/lzh2034855417-beep/Android-Personal-Agent/issues)。

反馈请附品牌/型号、Android/API、构建类型与版本、权限状态、复现步骤、期望/实际结果和脱敏截图。改代码前阅读 [DATA_MODEL](docs/DATA_MODEL.md)，PR 附测试结果与未测限制。下一阶段优先关闭 [TECH_DEBT](docs/TECH_DEBT.md) 中 P1 项，再扩展 Shizuku、流式回答和持久化。

## 许可证

[MIT](LICENSE)；第三方说明见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。
