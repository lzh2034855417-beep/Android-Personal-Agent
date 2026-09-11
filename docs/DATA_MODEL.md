# 数据模型与设备识别规范

## 已落地的核心模型

`model/` 是纯 Kotlin 数据层；Android 采集器在 `tool/`，模型不可引用 Activity、Context、Build、Compose 或网络。

| 模型 | 语义 |
| --- | --- |
| DeviceIdentifiers | 原始 manufacturer、modelCode、brand、device；允许 null，保留原值。无序列号、IMEI、Android ID |
| DeviceIdentity | identifiers + displayName + DeviceNameSource；展示名不能覆盖系统原值 |
| DeviceInfo | identity + androidRelease + apiLevel；旧 UI 通过只读派生属性 model/androidVersion 取文字，不双写 |
| DeviceSnapshot | 本轮基础遥测和应用集合，sampledAtInstant 表示基础采集完成时间，并不保证所有读数同一瞬间 |
| BatteryInfo / DisplayInfo / RamInfo / StorageInfo | 单位明确的即时读数；缺失值规则见下 |
| UsageApp / UsageSummary | 毫秒时长、区间起止、访问状态和不完整标记；应用时长可重叠，不等于亮屏时间 |
| AgentConversationMessage | MessageRole 枚举、content、附件标签、来源显示文字、cloudProvider；null provider 为仅本地 |
| PowerDiagnosticSnapshot | 采样时间、来源状态、按 UID 聚合的应用证据、系统证据和确定性诊断结论；不包含原始命令输出 |

RootBatteryInfo、DeviceProfileSnapshot 仍位于 tool 包。系统耗电证据模型位于 model 包，采集、解析、规则和报告文字分层，未知值不伪装成零。

## 设备名称解析

顺序：清理后的 manufacturer + modelCode 精确项目映射 → 离线名称库 → 系统型号原值 → 未识别机型。

- 仅匹配键使用 trim 与 Locale.ROOT 大小写转换；identifiers 保留原值。
- 空字符串、unknown、null、n/a 不当成商品名。
- 项目映射必须同时匹配厂商；已被项目映射占用的代码，在厂商缺失或冲突时不能再绕过厂商约束查询 model-only 库。
- 离线库返回空值、原型号或抛异常，回退系统型号。名称库是 model-only 的有限数据集，结果不保证全球地区版本无歧义。
- 不根据 codename、SoC、CPU 或型号前缀猜商品名，不反推内存、电池或屏幕供应商。
- 当前 Xiaomi 映射是此前项目中已有的设备映射，本轮未新增未经验证的型号；不把单台手机经验扩展成通用规则。

来源枚举：CURATED（项目映射）、OFFLINE_DATABASE（离线库）、SYSTEM_MODEL（系统型号）、UNAVAILABLE（缺失）。设备页面展示原始系统型号和来源，便于用户报告误识别。

### 添加机型

提交 manufacturer/model 原值（不含唯一设备标识）、所依据的厂商资料或脱敏设备实测记录、目标商品名、正例、错误厂商和未知型号反例。优先更新有依据的离线库；少量已确认缺失项可加精确映射。不得默认同前缀的所有区域型号相同。

## 数值、时间与缺失值

| 数据 | 内部单位/类型 | 约定 |
| --- | --- | --- |
| 基础/Root/档案采样时间 | Instant | 显示才调用 SampleTime；测试传固定 Instant/ZoneId |
| Usage 时间 | Long，Unix epoch milliseconds | `*Millis` 后缀；区间起止明确，应用时长可重叠 |
| 内存/存储 | Long，bytes | `*Bytes`；非正总量或可用值越界，本地分析显示未知而非“正常” |
| 电量 | Int?，0..100 | 0 合法，null 未获取；系统估算耗电另用 `estimatedPowerMah: Double?` |
| 温度 | Double?，摄氏度 | NaN/Infinity 无效；电池、SoC 和机身温度不是同一数据 |
| 电流/功率/电压 | mA / mW / mV | 名称带单位；电流符号保留，不据此单独推断充电状态 |
| 电池容量 | mAh | Root sysfs 的 µAh 转 mAh；无效或不足 1 mAh 的容量视为未获取 |
| 循环数 | Long? | 非负，0 合法；unknown 不存入数字字段 |

约定：未知用 null，空集合表示没有可用条目；权限/错误/partial 状态另列，不能由空集合推断无权限。展示文本如“未获取到”只能在报告或 UI 生成。现有 BatteryInfo.status/health 等中文字符串、RamInfo 非空数字等是历史约定，迁移清单在 TECH_DEBT，不能把旧 DTO 当成已完全标准化的外部协议。

## 系统耗电诊断契约

- 来源状态明确区分可用、不支持、权限不足、超时、截断和解析失败；缺失值为 null，不从失败输出中猜数值。
- RootCommandRunner 只接受八个代码定义的 AllowedRootCommand，不接受模型、用户或网络传入的 shell 文本。
- 每个来源独立超时并限制字节数。原始输出只存在于单次采集/解析过程，不进入 ViewModel、聊天历史或报告模型。
- 包名按 UID 映射；共享 UID 保留全部包名。系统或关键应用不得成为冻结候选。
- PowerFinding 分开保存证据、解释、置信度、建议级别、Scene 建议和限制说明。
- 中文报告上限 16 KiB；只有用户明确选择后才可传给模型服务。

第一版只给出诊断和 Scene 手动操作建议，不执行冻结、强停、限频、白名单修改或系统节点写入。

## 演进规则

新增采集器先定义数据来源、单位、时间、权限和失败语义；先补未知/越界/拒绝路径测试，再实现采集。未来持久化需版本字段、兼容策略与迁移测试。不要把已有 UI 文案、模型输出或 enum.ordinal 用作持久化键；服务商当前使用目录名称作为 key，稳定 ID 迁移须兼容旧加密槽。
