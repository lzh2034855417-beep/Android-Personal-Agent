# Android Personal Agent（APA）

看懂手机的耗电、发热与硬件信息。免费开源，无需注册，基础功能不需要 Root 或模型 API Key。

[![最新版](https://img.shields.io/github/v/release/lzh2034855417-beep/Android-Personal-Agent)](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](docs/USAGE.md)
[![MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**[下载最新版 APK](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)** · [使用指南](docs/USAGE.md) · [隐私说明](docs/PRIVACY.md) · [English](README_EN.md)

## 可以用来做什么

| 你想了解的事 | APA 提供什么 |
| --- | --- |
| 手机现在是什么状态？ | 查看电量、温度、内存、存储和设备信息 |
| 哪些应用耗电多？ | 导入系统报告，查看耗电总量、后台线索和统计窗口 |
| 这段时间掉电多快？ | 用系统报告里的有效统计，或做一次拔电后的粗略续航观察 |
| RAM／存储是谁家的？ | 从可读取的硬件字段识别厂商；没有可靠字段就显示未知 |
| 看完报告还是不明白？ | 使用本地解释；也可自行配置模型服务，让 AI 解释所选摘要 |

耗电排行高不等于异常，单次温度或电量读数也不能证明故障。APA 会说明数据不足的地方，不会自动清理、冻结应用或修改系统。

## 下载与安装

1. 打开 **[Releases](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/latest)**，在 **Assets** 下载 `APA-v版本号-release.apk`。
2. 在 Android 手机上打开 APK，按系统提示允许这次安装。`Source code` 压缩包是源码，不是安装包。
3. 打开 APA，先到“设备”页查看信息。已有正式版优先覆盖安装；若提示签名不一致，先反馈，不要急着卸载，以免丢失本地记录。

支持 **Android 8.0 及以上**。当前正式版为 **[0.3.1](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/tag/v0.3.1)**；可读数据取决于手机系统、权限和报告内容。

## 第一次使用

**只看设备信息：** 打开 APA → 设备，无需额外配置。

**想查应用耗电：** Agent → 导入系统报告 → 选择手机生成的 Bug Report ZIP／TXT → 查看分析并提问。找不到报告入口时，点“怎么生成系统报告”，或看[详细步骤](docs/USAGE.md#导入系统报告)。

**想用在线 AI：** 设置 → 选择模型服务 → 填入自己的 API Key 并保存 → 回到 Agent，检查附加内容后发送。模型服务可能单独收费；不配置也能使用本地功能。

## 常见问题

**没有 Root 能用吗？** 能。设备查看、系统报告导入和粗略续航观察不需要 Root。Root 只读诊断是可选入口；Shizuku 目前只有安装检测和跳转。

**为什么某个厂商或数值显示未知？** 系统可能没有提供，或报告格式尚未识别。APA 不按手机型号猜厂商，也不保证所有机型都读到同样多的信息。

**系统报告会上传吗？** 原始系统报告和 Root 原始输出不会发给模型。在线提问时，所选摘要和允许发送的对话内容会直接发给你配置的服务商；发送前请检查附件。详见[隐私说明](docs/PRIVACY.md)。

## 反馈与参与

遇到问题请[提交反馈](https://github.com/lzh2034855417-beep/Android-Personal-Agent/issues/new/choose)，附手机型号、Android 版本、APA 版本、复现步骤和脱敏截图。**不要公开完整系统报告、API Key 或设备唯一标识。**

这是个人维护的开源项目，欢迎真实使用反馈和代码贡献。[后续方向](ROADMAP.md) · [贡献指南](CONTRIBUTING.md) · [开发与构建](docs/DEVELOPMENT.md) · [全部文档](docs/README.md)

`main` 以 0.3.1 正式版源码为基线；复现特定安装版请使用对应发布标签，开发分支可能不同。[MIT 许可证](LICENSE) · [第三方说明](THIRD_PARTY_NOTICES.md)
