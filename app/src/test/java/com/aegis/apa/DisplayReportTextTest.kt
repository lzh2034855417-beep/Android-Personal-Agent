package com.aegis.apa

import com.aegis.apa.tool.DisplayInfo
import com.aegis.apa.tool.DisplayReportText
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayReportTextTest {
    @Test
    fun formatsScreenResolutionAndMaximumRefreshRate() {
        val text = DisplayReportText.format(DisplayInfo(1220, 2712, 480, 120f, 120f))

        assertTrue(text.contains("分辨率：1220 × 2712"))
        assertTrue(text.contains("当前刷新率：120 Hz"))
        assertTrue(text.contains("最高支持刷新率：120 Hz"))
    }
}
