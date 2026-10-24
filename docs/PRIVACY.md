# 数据与隐私

[返回首页](../README.md) · [使用指南](USAGE.md) · [安全问题报告](../SECURITY.md)

以下说明面向已发布的 0.3.1。采集发生在本机和发送给模型是两件事；“本地优先”不代表开启在线 AI 后没有数据发送。

## 什么会在本机读取

打开应用或恢复页面时，APA 在本机刷新基础设备与可见应用信息。授予 Usage Access 后还可读取使用事件。

在已有 Root 的设备上，启动或返回应用时还可能检查 Root 授权状态，这可能触发管理器授权提示；设备页会自动刷新硬件信息，已授权时会尝试用 Root 读取。系统耗电诊断和高级电池等手动入口则由用户主动发起。以上均为只读，不等于自动修改系统；无需 Root 也能使用基础功能。

系统报告在手机上提取和解析，原始 Bug Report 与 Root 原始输出不会发给模型。

## 什么时候会发送

点击在线发送后，请求直接发往你配置的模型服务商，没有 APA 中转服务器。可能包含基础快照、所选等级报告、可选应用报告、已附加的耗电摘要和同服务商允许发送的最近对话内容。

- 导入报告或完成诊断后，耗电摘要可能自动选中；发送前可取消，原始 ZIP 不会随之上传。
- 使用习惯排行有独立开关，默认关闭；开系统权限不等于选择发送排行。
- 同一服务商最多带最近 12 条允许发送的消息，可能含之前回答中的数据；不同服务商之间不转发历史。
- 续航观察的确定性回答在本机生成，不会因保存了 Key 就自动发送精确观察时间与电量轨迹。
- “解释本次对比”先预览发送范围，确认后才请求服务；备注默认关闭，不附带普通聊天历史。

在线请求受服务商的隐私政策和计费规则约束。停止或取消不能撤回对方已经收到的数据。

## 什么会保留在手机上

| 内容 | 保留方式与范围 |
| --- | --- |
| 模型 API Key | 按服务商用 Android Keystore 加密，默认本机有效期 7 天；本机过期不等于服务商撤销 Key |
| 聊天消息 | 加密保存最近最多 100 条，自最后保存起保留 7 天；清空对话删除聊天归档 |
| 本地验证记录 | 独立加密保留 30 天，最多 20 条；清空聊天不会同时删除验证记录 |
| 进行中的续航观察 | 保存一个起点和已检测充电标记，不建立电量轨迹数据库 |
| 草稿、附件选择和诊断对象 | 会话内保留；重启后可能需要重新导入或选择 |

到期清理在应用再次打开时执行；保存为异步操作，强制结束时尚未保存的变化可能丢失。报告附件原文、原始系统报告和 Key 不写进聊天归档。

应用配置关闭系统备份，并排除相关敏感存储的云备份与设备迁移；厂商实际恢复行为仍需设备验证，不能承诺所有系统都不会留下副本。

## 反馈前检查

截图和日志中遮住 Key、账号、序列号等唯一标识。完整 Bug Report 和 Root 输出不要发到公开 Issue。安全漏洞按 [SECURITY](../SECURITY.md) 报告。

## English summary

APA reads device data locally; Usage Access is optional. On rooted devices, startup or resume may check Root authorization and trigger a manager prompt. The Device page refreshes hardware information automatically and may use Root when authorized. System power diagnostics and advanced battery checks use separate user-triggered entries. These operations are read-only, and basic features do not require Root. Raw Bug Reports and raw Root output are not sent to model providers.

Online requests go directly to your provider, without an APA relay. They may include selected summaries, device readings, optional reports and permitted recent conversation content. Review attachments before sending; imported diagnostic summaries may be selected automatically. Cancellation cannot retract data already received by a provider.

Provider keys and chat messages use Android Keystore-backed encryption. Keys default to 7-day local validity; chat stores up to 100 messages for 7 days since the last save. Separate verification records keep up to 20 records for 30 days and are not deleted by clearing chat. Expiry cleanup occurs when the app is opened again. Raw report attachments are not stored in chat history. Configured backup exclusions are not proof of every OEM's restore behavior.

Do not post keys, full system reports, account data or unique device identifiers publicly. These details describe release 0.3.1; consult the matching release for future changes.
