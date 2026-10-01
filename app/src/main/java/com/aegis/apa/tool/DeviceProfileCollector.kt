package com.aegis.apa.tool

import com.aegis.apa.localization.AppLanguage
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DeviceProfileAccess {
    STANDARD,
    ROOT
}

enum class HardwareSupplySource {
    ROOT_SYSFS,
    ANDROID_BUGREPORT,
    COMBINED
}

data class HardwareSupplyInfo(
    val ramVendor: String? = null,
    val ramType: String? = null,
    val storageVendor: String? = null,
    val storageModel: String? = null,
    val storageSpec: String? = null,
    val source: HardwareSupplySource
) {
    val hasDetails: Boolean
        get() = listOf(ramVendor, ramType, storageVendor, storageModel, storageSpec)
            .any { !it.isNullOrBlank() }

    fun mergeMissingFrom(fallback: HardwareSupplyInfo?): HardwareSupplyInfo {
        val usesFallback = fallback != null && (
            (ramVendor == null && fallback.ramVendor != null) ||
                (ramType == null && fallback.ramType != null) ||
                (storageVendor == null && fallback.storageVendor != null) ||
                (storageModel == null && fallback.storageModel != null) ||
                (storageSpec == null && fallback.storageSpec != null)
            )
        return copy(
            ramVendor = ramVendor ?: fallback?.ramVendor,
            ramType = ramType ?: fallback?.ramType,
            storageVendor = storageVendor ?: fallback?.storageVendor,
            storageModel = storageModel ?: fallback?.storageModel,
            storageSpec = storageSpec ?: fallback?.storageSpec,
            source = if (usesFallback && fallback?.source != source) HardwareSupplySource.COMBINED else source
        )
    }
}

internal fun normalizeStorageVendor(raw: String?): String? = when (val value = raw?.trim()?.takeIf(String::isNotEmpty)) {
    null -> null
    else -> when (value.replace(" ", "").lowercase(Locale.ROOT)) {
        "skhynix", "hynix" -> "SK hynix"
        "samsung" -> "Samsung"
        "micron" -> "Micron"
        "toshiba", "kioxia" -> "Kioxia"
        "xbstor" -> "飞存闪拓 (XBSTOR)"
        else -> value
    }
}

data class CpuPolicyProfile(
    val name: String,
    val cpus: List<Int>,
    val minFrequencyKhz: Long?,
    val maxFrequencyKhz: Long?,
    val governor: String?
)

data class ThermalSensorProfile(
    val type: String,
    val temperatureCelsius: Double
)

