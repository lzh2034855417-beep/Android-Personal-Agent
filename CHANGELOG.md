# Changelog

所有重要变更都会记录在此文件中。

## [0.1.0] - 2026-07-30

### Added

- 设备、电池、内存和存储状态读取
- 应用列表与已知应用检测
- Level 0、Level 1、Level 2 能力页面
- KernelSU、Magisk、Shizuku、MT 管理器、LSPosed 等应用检测
- Root 高级电池信息读取
- Agent 对话页面与可折叠报告选择器
- 多轮对话和最近 12 条消息上下文
- DeepSeek、OpenAI GPT、Anthropic Claude、Xiaomi MiMo、Kimi 接口
- 分服务商 Android Keystore 加密 API Key 存储

### Changed

- 应用页面和设置页面汉化
- Agent 页面调整为紧凑对话布局
- API Key 默认保存期限调整为 7 天
- 云端 Agent 只根据用户明确附带的报告回答

### Security

- API Key 不写入源码
- 使用 AES/GCM 与 Android Keystore 加密凭据
- 禁用 Android 系统备份

### Known limitations

- Shizuku 实际授权与 Binder 能力尚未接入
- Scene 一天续航报告尚未支持导入
- 对话记录尚未持久化
- Claude、GPT、MiMo、Kimi 需要扩大真实账户内测
