package com.aegis.apa.tool

import com.aegis.apa.model.StorageInfo

import android.os.Environment
import android.os.StatFs

object StorageTool {
    fun read(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)

        return StorageInfo(
            totalBytes = stat.totalBytes,
            availableBytes = stat.availableBytes
        )
    }
}
