package com.aegis.apa.tool

data class HardwareExperienceGrade(
    val label: String,
    val summary: String,
    val reasons: List<String>
)

/**
 * A transparent configuration tier based only on values APA can read locally.
 * It intentionally does not infer component suppliers or panel quality.
 */
object HardwareExperienceEvaluator {
    fun evaluate(
        totalRamBytes: Long,
        totalStorageBytes: Long,
        maxCpuFrequencyKhz: Long?
    ): HardwareExperienceGrade {
        if (maxCpuFrequencyKhz == null) {
            return HardwareExperienceGrade(
                label = "待读取",
                summary = "读取芯片档案后给出等级",
                reasons = listOf("内存 ${formatGigabytes(totalRamBytes)}", "存储 ${formatGigabytes(totalStorageBytes)}")
            )
        }

        val ramScore = when {
            totalRamBytes >= 12L * GIB -> 2
            totalRamBytes >= 8L * GIB -> 1
            else -> 0
        }
        val storageScore = when {
            totalStorageBytes >= 512L * GIB -> 2
            totalStorageBytes >= 256L * GIB -> 1
            else -> 0
        }
        val cpuScore = when {
            maxCpuFrequencyKhz >= 3_200_000L -> 2
            maxCpuFrequencyKhz >= 2_400_000L -> 1
            else -> 0
        }
        val score = ramScore + storageScore + cpuScore
        val reasons = listOf(
            "内存 ${formatGigabytes(totalRamBytes)}",
            "存储 ${formatGigabytes(totalStorageBytes)}",
            "CPU 峰值 ${maxCpuFrequencyKhz / 1_000} MHz"
        )

        return when {
            score >= 5 -> HardwareExperienceGrade("顶级金标", "性能配置充足", reasons)
            score >= 3 -> HardwareExperienceGrade("高级银标", "日常使用均衡", reasons)
            else -> HardwareExperienceGrade("中级铜标", "基础使用够用", reasons)
        }
    }

    private fun formatGigabytes(bytes: Long): String = "${bytes / GIB} GB"

    private const val GIB = 1024L * 1024L * 1024L
}
