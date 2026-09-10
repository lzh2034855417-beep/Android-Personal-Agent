package com.aegis.apa.model

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val isLowMemory: Boolean
)

