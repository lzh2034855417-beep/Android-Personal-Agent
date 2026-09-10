package com.aegis.apa.tool

import com.aegis.apa.model.RootStatus

import android.content.Context
import java.io.File

object RootTool {
    private val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/su"
    )

    fun read(context: Context): RootStatus {
        val packageManager = context.packageManager

        fun isInstalled(packageName: String): Boolean = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.isSuccess

        return RootStatus(
            hasSuBinary = suPaths.any { File(it).canExecute() },
            isShizukuInstalled = isInstalled("moe.shizuku.privileged.api"),
            isKernelSuManagerInstalled = isInstalled("me.weishu.kernelsu"),
            isMagiskManagerInstalled = isInstalled("com.topjohnwu.magisk")
        )
    }
}
