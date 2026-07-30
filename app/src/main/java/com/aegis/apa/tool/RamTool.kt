package com.aegis.apa.tool

import android.app.ActivityManager
import android.content.Context

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val isLowMemory: Boolean
)

object RamTool {
    fun read(context: Context): RamInfo {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return RamInfo(
            totalBytes = memoryInfo.totalMem,
            availableBytes = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory
        )
    }
}
