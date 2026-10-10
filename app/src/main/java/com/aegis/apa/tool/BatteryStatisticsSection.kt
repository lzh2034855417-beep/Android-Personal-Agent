package com.aegis.apa.tool

/** Selects only an unambiguous supported statistics scope, preserving its provenance. */
object BatteryStatisticsSection {
    fun sinceLastCharge(output: String): String? {
        val lines = output.lines()
        val headerIndex = lines.indices.filter { lines[it].trim() == SINCE_LAST_CHARGE }.singleOrNull()
            ?: return null
        return lines.asSequence().drop(headerIndex + 1)
            .takeWhile { !isStatisticsHeading(it) }
            .joinToString("\n")
    }

    internal fun hasStatisticsHeading(output: String): Boolean = output.lineSequence().any(::isStatisticsHeading)

    /** Reads duration only from an already selected section; missing/ambiguous/invalid stays unknown. */
    internal fun batteryDurationMillis(statistics: String): Long? {
        val durationLine = statistics.lineSequence()
            .filter { it.trimStart().startsWith("Time on battery:") }.take(2).toList().singleOrNull()
            ?: return null
        val durationText = batteryDuration.matchEntire(durationLine)?.groupValues?.get(1)?.trim()
            ?.takeIf { durationTokens.matches(it) } ?: return null
        return DiagnosticDurationParser.parseMillis(durationText)?.takeIf { it > 0 }
    }

    private fun isStatisticsHeading(line: String): Boolean =
        line.trimStart().startsWith("Statistics", ignoreCase = true)

    private const val SINCE_LAST_CHARGE = "Statistics since last charge:"
    private val batteryDuration = Regex("^\\s*Time on battery:\\s*(.*?)\\s*\\([^)]*\\).*$")
    private val durationTokens = Regex("(?:\\d+\\s*(?:ms|h|m|s)\\s*)+", RegexOption.IGNORE_CASE)
}
