# RAM 报告兼容性与来源

2026-10-10：按字段来源解析，不按手机型号猜测 RAM 厂家或类型。支持一种字段格式不代表该品牌所有系统报告都会提供此字段。

| 来源 | 支持的证据 | 验证范围 |
| --- | --- | --- |
| 高通启动日志 | 完整配对 `Manufacturer ID = …, Device Type = …`，支持十进制和显式十六进制 | 用户 K60 Pro 原 ZIP 验收；其他编号用合成回归样例 |
| 一加 project_info | `FS/sys/project_info/ddr_manufacture_info` 的十进制编号；`FS/sys/project_info/component_info/ddr` 的 `MANU:` | 根据公开内核格式编写合成样例，尚无一加真机报告验收 |
| 三星 sec_hw_param | `FS/sys/class/sec/sec_hw_param/ddr_info` 及虚拟设备别名中的 `"DDRV"` 厂商缩写 | 根据公开内核格式编写合成样例，尚无三星真机报告验收 |

## 公开证据

- [OnePlus 官方 project_info.c](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8350/blob/oneplus/SM8350_R_11.0/techpack/oneplus/misc/project_info.c)：DDR 厂商节点输出十进制，组件节点单列 MANU。VER 的容量／布局字段不能推导 LPDDR 类型。
- [高通 upstream smem_dramc.c](https://code.googlesource.com/linux/torvalds/linux/+/7acf90feab8b009fde7def08ff2c622d0f10e99f/drivers/soc/qcom/smem_dramc.c)：类型编号使用独立枚举，5/6/7/8/9 对应 LPDDR3/LPDDR4/LPDDR4X/LPDDR5/LPDDR5X；不与旧通用 DDR DeviceType 编号混用。
- [三星 sec_hw_param.c](https://github.com/LineageOS/android_kernel_samsung_sm8250/blob/lineage-23.2/drivers/samsung/debug/sec_hw_param.c) 与 [sec_smem.c](https://github.com/LineageOS/android_kernel_samsung_sm8250/blob/lineage-23.2/drivers/samsung/misc/sec_smem.c)：DDRV 为厂商缩写，DSF 为固件版本，不用于判断内存类型。来源是公开的三星内核代码镜像。
- [公开启动日志样例](https://gist.github.com/mouseos/1aa4308f4dece8ddbcabb0f8076cd41e)：配对记录存在未带 0x 前缀的十进制写法。此类公开日志只用于核对格式，不作为本项目真机验收。

## 解析约束

- 模式寄存器厂商编号与扩展 JEDEC 编号分开。未知编号保留原始 ID，不强行命名厂家。
- 只读取白名单 RAM 节点；屏幕等其他组件的 MANU 不参与 RAM 识别。
- 小型节点限 4 KiB，并遵守取消、总读取预算。带 NUL／损坏编码的节点，以及带 NUL 的启动日志行不能拼接出有效编号。
- 缺少厂商字段时显示未知；不根据型号、容量、DSF 版本或营销规格猜测。
- 导入报告仍需通过设备身份匹配与确认后更新本机硬件缓存。
- 尚未找到足够的公开报告证据来新增 OPPO、vivo、华为等专属格式；现有通用字段可用时仍能解析，但不能宣称这些厂商已全面验收。

## 本轮验证

新增回归覆盖十进制编号、高通旧类型编号、一加数字与 MANU 节点、三星 DDRV/DSF 分离、无关组件、越界编号、NUL、节点限额与取消。
用户 K60 Pro 原报告通过生产解析器验收：美光 RAM、类型码 LPDDR5、三星 UFS 4.0，实际耗电区间 962–1006 mAh。原始报告及个人数据不进入 Git。
