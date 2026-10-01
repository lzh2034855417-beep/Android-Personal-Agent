package com.aegis.apa.agent

object AdviceCapabilityPolicy {
    fun allowsLevel(
        level: String,
        shizukuAuthorized: Boolean,
        rootAuthorized: Boolean
    ): Boolean = when (level) {
        "Level 0" -> true
        "Level 1" -> shizukuAuthorized
        "Level 2" -> rootAuthorized
        else -> false
    }

    fun effectiveLevel(
        requestedLevel: String?,
        shizukuAuthorized: Boolean,
        rootAuthorized: Boolean
    ): String? = requestedLevel?.let { requested ->
        requested.takeIf { allowsLevel(it, shizukuAuthorized, rootAuthorized) } ?: "Level 0"
    }

    fun needsFreshSnapshot(
        selectedLevel: String?,
        includeAppReport: Boolean,
        includeUsageReport: Boolean,
        includePowerDiagnosticReport: Boolean
    ): Boolean = selectedLevel != null || includeAppReport || includeUsageReport

    fun cloudAdviceScope(
        selectedLevel: String?,
        hasDeviceEvidence: Boolean
    ): CloudAdviceScope = when {
        selectedLevel?.startsWith("Level 2") == true -> CloudAdviceScope.LEVEL_2
        selectedLevel?.startsWith("Level 1") == true -> CloudAdviceScope.LEVEL_1
        selectedLevel != null || hasDeviceEvidence -> CloudAdviceScope.LEVEL_0
        else -> CloudAdviceScope.GENERAL
    }
}
