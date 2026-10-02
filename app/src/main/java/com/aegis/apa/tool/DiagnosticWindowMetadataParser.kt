package com.aegis.apa.tool

import com.aegis.apa.model.*

object DiagnosticWindowMetadataParser {
    fun parse(sections: List<RawDiagnosticSection>, headerFields: Map<String, String>): DiagnosticVerificationMetadata {
        fun field(key: String) = headerFields[key]?.takeIf(::validVerificationPropertyValue)
        val device = VerificationDeviceContext(
            field("ro.product.manufacturer"), field("ro.product.model"),
            field("ro.build.version.sdk")?.takeIf { it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 },
            field("ro.build.version.incremental"), VerificationOwnership.UNKNOWN
        ).takeIf { it.manufacturer != null || it.modelCode != null || it.apiLevel != null || it.buildVersion != null }

        val windows = sections.groupBy { it.source }.mapValues { (source, candidates) ->
            val section = candidates.singleOrNull()?.takeIf { it.status == DiagnosticSourceStatus.AVAILABLE && !it.truncated }
            val unknown = MetricWindow(source, VerificationMetricScope.UNKNOWN, null, null, null, null, VerificationConfirmation.UNKNOWN)
            if (source != "batterystats" || section == null) return@mapValues unknown
            // This existing cumulative section supplies scope/duration, never wall-clock bounds.
            if (section.output.lineSequence().count { it.trim() == SINCE_LAST_CHARGE } != 1) return@mapValues unknown
            val statistics = section.output.lineSequence().dropWhile { it.trim() != SINCE_LAST_CHARGE }.drop(1)
                // Even unrecognized later statistics headings end this scope's provenance.
                .takeWhile { !it.trimStart().startsWith("Statistics", ignoreCase = true) }
            val durationLines = statistics.filter { it.trimStart().startsWith("Time on battery:") }.take(2).toList()
            val durationText = durationLines.singleOrNull()?.let { batteryDuration.matchEntire(it)?.groupValues?.get(1)?.trim() }
            val duration = durationText?.takeIf { durationTokens.matches(it) }
                ?.let(DiagnosticDurationParser::parseMillis)?.takeIf { it > 0 }
            unknown.copy(scope = VerificationMetricScope.SINCE_LAST_CHARGE, batteryDurationMillis = duration)
        }
        // No validated report timestamp or explicit OEM statistics-window format is supported yet.
        return DiagnosticVerificationMetadata(reportedAt = null, device = device, windowsBySource = windows)
    }

    private const val SINCE_LAST_CHARGE = "Statistics since last charge:"
    private val batteryDuration = Regex("^\\s*Time on battery:\\s*(.*?)\\s*\\([^)]*\\).*$")
    private val durationTokens = Regex("(?:\\d+\\s*(?:ms|h|m|s)\\s*)+", RegexOption.IGNORE_CASE)
}

internal fun validVerificationPropertyValue(value: String): Boolean = value.isNotBlank() &&
    value.toByteArray(Charsets.UTF_8).size <= 256 && value.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }

/** Retains only non-unique allowlisted properties; conflicts/invalid values cannot later recover. */
internal class VerificationHeaderAccumulator {
    private val fields = linkedMapOf<String, String>()
    private val invalidKeys = mutableSetOf<String>()

    fun consume(line: String, incomplete: Boolean) {
        val key = keyPrefix.find(line)?.groupValues?.get(1) ?: return
        if (key in invalidKeys) return
        val value = if (incomplete) null else propertyLine.matchEntire(line)?.groupValues?.get(2)
        if (value == null || !validVerificationPropertyValue(value) || fields[key]?.let { it != value } == true) {
            fields.remove(key)
            invalidKeys += key
            return
        }
        // Four keys * 256-byte values plus key bytes fit well below the 4KiB retained limit.
        fields[key] = value
    }

    fun snapshot(): Map<String, String> = fields.toMap()

    companion object {
        private const val KEYS = "ro\\.product\\.manufacturer|ro\\.product\\.model|ro\\.build\\.version\\.sdk|ro\\.build\\.version\\.incremental"
        private val keyPrefix = Regex("^\\[($KEYS)\\]:")
        private val propertyLine = Regex("^\\[($KEYS)\\]: \\[(.*)\\]$")
    }
}
