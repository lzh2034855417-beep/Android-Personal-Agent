package com.aegis.apa.tool

import android.os.Environment
import android.os.StatFs

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long
        get() = totalBytes - availableBytes
}

object StorageTool {
    fun read(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)

        return StorageInfo(
            totalBytes = stat.totalBytes,
            availableBytes = stat.availableBytes
        )
    }
}
