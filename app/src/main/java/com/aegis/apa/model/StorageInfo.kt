package com.aegis.apa.model

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long
        get() = totalBytes - availableBytes
}

