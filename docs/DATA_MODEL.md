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

RootBatteryInfo、DeviceProfileSnapshot 仍位于旧 tool 包，但时间已改为 Instant，cycleCount 已改为 Long?。Scene 的枚举/样本/解析结果仍按功能放在 SceneReport.kt；这两个区域的类型与文字分离是下一步迁移，不通过 typealias 伪装完成。

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
| Scene/Usage 时间 | Long，Unix epoch milliseconds | `*Millis` 后缀；Scene 数字小于 10^10 按秒，否则按毫秒；带 offset 字符串保留其时间含义，无 offset 按手机当前时区 |
| 内存/存储 | Long，bytes | `*Bytes`；非正总量或可用值越界，本地分析显示未知而非“正常” |
| 电量 | Int?，0..100 | 0 合法，null 未获取；Scene 拒绝越界和小数，不能先截断后验证 |
| 温度 | Double?，摄氏度 | NaN/Infinity 无效；电池、SoC 和机身温度不是同一数据 |
| 电流/功率/电压 | mA / mW / mV | 名称带单位；电流符号保留，不据此单独推断充电状态 |
| 电池容量 | mAh | Root sysfs 的 µAh 转 mAh；无效或不足 1 mAh 的容量视为未获取 |
| 循环数 | Long? | 非负，0 合法；unknown 不存入数字字段 |

约定：未知用 null，空集合表示没有可用条目；权限/错误/partial 状态另列，不能由空集合推断无权限。展示文本如“未获取到”只能在报告或 UI 生成。现有 BatteryInfo.status/health 等中文字符串、RamInfo 非空数字等是历史约定，迁移清单在 TECH_DEBT，不能把旧 DTO 当成已完全标准化的外部协议。

## Scene CSV 输入契约

- UTF-8（允许 BOM），逗号分隔；上限 2 MiB。单行字段支持双引号、逗号与双引号转义；不支持带换行的字段。
- 列名兼容常见中英文：时间、电量、温度、电流、功耗、前台应用。拒绝同一语义重复列及数据行列数不一致。
- 数值单元格是有限数字，可使用科学计数法；电量可带 `%`。单位写在表头，不能把任意带字母文本剥离成数字。
- current/电流 默认 mA，支持 mA、uA/µA/μA、A 表头；power/功耗 默认 mW，支持 mW、W；温度默认摄氏度，不推测华氏度。
- 文件中没有单位的列按上述约定，不声称所有 Scene 导出版本都兼容。未识别列不纳入报告；前台应用不是持续时间或耗电归因。
- 只在时间和电量充分且无回升/重复时间时计算区间平均下降；不推算全天续航、老化百分比或维修结论。未采到的中间充电仍无法排除。

示例：[scene-example.csv](examples/scene-example.csv)。这是合成的格式示例，不是实际设备数据。

## 演进规则

新增采集器先定义数据来源、单位、时间、权限和失败语义；先补未知/越界/拒绝路径测试，再实现采集。未来持久化需版本字段、兼容策略与迁移测试。不要把已有 UI 文案、模型输出或 enum.ordinal 用作持久化键；服务商当前使用目录名称作为 key，稳定 ID 迁移须兼容旧加密槽。
