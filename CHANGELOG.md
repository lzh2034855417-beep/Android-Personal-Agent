# Changelog

所有重要变更都会记录在此文件中。

## [Unreleased]

## [0.1.1] - 2026-09-06

### Reliability

- 本地与云端分析前重新读取基础快照，设备读取移到后台；返回应用自动刷新，串行处理并发刷新请求
- 电池缺失数据保持未知；无使用记录不再显示为零分钟
- 以系统前后台事件估算今天的应用使用时长，裁剪跨零点会话并合并同应用的重叠 Activity 区间；标注统计范围及记录限制
- 每次在线请求重新检查密钥保存期限，并固定该请求的服务商和凭据
- Root 电池、调度档案分别标注采样时间，Scene 占位入口禁用并标注开发中
- 设置页显示版本和构建类型，APK 文件名区分 Debug/Release
- 更新 AndroidX Test JUnit/Espresso，修复 Android 17 真机测试中的 InputManager 反射错误

### Level 0+

- 新增屏幕、电池即时状态、系统安全补丁与 ABI 等普通 Android API 报告字段
- 新增可选的“使用情况访问权限”：只汇总当天应用前台使用时长，未授权不影响基础报告

### Added

- 新增实验性的设备调度档案采集器，只读获取设备、SoC、内核、CPU 拓扑、频率策略和相关温度节点
- 优先尝试 Root 只读采集；授权失败、拒绝或超时时自动回退到标准权限
- Level 2 报告可由用户主动附带调度档案，供 Agent 解释当前设备状态

### Changed

- 设备页重组为实时状态、设备与系统、内存与存储、进阶电池四个分组
- 芯片与调度档案默认保持精简，可通过“详细⌄”展开 CPU 策略、温度节点和内核信息

### Security

- 调度档案采集不写入频率、温控、线程亲和性或其他系统节点
- 采集结果采用字段白名单，不读取或保留设备序列号
- 当前调度模式固定标记为 `V8 原厂调度`，尚不执行任何调度修改

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
