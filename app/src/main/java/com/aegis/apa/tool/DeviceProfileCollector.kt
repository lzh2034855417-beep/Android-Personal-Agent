package com.aegis.apa.tool

import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DeviceProfileAccess {
    STANDARD,
    ROOT
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
    val error: String? = null,
    val sampledAtInstant: java.time.Instant = java.time.Instant.now()
) {
    val sampledAt: String get() = com.aegis.apa.model.SampleTime.format(sampledAtInstant)

    fun toReportText(): String = buildString {
        appendLine("调度档案：只读")
        appendLine("档案采样时间：$sampledAt（需手动重读）")
        appendLine("当前模式：V8 原厂调度（未修改系统）")
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

    private fun formatFrequency(valueKhz: Long?): String =
        valueKhz?.let { String.format(Locale.US, "%.0f MHz", it / 1_000.0) } ?: "未知"
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
    scheduler = "V8 原厂调度",
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
            error = error
        )
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
