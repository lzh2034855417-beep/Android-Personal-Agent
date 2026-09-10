# 技术债与审计发现

2026-09-10；P1 为下次公开发布前优先处理，P2 为可靠性/能力债，P3 为维护优化。以下区分代码可证问题与尚未设备复现的风险。没有做外部漏洞扫描，不能由本表推导“无安全问题”。

## 本轮已处理

| ID | 原问题及证据位置 | 处理与保护 |
| --- | --- | --- |
| FIX-01 | DeviceInfoTool 用 model-only 特例覆盖任意厂商；只保留展示字符串 | DeviceIdentity、DeviceNameResolver；保留原值/来源，厂商约束、locale、缺失和库异常测试 |
| FIX-02 | SceneCsvParser 剥离字母、截断百分比后校验、忽略单位/重复列 | 严格有限数字、明确单位转换、结构拒绝；回归用例 |
| FIX-03 | MainActivity 导入直接 readText，无上限 | SceneCsvInput 2 MiB、严格 UTF-8；大小和编码测试 |
| FIX-04 | SceneReportBuilder 只看首尾，充电夹杂被算成持续耗电 | 回升/重复时间时停止速率推断；更名为采样区间摘要 |
| FIX-05（隐私） | 本地 Scene 摘要追加到 assistant 正文，CloudLlmProvider 原来发送所有历史；换服务商也携带旧历史 | MessageRole + cloudProvider，默认本地；同服务商筛选后取 12 条；移除 Scene 请求参数，3 项回归 |
| FIX-06 | LocalDeviceAnalyzer 的 0/0 或非法资源值进入“正常”分支 | 无效资源值显示未知；边界测试 |
| FIX-07 | 基础 DTO 与 Android 读取混排，Snapshot 在 Activity；Root cycleCount 是 String | 提取 model 包，Instant 时间，Long? 循环数，纯 RootBatteryParser 和单位/无效值测试 |
| FIX-08 | 签名检查按命令名包含 release 判断，aggregate assemble 可绕过 | APK/AAB 打包执行边界检查全部字段和文件；无密钥测试/预览构建和负向 assemble 验证 |
| FIX-09 | UI 测试硬编码正式包名、旧输入框/Scene 占位入口 | 运行时组件、语义输入选择器、当前 Scene 入口及键盘回归；仪器运行状态见 TESTING |

| FIX-10（TD-01） | Level 0 混用高级档案推导等级 | 报告构造器不再接收硬件等级，Activity 删除高级频率读取路径，保持基础报告来源一致 |
| FIX-11（TD-04） | 调度硬编码原厂结论 | 展示实际 governor，缺失显示未知，仅说明 APA 未修改系统；缺失/多策略回归 |
| FIX-12（TD-13） | 授权使用事件即随 Level 0 发送 | 独立开关默认关闭，报告构造器默认排除，合成敏感标签关闭/开启双向验证；页面说明历史范围 |

## 未解决的优先项

| ID | 优先级 / 证据 | 影响 | 验收条件 |
| --- | --- | --- | --- |
| TD-02 | P1（剩余兼容验证），已显式排除凭据云备份/迁移 | 部分系统的设备迁移行为未验证；凭据迁移边界不明确 | 打包规则契约测试已补；仍需 Android 26/31+ 及 OEM 实际恢复测试 |
| TD-03 | P1，无仓库 CI 工作流，真机通过但启动需辅助 | 本轮 11/11 真机通过；仍缺独立无人干预运行和 PR 自动检查 | 每个 PR 跑 JVM/Lint/build；独立模拟器执行 UI 测试，保留报告，不使用发布 Key |
| TD-05 | P2（设备验收待完成），会话状态已提升到 ViewModel | 草稿/选择/对话/Scene 已实现跨切页和配置重建保留；进程回收恢复尚未实现 | 运行新增切页/重建/清空/中断仪器测试；进程回收后的恢复另行设计；Key 不进入状态容器或 SavedState |
| TD-06 | P2，MainActivity Root/档案 raw Thread；SystemProfileCommandRunner waitFor 后才读输出 | 生命周期脱离、异常漏捕；管道满可导致超时丢数据 | 可取消用例、并发排空有上限输出、finally 释放进程；拒绝/超时/大量输出/重建测试 |
| TD-07 | P2，RootTool 只检测 KernelSU 原包；AppTool 检测 Next | 能力页与应用页不一致，启动管理器可能失败 | 共用包目录并返回实际安装包；Next-only 场景测试 |
| TD-08 | P2，Manifest 无 launcher intent query；AppTool MATCH_ALL | Android 包可见性过滤可能低报可启动应用，标签提示也非可信身份 | 最小化 queries 配置；Android 11+ 已知安装集合实测；不可宣称全应用列表 |
| TD-09 | P2（剩余兼容验证），展开区已限高并独立滚动 | 已扩展键盘回归覆盖报告展开；长摘要/大字体/小屏/横屏仍需矩阵验证 | 有界预览+滚动，导入长摘要/键盘/字体缩放组合测试 |
| TD-10 | P2，CloudLlmProvider 同时组 prompt/JSON/HTTP/解析错误；response readText 无上限 | 难以模拟协议错误，超长响应内存风险，无法有效取消 | 纯请求构造器+传输接口；两协议成功/空结果/401/429/超时/取消/响应上限测试 |
| TD-11 | P2，AgentErrorMessage 直接显示部分异常正文，StoredApiKey 是 data class | 服务商回显可能包含敏感文本；未来日志容易打印 Key | 限制和脱敏错误，凭据避免自动 toString 暴露；合成 key 回显回归 |
| TD-12 | P2，本地分析入口只消费基础 DeviceContext，报告等级并非都消费 | 无 Key 时选择等级不等于本地规则使用了全部附件 | 明确本地分析能力并根据实际使用字段显示附件；各等级本地路径测试 |
| TD-14 | P2，名称库 model-only、离线库 0.7.1、Root sysfs 单位假设 | 区域型号歧义、OEM 单位差异、非标准节点不可保证 | 带来源样本库、代表性机型矩阵、单位差异显式适配；不靠大小猜单位 |
| TD-15 | P3，QuickReportPanel 多次产生 PNG、不清缓存，catch Exception 包含取消 | 长期占用缓存、取消后错误状态 | 有限缓存回收、重抛取消、分享权限/渲染实测 |
| TD-16 | P3，部分旧 DTO/status/Level/topic 仍是字符串，格式器散落 | 本地化/持久化耦合，二进制容量写成 GB 等文案不统一 | 分批类型迁移与格式器测试；新增字段遵守 DATA_MODEL |
| TD-17 | P3，依赖/targetSdk/资源与 KTX Lint 警告 | 维护成本与兼容性债；不等于已确认安全漏洞 | 逐项升级并跑兼容矩阵；不通过统一 suppress 隐藏警告 |

## 执行原则

本次“完整审计”是覆盖全仓库并记录发现，不表示清零全部技术债。发布前先关闭 P1；每项关闭必须附可执行测试/设备证据。代码编译、JVM 测试、仪器测试运行和真实服务验证分别记录，不混称“全测试通过”。
