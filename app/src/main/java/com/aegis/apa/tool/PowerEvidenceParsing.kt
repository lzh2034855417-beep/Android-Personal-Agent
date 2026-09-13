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
    val jobCount: Long? = null
)

data class EvidenceParseResult(
    val apps: Map<Int, PartialAppEvidence>,
    val parsedFields: Set<EvidenceField>
)

internal object AndroidUidParser {
    fun parse(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        Regex("u(\\d+)a(\\d+)").matchEntire(token)?.let { match ->
            return match.groupValues[1].toInt() * 100_000 + 10_000 + match.groupValues[2].toInt()
        }
        Regex("u(\\d+)s(\\d+)").matchEntire(token)?.let { match ->
            return match.groupValues[1].toInt() * 100_000 + match.groupValues[2].toInt()
        }
        return null
    }
}

internal fun mergePartialEvidence(
    first: PartialAppEvidence?,
    second: PartialAppEvidence
): PartialAppEvidence {
    if (first == null) return second
    return PartialAppEvidence(
        uid = first.uid,
        estimatedPowerMah = second.estimatedPowerMah ?: first.estimatedPowerMah,
        foregroundDurationMillis = second.foregroundDurationMillis ?: first.foregroundDurationMillis,
        backgroundDurationMillis = second.backgroundDurationMillis ?: first.backgroundDurationMillis,
        wakeLockDurationMillis = sumNullable(first.wakeLockDurationMillis, second.wakeLockDurationMillis),
        wakeupCount = sumNullable(first.wakeupCount, second.wakeupCount),
        alarmCount = sumNullable(first.alarmCount, second.alarmCount),
        jobCount = sumNullable(first.jobCount, second.jobCount)
    )
}

private fun sumNullable(first: Long?, second: Long?): Long? =
    if (first == null && second == null) null else (first ?: 0L) + (second ?: 0L)

internal object DiagnosticDurationParser {
    private val tokenPattern = Regex("(\\d+)\\s*(ms|h|m|s)", RegexOption.IGNORE_CASE)

    fun parseMillis(text: String): Long? {
        val tokens = tokenPattern.findAll(text).toList()
        if (tokens.isEmpty()) return null
        return tokens.sumOf { match ->
            val value = match.groupValues[1].toLong()
            when (match.groupValues[2].lowercase()) {
                "h" -> value * 60 * 60_000L
                "m" -> value * 60_000L
                "s" -> value * 1_000L
                else -> value
            }
        }
    }
}