data class DeviceProfileSnapshot(
    val model: String?,
    val manufacturer: String? = null,
    val device: String?,
    val soc: String?,
    val androidVersion: String?,
    val buildVersion: String?,
    val kernelVersion: String?,
    val cpuPresent: String?,
    val cpuOnline: String?,
    val cpuPolicies: List<CpuPolicyProfile>,
    val thermalSensors: List<ThermalSensorProfile>,
    val access: DeviceProfileAccess,
    val hardwareSupplyInfo: HardwareSupplyInfo? = null,
    val error: String? = null,
    val sampledAtInstant: java.time.Instant = java.time.Instant.now()
) {
    val sampledAt: String get() = com.aegis.apa.model.SampleTime.format(sampledAtInstant)

    fun toReportText(language: AppLanguage = AppLanguage.ZH_CN): String =
        if (language == AppLanguage.EN) toEnglishReportText() else toChineseReportText()

    private fun toChineseReportText(): String = buildString {
        appendLine("调度档案：只读")
        appendLine("档案采样时间：$sampledAt（需手动重读）")
        appendLine("APA 未修改系统调度")
        appendLine(
            "读取权限：${if (access == DeviceProfileAccess.ROOT) "Root 只读" else "标准权限"}"
        )
        appendLine("设备名称：${publicDeviceName(manufacturer = manufacturer, modelCode = model)}")
        appendLine("SoC：${soc ?: "设备未提供"}")
        appendLine("Android：${androidVersion ?: "设备未提供"}")
        appendLine("系统版本：${buildVersion ?: "设备未提供"}")
        appendLine("内核：${kernelVersion ?: "设备未提供"}")
        appendLine("CPU present：${cpuPresent ?: "设备未提供"}")
        appendLine("CPU online：${cpuOnline ?: "设备未提供"}")
        hardwareSupplyInfo?.takeIf(HardwareSupplyInfo::hasDetails)?.let { hardware ->
            appendLine("内存规格：${hardware.ramType ?: "设备未提供"}")
            appendLine("存储厂商：${hardware.storageVendor ?: "设备未提供"}")
            appendLine("存储型号：${hardware.storageModel ?: "设备未提供"}")
        }
        if (cpuPolicies.isEmpty()) {
            appendLine("CPU 策略：设备未提供")
        } else {
            appendLine("CPU 策略：")
            cpuPolicies.forEach { policy ->
                appendLine(
                    "- ${policy.name} · CPU ${policy.cpus.joinToString(",")} · " +
                        "${formatFrequency(policy.minFrequencyKhz)}–${formatFrequency(policy.maxFrequencyKhz)} · " +
                        "${policy.governor ?: "governor 未提供"}"
                )
            }
        }
        if (thermalSensors.isEmpty()) {
            appendLine("温度节点：设备未提供")
        } else {
            appendLine("温度节点：")
            thermalSensors.forEach { sensor ->
                appendLine(
                    "- ${sensor.type}：${String.format(Locale.US, "%.1f", sensor.temperatureCelsius)}°C"
                )
            }
        }
        error?.let { appendLine("采集提示：$it") }
    }

    private fun toEnglishReportText(): String = buildString {
        appendLine("Scheduling profile: Read-only")
        appendLine("Profile sample time: $sampledAt (refresh manually)")
        appendLine("APA did not modify system scheduling")
        appendLine("Access: ${if (access == DeviceProfileAccess.ROOT) "Root read-only" else "Standard access"}")
        appendLine("Device: ${publicDeviceName(manufacturer = manufacturer, modelCode = model)}")
        appendLine("SoC: ${soc ?: "Unavailable"}")
        appendLine("Android: ${androidVersion ?: "Unavailable"}")
        appendLine("System build: ${buildVersion ?: "Unavailable"}")
        appendLine("Kernel: ${kernelVersion ?: "Unavailable"}")
        appendLine("CPU present: ${cpuPresent ?: "Unavailable"}")
        appendLine("CPU online: ${cpuOnline ?: "Unavailable"}")
        hardwareSupplyInfo?.takeIf(HardwareSupplyInfo::hasDetails)?.let { hardware ->
            appendLine("RAM specification: ${hardware.ramType ?: "Unavailable"}")
            appendLine("Storage vendor: ${hardware.storageVendor ?: "Unavailable"}")
            appendLine("Storage model: ${hardware.storageModel ?: "Unavailable"}")
        }
        if (cpuPolicies.isEmpty()) {
            appendLine("CPU policies: Unavailable")
        } else {
            appendLine("CPU policies:")
            cpuPolicies.forEach { policy ->
                appendLine(
                    "- ${policy.name} · CPU ${policy.cpus.joinToString(",")} · " +
                        "${formatFrequency(policy.minFrequencyKhz, "Unknown")}–${formatFrequency(policy.maxFrequencyKhz, "Unknown")} · " +
                        "${policy.governor ?: "governor unavailable"}"
                )
            }
        }
        if (thermalSensors.isEmpty()) {
            appendLine("Thermal sensors: Unavailable")
        } else {
            appendLine("Thermal sensors:")
            thermalSensors.forEach { sensor ->
                appendLine("- ${sensor.type}: ${String.format(Locale.US, "%.1f", sensor.temperatureCelsius)}°C")
            }
        }
        error?.let { appendLine("Collection note: ${collectionErrorEnglish(it)}") }
    }

    private fun formatFrequency(valueKhz: Long?, unavailable: String = "未知"): String =
        valueKhz?.let { String.format(Locale.US, "%.0f MHz", it / 1_000.0) } ?: unavailable

    private fun collectionErrorEnglish(value: String): String = when {
        value.contains("超时") -> "Collection timed out."
        value.contains("失败") || value.contains("拒绝") -> "Collection failed or access was denied."
        else -> "Some profile data was unavailable."
    }
}

data class ChipSchedulingDetails(
    val chipset: String,
    val scheduler: String,
    val access: String,
    val cpuTopology: String,
    val policySummaries: List<String>,
    val thermalSummary: String,
    val kernelSummary: String
)

