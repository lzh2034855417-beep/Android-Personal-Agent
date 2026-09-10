package com.aegis.apa.tool

import com.aegis.apa.model.DisplayInfo

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object DisplayReportText {
    fun format(info: DisplayInfo): String = buildString {
        appendLine("分辨率：${info.widthPixels?.let { width -> info.heightPixels?.let { "$width × $it" } } ?: "设备未提供"}")
        appendLine("屏幕密度：${info.densityDpi?.let { "$it dpi" } ?: "设备未提供"}")
        appendLine("当前刷新率：${info.currentRefreshRate?.let { "${it.toInt()} Hz" } ?: "设备未提供"}")
        appendLine("最高支持刷新率：${info.maxRefreshRate?.let { "${it.toInt()} Hz" } ?: "设备未提供"}")
    }
}

object DisplayInfoTool {
    fun read(context: Context): DisplayInfo {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val currentMode = display?.mode
        val densityDpi = context.resources.displayMetrics.densityDpi
        return DisplayInfo(
            widthPixels = currentMode?.physicalWidth?.takeIf { it > 0 },
            heightPixels = currentMode?.physicalHeight?.takeIf { it > 0 },
            densityDpi = densityDpi.takeIf { it > 0 },
            currentRefreshRate = display?.refreshRate?.takeIf { it > 0f },
            maxRefreshRate = display?.supportedModes?.maxOfOrNull { it.refreshRate }?.takeIf { it > 0f }
        )
    }
}
