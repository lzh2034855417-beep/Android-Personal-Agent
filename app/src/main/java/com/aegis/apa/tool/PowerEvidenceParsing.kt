package com.aegis.apa.tool

import com.aegis.apa.model.EvidenceField

data class PartialAppEvidence(
    val uid: Int,
    val estimatedPowerMah: Double? = null,
    val foregroundDurationMillis: Long? = null,
    val backgroundDurationMillis: Long? = null,
    val wakeLockDurationMillis: Long? = null,
    val wakeupCount: Long? = null,
    val alarmCount: Long? = null,
    val jobCount: Long? = null,
    internal val invalidAdditiveMetrics: Set<AdditiveEvidenceMetric> = emptySet()
)

enum class AdditiveEvidenceMetric {
    WAKE_LOCK_DURATION,
    WAKEUP_COUNT,
    ALARM_COUNT,
    JOB_COUNT
}

data class EvidenceParseResult(
    val apps: Map<Int, PartialAppEvidence>,
    val parsedFields: Set<EvidenceField>
)

internal object AndroidUidParser {
    fun parse(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        Regex("u(\\d+)a(\\d+)").matchEntire(token)?.let { match ->
            return composeUid(
                userToken = match.groupValues[1],
                appToken = match.groupValues[2],
                appIdOffset = 10_000L,
                maxAppToken = 9_999L
            )
        }
        Regex("u(\\d+)s(\\d+)").matchEntire(token)?.let { match ->
            return composeUid(
                userToken = match.groupValues[1],
                appToken = match.groupValues[2],
                appIdOffset = 0L,
                maxAppToken = 9_999L
            )
        }
        return null
    }

    private fun composeUid(
        userToken: String,
        appToken: String,
        appIdOffset: Long,
        maxAppToken: Long
    ): Int? {
        val userId = userToken.toLongOrNull() ?: return null
        val appTokenValue = appToken.toLongOrNull()?.takeIf { it <= maxAppToken } ?: return null
        if (userId > Int.MAX_VALUE / 100_000L) return null
        val uid = userId * 100_000L + appIdOffset + appTokenValue
        return uid.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
}

internal fun mergePartialEvidence(
    first: PartialAppEvidence?,
    second: PartialAppEvidence
): PartialAppEvidence {
    if (first == null) return second
    val invalidMetrics = (first.invalidAdditiveMetrics + second.invalidAdditiveMetrics).toMutableSet()
    val wakeLock = mergeAdditiveMetric(
        first.wakeLockDurationMillis,
        second.wakeLockDurationMillis,
        AdditiveEvidenceMetric.WAKE_LOCK_DURATION in invalidMetrics
    ).also { if (it.overflowed) invalidMetrics += AdditiveEvidenceMetric.WAKE_LOCK_DURATION }
    val wakeups = mergeAdditiveMetric(
        first.wakeupCount,
        second.wakeupCount,
        AdditiveEvidenceMetric.WAKEUP_COUNT in invalidMetrics
    ).also { if (it.overflowed) invalidMetrics += AdditiveEvidenceMetric.WAKEUP_COUNT }
    val alarms = mergeAdditiveMetric(
        first.alarmCount,
        second.alarmCount,
        AdditiveEvidenceMetric.ALARM_COUNT in invalidMetrics
    ).also { if (it.overflowed) invalidMetrics += AdditiveEvidenceMetric.ALARM_COUNT }
    val jobs = mergeAdditiveMetric(
        first.jobCount,
        second.jobCount,
        AdditiveEvidenceMetric.JOB_COUNT in invalidMetrics
    ).also { if (it.overflowed) invalidMetrics += AdditiveEvidenceMetric.JOB_COUNT }
    return PartialAppEvidence(
        uid = first.uid,
        estimatedPowerMah = second.estimatedPowerMah ?: first.estimatedPowerMah,
        foregroundDurationMillis = second.foregroundDurationMillis ?: first.foregroundDurationMillis,
        backgroundDurationMillis = second.backgroundDurationMillis ?: first.backgroundDurationMillis,
        wakeLockDurationMillis = wakeLock.value,
        wakeupCount = wakeups.value,
        alarmCount = alarms.value,
        jobCount = jobs.value,
        invalidAdditiveMetrics = invalidMetrics
    )
}

private data class AdditiveMergeResult(val value: Long?, val overflowed: Boolean)

private fun mergeAdditiveMetric(first: Long?, second: Long?, alreadyInvalid: Boolean): AdditiveMergeResult {
    if (alreadyInvalid) return AdditiveMergeResult(null, overflowed = false)
    if (first == null) return AdditiveMergeResult(second, overflowed = false)
    if (second == null) return AdditiveMergeResult(first, overflowed = false)
    return try {
        AdditiveMergeResult(Math.addExact(first, second), overflowed = false)
    } catch (_: ArithmeticException) {
        AdditiveMergeResult(null, overflowed = true)
    }
}

internal object DiagnosticDurationParser {
    private val tokenPattern = Regex("(\\d+)\\s*(ms|h|m|s)", RegexOption.IGNORE_CASE)

    fun parseMillis(text: String): Long? {
        val tokens = tokenPattern.findAll(text).toList()
        if (tokens.isEmpty()) return null
        var total = 0L
        tokens.forEach { match ->
            val value = match.groupValues[1].toLongOrNull() ?: return null
            val multiplier = when (match.groupValues[2].lowercase()) {
                "h" -> 60 * 60_000L
                "m" -> 60_000L
                "s" -> 1_000L
                else -> 1L
            }
            if (value > Long.MAX_VALUE / multiplier) return null
            val millis = value * multiplier
            if (total > Long.MAX_VALUE - millis) return null
            total += millis
        }
        return total
    }
}
