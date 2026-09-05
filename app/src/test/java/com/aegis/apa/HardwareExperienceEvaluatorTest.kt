package com.aegis.apa

import com.aegis.apa.tool.HardwareExperienceEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareExperienceEvaluatorTest {
    @Test
    fun awardsGoldForFlagshipConfiguration() {
        val result = HardwareExperienceEvaluator.evaluate(
            totalRamBytes = 16L * GIB,
            totalStorageBytes = 512L * GIB,
            maxCpuFrequencyKhz = 4_600_000L
        )

        assertEquals("顶级金标", result.label)
        assertEquals("性能配置充足", result.summary)
    }

    @Test
    fun awardsSilverForBalancedConfiguration() {
        val result = HardwareExperienceEvaluator.evaluate(
            totalRamBytes = 8L * GIB,
            totalStorageBytes = 256L * GIB,
            maxCpuFrequencyKhz = 2_500_000L
        )

        assertEquals("高级银标", result.label)
        assertEquals("日常使用均衡", result.summary)
    }

    @Test
    fun awardsCopperForEntryConfiguration() {
        val result = HardwareExperienceEvaluator.evaluate(
            totalRamBytes = 4L * GIB,
            totalStorageBytes = 64L * GIB,
            maxCpuFrequencyKhz = 1_800_000L
        )

        assertEquals("中级铜标", result.label)
        assertEquals("基础使用够用", result.summary)
    }

    @Test
    fun keepsGradePendingUntilCpuProfileIsRead() {
        val result = HardwareExperienceEvaluator.evaluate(
            totalRamBytes = 16L * GIB,
            totalStorageBytes = 512L * GIB,
            maxCpuFrequencyKhz = null
        )

        assertEquals("待读取", result.label)
        assertEquals("读取芯片档案后给出等级", result.summary)
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