fun DeviceProfileSnapshot.toChipSchedulingDetails(): ChipSchedulingDetails = ChipSchedulingDetails(
    chipset = soc ?: "未读取",
    scheduler = cpuPolicies.mapNotNull { it.governor?.takeIf(String::isNotBlank) }.distinct()
        .joinToString(" / ").ifEmpty { "未获取到" },
    access = if (access == DeviceProfileAccess.ROOT) "Root 只读" else "标准权限",
    cpuTopology = cpuPresent?.let { "CPU $it" } ?: "CPU 未读取",
    policySummaries = cpuPolicies.map { policy ->
        "${policy.name} · CPU ${policy.cpus.joinToString(",")} · " +
            "最高 ${policy.maxFrequencyKhz?.div(1_000) ?: "未知"} MHz"
    },
    thermalSummary = "相关温度节点：${thermalSensors.size} 个",
    kernelSummary = "内核：${kernelVersion ?: "未读取"}"
)

object DeviceProfileParser {
    private val retainedKeys = setOf(
        "MODEL",
        "MANUFACTURER",
        "DEVICE",
        "SOC",
        "ANDROID",
        "BUILD",
        "KERNEL",
        "CPU_PRESENT",
        "CPU_ONLINE"
    )

    fun parse(
        raw: String,
        access: DeviceProfileAccess,
        error: String? = null
    ): DeviceProfileSnapshot {
        val values = raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim()
                if (key !in retainedKeys) return@mapNotNull null
                key to line.substring(separator + 1).trim().ifEmpty { null }
            }
            .toMap()

        val policies = raw.lineSequence()
            .filter { it.startsWith("POLICY=") }
            .mapNotNull(::parsePolicy)
            .toList()

        val thermalSensors = raw.lineSequence()
            .filter { it.startsWith("THERMAL=") }
            .mapNotNull(::parseThermalSensor)
            .toList()
        val sysfsDdrType = raw.lineSequence()
            .firstOrNull { it.startsWith("DDR_TYPE_HEX=") }
            ?.substringAfter('=')
            ?.trim()
            ?.let(::decodeDdrType)
        val storage = raw.lineSequence()
            .firstOrNull { it.startsWith("STORAGE=") }
            ?.substringAfter('=')
            ?.split('|', limit = 2)
        val sysfsHardware = HardwareSupplyInfo(
            ramType = sysfsDdrType,
            storageVendor = normalizeStorageVendor(storage?.getOrNull(0)),
            storageModel = storage?.getOrNull(1)?.trim()?.ifEmpty { null },
            source = HardwareSupplySource.ROOT_SYSFS
        ).takeIf(HardwareSupplyInfo::hasDetails)
        val bugReportHardware = HardwareSupplyInfo(
            ramVendor = DDR_MANUFACTURER_ID.find(raw)?.groupValues?.get(1)?.let(::ddrVendorFromId),
            ramType = DDR_DEVICE_TYPE.find(raw)?.groupValues?.get(1)?.let(::decodeDdrType),
            storageVendor = UFS_INQUIRY_ID.find(raw)?.groupValues?.get(1)?.let(::ufsVendorFromId),
            storageSpec = UFS_SPEC_VERSION.find(raw)?.groupValues?.get(1)?.let { "UFS ${it.trim()}" },
            source = HardwareSupplySource.ANDROID_BUGREPORT
        ).takeIf(HardwareSupplyInfo::hasDetails)
        val hardwareSupplyInfo = when {
            sysfsHardware != null -> sysfsHardware.mergeMissingFrom(bugReportHardware)
            else -> bugReportHardware
        }

