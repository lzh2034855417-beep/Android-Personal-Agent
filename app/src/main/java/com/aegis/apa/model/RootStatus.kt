package com.aegis.apa.model

data class RootStatus(
    val hasSuBinary: Boolean,
    val isShizukuInstalled: Boolean,
    val isKernelSuManagerInstalled: Boolean,
    val isMagiskManagerInstalled: Boolean
)

