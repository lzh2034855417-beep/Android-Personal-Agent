package com.aegis.apa.agent

/**
 * Exact Android API 29+ severity names/codes, never temperature or a visible-notification claim.
 * Official constants: https://developer.android.com/reference/android/os/PowerManager#THERMAL_STATUS_NONE
 * Parser provenance: thermalservice's mStatus also occurs in android.os.Temperature's dump.
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/os/Temperature.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/core/java/com/android/server/power/ThermalManagerService.java
 * Both use the same platform throttling severity constants returned by getCurrentThermalStatus.
 */
enum class GuidedThermalStatus(val code: String, val requiresSystemGuidance: Boolean) {
    NONE("0", false), LIGHT("1", false), MODERATE("2", false), SEVERE("3", true),
    CRITICAL("4", true), EMERGENCY("5", true), SHUTDOWN("6", true);

    companion object {
        // Local conservative guidance policy selects the four named severe states explicitly.
        // Unrecognized vendor/ future codes remain unknown, regardless of their numeric size.
        fun fromCode(code: String): GuidedThermalStatus? = entries.firstOrNull { it.code == code }
    }
}
