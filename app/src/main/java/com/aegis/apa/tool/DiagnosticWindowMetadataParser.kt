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
            val statistics = BatteryStatisticsSection.sinceLastCharge(section.output)
                ?: return@mapValues unknown
            val duration = BatteryStatisticsSection.batteryDurationMillis(statistics)
            unknown.copy(scope = VerificationMetricScope.SINCE_LAST_CHARGE, batteryDurationMillis = duration)
        }
        // No validated report timestamp or explicit OEM statistics-window format is supported yet.
        return DiagnosticVerificationMetadata(reportedAt = null, device = device, windowsBySource = windows)
    }

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
