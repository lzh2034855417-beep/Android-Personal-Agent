package com.aegis.apa.tool

import com.aegis.apa.model.RamInfo

import android.app.ActivityManager
import android.content.Context

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
