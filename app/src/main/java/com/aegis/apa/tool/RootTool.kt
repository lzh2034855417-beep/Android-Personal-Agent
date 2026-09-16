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
            kernelSuManagerPackage = resolveKernelSuManagerPackage(::isInstalled),
            isMagiskManagerInstalled = isInstalled("com.topjohnwu.magisk")
        )
    }
}

internal val KERNEL_SU_MANAGER_PACKAGES = listOf(
    "me.weishu.kernelsu",
    "me.weishu.kernelsu.next"
)

internal fun resolveKernelSuManagerPackage(isInstalled: (String) -> Boolean): String? =
    KERNEL_SU_MANAGER_PACKAGES.firstOrNull(isInstalled)
