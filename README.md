# Android Personal Agent（APA）

面向 Android 设备的开源个人 Agent 实验项目。

[中文](README.md) | [English](README_EN.md)

![Version](https://img.shields.io/badge/version-v0.1.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![License](https://img.shields.io/badge/license-MIT-yellow)

> 当前版本：`v0.1.0`
>
> APA 仍处于公开内测阶段。AI 输出仅供参考，不应作为维修、设备健康评估或系统修改的唯一依据。

APA 会在用户明确选择数据范围后读取设备状态，并将报告交给本地分析器或用户配置的云端模型进行解释。项目强调用户知情、最小化数据发送和能力分级，不会在后台自动上传设备报告。

## 作者的话

> 我是来自中国大陆的一名本科在读学生。APA 是一个纯粹由兴趣驱动的个人开源项目，希望借此探索 Android 系统能力与个人 Agent 的结合。项目仍很年轻，欢迎开发者、测试者和 Android 爱好者一起参与，让它逐步变得可靠、透明而实用。

## 测试条件

- Level 0：Android 8.0 或更高版本的普通 Android 设备
- Level 1：安装并运行 Shizuku
- Level 2：已通过 KernelSU、Magisk 等方案取得 Root 权限

能力等级代表可访问的数据范围，而不是设备性能或用户等级。Root 和 Shizuku 都不是使用 APA 的必要条件。

> KernelSU 是 Root 管理方案，因此归入 Level 2；Level 1 对应 Shizuku。

## 联系与协作

问题反馈、功能建议和开发交流请优先使用本仓库的 **GitHub Issues**。欢迎 Android、Kotlin、系统工具和大模型应用方向的开发者提交 Issue 或 Pull Request。

请勿在 Issue、截图或日志中公开 API Key、手机号、QQ 号、设备序列号等敏感信息。目前不设置公开的私人 QQ 或 Telegram 联系方式，避免让个人社交账号与项目维护强绑定。

## 当前能力

- AI 可依据用户主动附带的设备与应用报告，对手机当前状态作出基础评价、说明依据并提供非强制性建议
- 读取设备型号、Android 版本、电量、内存和存储信息
- 检测常用 Root、框架和普通应用
- 查看可启动应用及应用基础信息
- 按 Level 0 / Level 1 / Level 2 选择发送给 Agent 的报告范围
- Root 授权后读取部分底层电池数据
- 多轮 Agent 对话、报告附件标记和对话清空
- 每家模型服务独立加密保存 API Key，有效期默认 7 天

## 能力等级

| 等级 | 数据来源 | 当前状态 |
| --- | --- | --- |
| Level 0 | 普通 Android API | 已实现设备、电池、内存、存储和应用基础信息 |
| Level 1 | Shizuku | 已实现安装检测与跳转；服务状态、授权状态及高级接口仍在开发 |
| Level 2 | Root | 已实现 `su`、KernelSU/Magisk 检测及部分底层电池读取 |

Root 和 Shizuku 都不是运行 APA 的必要条件。没有高级权限时，Level 0 仍可独立使用。

## 云端模型

| 服务商 | 默认模型 | 接口 |
| --- | --- | --- |
| DeepSeek | `deepseek-v4-flash` | DeepSeek Chat Completions |
| OpenAI | `gpt-5.6-terra` | OpenAI Chat Completions |
| Anthropic | `claude-sonnet-5` | Anthropic Messages API |
| Xiaomi MiMo | `mimo-v2.5` | MiMo OpenAI-compatible API |
| Kimi | `kimi-k3` | Kimi 国内 Chat Completions |

除 DeepSeek 外的服务商仍需要更多账户和机型参与公开内测。模型名称及服务可用性可能由服务商调整。

## 隐私与安全

- 设备数据默认仅在本机读取。
- 只有用户主动发送消息时，当前选择的报告才会发往所选模型服务商。
- API Key 使用 Android Keystore 的 AES/GCM 加密后保存在本机。
- 不同服务商的 Key 分开保存，默认 7 天后失效。
- APA 禁止系统云备份，避免加密凭据文件进入设备备份。
- 项目源码不包含开发者或测试者的 API Key。
- APA 没有自建中转服务器，云端分析直接请求用户选择的模型服务商。

使用云端模型意味着用户选择的报告内容会受对应服务商的隐私政策与数据处理条款约束。发送前请确认报告中不包含不希望上传的信息。

## 系统要求

- Android 8.0（API 26）或更高版本
- 网络权限仅用于用户主动发起的云端模型请求
- Usage Access、Shizuku 和 Root 均为可选能力

## 本地构建

需要 Android Studio、Android SDK 以及项目兼容的 JDK。

Windows：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

Debug APK 默认输出到：

```text
app/build/outputs/apk/debug/APA-v0.1.0.apk
```

## 配置模型

1. 打开 APA 的“设置”页面。
2. 选择模型服务商。
3. 输入该服务商签发的 API Key。
4. 点击“加密保存 7 天”。
5. 回到 Agent 页面，选择本轮附带的报告并发送问题。

每家服务商使用独立 Key。切换服务商不会把上一家的 Key 当作当前服务商凭据。

## 当前限制

- Level 1 尚未接入真正的 Shizuku Binder 授权与系统接口。
- Scene 一天续航报告目前只有选择入口，尚未实现文件导入和解析。
- 对话记录只保存在当前应用进程中，重启后会清空。
- 云端请求暂未提供流式输出、取消请求和自动重试。
- 应用检测依赖已知包名与系统可见性，不保证覆盖所有修改版或隐藏版应用。
- Release APK 的正式签名需要由项目维护者自行配置。

## 风险提示

Root 操作可能导致系统异常、数据损坏或设备失去保修。APA 当前只读取有限的底层信息，不会自动执行清理、授权、修改系统设置等操作。请勿向来源不明的应用授予 Root 或 Shizuku 权限。

AI 输出可能存在遗漏或错误。执行任何涉及 Root、系统组件、删除数据或电池维修的建议前，请进行独立验证。

APA 的 AI 评价仅基于本次附带的有限报告，属于基础信息分析，不等同于专业检测、维修结论或长期设备健康诊断。

## 路线图

- 接入 Shizuku 服务状态、授权状态和实际能力
- 导入并解析 Scene 一天续航报告
- 支持流式回答、停止生成和重新生成
- 增加对话持久化与导出
- 扩充自动化测试和内测机型覆盖

## 参与测试

提交问题时建议附上：

- 手机品牌和型号
- Android 版本及系统版本
- 是否使用 KernelSU、Magisk、Shizuku 或 LSPosed
- 复现步骤
- 已隐藏个人信息的截图或错误文本

请勿在 Issue、截图或日志中提交 API Key。

## 参与开发

1. Fork 本仓库。
2. 从 `main` 创建功能分支。
3. 保持修改范围清晰，并说明测试设备与验证结果。
4. 提交 Pull Request。

首次参与开源协作、不熟悉 GitHub 流程也没关系，可以先创建 Issue 描述想法。

## License

本项目采用 [MIT License](LICENSE)。