        return DeviceProfileSnapshot(
            model = values["MODEL"],
            manufacturer = values["MANUFACTURER"],
            device = values["DEVICE"],
            soc = values["SOC"],
            androidVersion = values["ANDROID"],
            buildVersion = values["BUILD"],
            kernelVersion = values["KERNEL"],
            cpuPresent = values["CPU_PRESENT"],
            cpuOnline = values["CPU_ONLINE"],
            cpuPolicies = policies,
            thermalSensors = thermalSensors,
            access = access,
            hardwareSupplyInfo = hardwareSupplyInfo,
            error = error
        )
    }

    private fun ddrVendorFromId(raw: String): String? {
        val id = normalizedSupplierId(raw) ?: return null
        return when (id.trimStart('0')) {
            "1CE" -> "三星 (Samsung)"
            "1AD" -> "海力士 (SK hynix)"
            "12C" -> "美光 (Micron)"
            else -> "JEDEC ID 0x$id"
        }
    }

    private fun ufsVendorFromId(raw: String): String? {
        val id = normalizedSupplierId(raw) ?: return null
        return when (id.trimStart('0')) {
            "1" -> "Samsung"
            "6" -> "SK hynix"
            "FF" -> "Micron"
            else -> "UFS inquiry ID 0x$id"
        }
    }

    private fun normalizedSupplierId(raw: String): String? = normalizeHexId(raw)
        .takeIf { it.length <= 8 && it.any { digit -> digit != '0' } }

    private fun normalizeHexId(raw: String): String = raw.trim()
        .removePrefix("0x")
        .removePrefix("0X")
        .uppercase(Locale.ROOT)
        .padStart(4, '0')

    private fun decodeDdrType(raw: String): String? = when (normalizeHexId(raw).trimStart('0').ifEmpty { "0" }) {
        "3" -> "LPDDR3"
        "5" -> "LPDDR4"
        "7" -> "LPDDR4X"
        "8" -> "LPDDR5"
        "9" -> "LPDDR5X"
        else -> null
    }

    private fun parsePolicy(line: String): CpuPolicyProfile? {
        val fields = line.removePrefix("POLICY=").split('|', limit = 5)
        if (fields.size < 2 || fields[0].isBlank()) return null
        val cpus = fields[1].split(Regex("\\s+"))
            .mapNotNull(String::toIntOrNull)
        if (cpus.isEmpty()) return null
        return CpuPolicyProfile(
            name = fields[0],
            cpus = cpus,
            minFrequencyKhz = fields.getOrNull(2)?.toLongOrNull(),
            maxFrequencyKhz = fields.getOrNull(3)?.toLongOrNull(),
            governor = fields.getOrNull(4)?.trim()?.ifEmpty { null }
        )
    }

    private fun parseThermalSensor(line: String): ThermalSensorProfile? {
        val fields = line.removePrefix("THERMAL=").split('|', limit = 2)
        val type = fields.getOrNull(0)?.trim()?.ifEmpty { null } ?: return null
        val milliCelsius = fields.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
        return ThermalSensorProfile(type, milliCelsius / 1_000.0)
    }

    private val DDR_DEVICE_TYPE = Regex(
        "DDR\\s+Device\\s+Type\\s*=\\s*(0x[0-9A-Fa-f]+|[0-9A-Fa-f]+)",
        RegexOption.IGNORE_CASE
    )
    private val DDR_MANUFACTURER_ID = Regex(
        "DDR\\s+Manufacturer\\s+ID\\s*=\\s*(0x[0-9A-Fa-f]+|[0-9A-Fa-f]+)",
        RegexOption.IGNORE_CASE
    )
    private val UFS_INQUIRY_ID = Regex(
        "UFS\\s+INQUIRY\\s+ID\\s*[-:=]\\s*(0x[0-9A-Fa-f]+|[0-9A-Fa-f]+)",
        RegexOption.IGNORE_CASE
    )
    private val UFS_SPEC_VERSION = Regex(
        "UFS\\s+Spec\\s+Version\\s*[-:=]\\s*([0-9]+(?:\\.[0-9]+)?)",
        RegexOption.IGNORE_CASE
    )
}

data class ProfileCommandResult(
    val exitCode: Int?,
    val output: String,
    val timedOut: Boolean = false
) {
    val succeeded: Boolean
        get() = !timedOut && exitCode == 0
}

fun interface ProfileCommandRunner {
    fun run(command: List<String>): ProfileCommandResult
}

object SystemProfileCommandRunner : ProfileCommandRunner {
    override fun run(command: List<String>): ProfileCommandResult {
        val process = runCatching {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            return ProfileCommandResult(exitCode = null, output = error.message.orEmpty())
        }

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ProfileCommandResult(exitCode = null, output = "", timedOut = true)
        }

        return ProfileCommandResult(
            exitCode = process.exitValue(),
            output = process.inputStream.bufferedReader().use { it.readText() }
        )
    }
}

