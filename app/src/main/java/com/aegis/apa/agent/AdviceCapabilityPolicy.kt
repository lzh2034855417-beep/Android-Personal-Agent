package com.aegis.apa.agent

object AdviceCapabilityPolicy {
    fun hasSuccessfulRootEvidence(readAttempted: Boolean, error: String?): Boolean =
        readAttempted && error == null

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
        requestedLevel: String,
        shizukuAuthorized: Boolean,
        rootAuthorized: Boolean
    ): String = requestedLevel.takeIf {
        allowsLevel(it, shizukuAuthorized, rootAuthorized)
    } ?: "Level 0"
}
