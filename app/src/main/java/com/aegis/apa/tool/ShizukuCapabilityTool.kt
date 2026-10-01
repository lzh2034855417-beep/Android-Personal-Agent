package com.aegis.apa.tool

import android.content.pm.PackageManager
import com.aegis.apa.model.ShizukuAccessState
import rikka.shizuku.Shizuku

object ShizukuCapabilityTool {
    const val PERMISSION_REQUEST_CODE = 1001

    fun read(isInstalled: Boolean): ShizukuAccessState {
        if (!isInstalled) return ShizukuAccessState.NOT_INSTALLED
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permissionGranted = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuAccessState.resolve(
            isInstalled = true,
            binderAlive = binderAlive,
            permissionGranted = permissionGranted
        )
    }

    fun requestPermission(): Boolean {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return false
        if (
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        ) return true
        if (runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(true)) {
            return false
        }
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }
}
