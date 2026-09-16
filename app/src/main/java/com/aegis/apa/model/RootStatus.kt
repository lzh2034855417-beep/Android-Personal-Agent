package com.aegis.apa.model

data class RootStatus(
    val hasSuBinary: Boolean,
    val isShizukuInstalled: Boolean,
    val kernelSuManagerPackage: String?,
    val isMagiskManagerInstalled: Boolean
) {
    val isKernelSuManagerInstalled: Boolean
        get() = kernelSuManagerPackage != null
}

