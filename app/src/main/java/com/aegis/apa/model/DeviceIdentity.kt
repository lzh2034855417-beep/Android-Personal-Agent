package com.aegis.apa.model

/** Verbatim system identifiers, not serial numbers or unique device IDs. */
data class DeviceIdentifiers(
    val manufacturer: String?,
    val modelCode: String?,
    val brand: String? = null,
    val device: String? = null
)

enum class DeviceNameSource { CURATED, OFFLINE_DATABASE, SYSTEM_MODEL, UNAVAILABLE }

data class DeviceIdentity(
    val identifiers: DeviceIdentifiers,
    val displayName: String,
    val nameSource: DeviceNameSource
)

data class DeviceInfo(
    val identity: DeviceIdentity,
    val androidRelease: String?,
    val apiLevel: Int?
) {
    val model: String get() = identity.displayName
    val androidVersion: String get() = "Android ${androidRelease?.takeIf { it.isNotBlank() } ?: "未知"}" +
        (apiLevel?.let { "（API $it）" } ?: "")
}