object DeviceProfileCollector {
    fun read(
        preferRoot: Boolean,
        runner: ProfileCommandRunner = SystemProfileCommandRunner
    ): DeviceProfileSnapshot {
        if (preferRoot) {
            val rootResult = runner.run(listOf("su", "-c", collectionScript))
            if (rootResult.succeeded) {
                return DeviceProfileParser.parse(
                    raw = rootResult.output,
                    access = DeviceProfileAccess.ROOT,
                    error = emptyOutputWarning(rootResult.output)
                )
            }

            val rootWarning = if (rootResult.timedOut) {
                "Root 读取超时，已回退标准权限"
            } else {
                "Root 读取失败或被拒绝，已回退标准权限"
            }
            return readStandard(runner, rootWarning)
        }

        return readStandard(runner, null)
    }

    private fun readStandard(
        runner: ProfileCommandRunner,
        priorWarning: String?
    ): DeviceProfileSnapshot {
        val result = runner.run(listOf("sh", "-c", collectionScript))
        val error = when {
            result.timedOut -> listOfNotNull(priorWarning, "标准权限读取超时").joinToString("；")
            !result.succeeded -> listOfNotNull(priorWarning, "标准权限采集失败").joinToString("；")
            result.output.isBlank() -> listOfNotNull(priorWarning, "设备未返回调度档案数据").joinToString("；")
            else -> priorWarning
        }
        return DeviceProfileParser.parse(
            raw = result.output.takeIf { result.succeeded }.orEmpty(),
            access = DeviceProfileAccess.STANDARD,
            error = error
        )
    }

    private fun emptyOutputWarning(output: String): String? =
        "设备未返回调度档案数据".takeIf { output.isBlank() }

    private val collectionScript = """
        printf 'MANUFACTURER=%s\n' "${'$'}(getprop ro.product.manufacturer)"
        printf 'MODEL=%s\n' "${'$'}(getprop ro.product.model)"
        printf 'DEVICE=%s\n' "${'$'}(getprop ro.product.device)"
        printf 'SOC=%s\n' "${'$'}(getprop ro.soc.model)"
        printf 'ANDROID=%s\n' "${'$'}(getprop ro.build.version.release)"
        printf 'BUILD=%s\n' "${'$'}(getprop ro.build.version.incremental)"
        printf 'KERNEL=%s\n' "${'$'}(uname -r)"
        printf 'CPU_PRESENT=%s\n' "${'$'}(cat /sys/devices/system/cpu/present 2>/dev/null)"
        printf 'CPU_ONLINE=%s\n' "${'$'}(cat /sys/devices/system/cpu/online 2>/dev/null)"
        ddr_type=/proc/device-tree/memory/ddr_device_type
        if [ -r "${'$'}ddr_type" ]; then
          printf 'DDR_TYPE_HEX=%s\n' "${'$'}(od -An -tx1 "${'$'}ddr_type" 2>/dev/null | tr -d ' \n')"
        fi
        for block in sda sdb sdc sdd sde sdf; do
          vendor_path=/sys/block/${'$'}block/device/vendor
          model_path=/sys/block/${'$'}block/device/model
          if [ -r "${'$'}vendor_path" ]; then
            vendor="${'$'}(cat "${'$'}vendor_path" 2>/dev/null | tr -d '\r\n')"
            model="${'$'}(cat "${'$'}model_path" 2>/dev/null | tr -d '\r\n')"
            if [ -n "${'$'}vendor" ]; then
              printf 'STORAGE=%s|%s\n' "${'$'}vendor" "${'$'}model"
              break
            fi
          fi
        done
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
          [ -d "${'$'}p" ] || continue
          name="${'$'}{p##*/}"
          cpus="${'$'}(cat "${'$'}p/related_cpus" 2>/dev/null)"
          min="${'$'}(cat "${'$'}p/cpuinfo_min_freq" 2>/dev/null)"
          max="${'$'}(cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null)"
          governor="${'$'}(cat "${'$'}p/scaling_governor" 2>/dev/null)"
          printf 'POLICY=%s|%s|%s|%s|%s\n' "${'$'}name" "${'$'}cpus" "${'$'}min" "${'$'}max" "${'$'}governor"
        done
        for z in /sys/class/thermal/thermal_zone*; do
          [ -d "${'$'}z" ] || continue
          type="${'$'}(cat "${'$'}z/type" 2>/dev/null)"
          case "${'$'}type" in
            *cpu*|*CPU*|*soc*|*SOC*|*skin*|*battery*|*gpu*|*GPU*)
              temp="${'$'}(cat "${'$'}z/temp" 2>/dev/null)"
              printf 'THERMAL=%s|%s\n' "${'$'}type" "${'$'}temp"
              ;;
          esac
        done
    """.trimIndent()
}
