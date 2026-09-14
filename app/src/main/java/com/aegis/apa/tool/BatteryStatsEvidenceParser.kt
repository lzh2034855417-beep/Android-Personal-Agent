package com.aegis.apa.tool

import com.aegis.apa.model.EvidenceField

object BatteryStatsEvidenceParser {
    private const val ESTIMATED_POWER_HEADER = "Estimated power use (mAh):"
    private val uidPowerPattern = Regex("(?mi)^\\s*UID\\s+(\\S+):\\s*([0-9]+(?:\\.[0-9]+)?)(.*)$")
    private val cpuProcessStateDurationPattern = Regex(
        "(?i)\\bcpu:(fg|bg|fgs)=\\s*<?[0-9]+(?:\\.[0-9]+)?\\s*\\(([^)]*)\\)"
    )
    private val uidBlockPattern = Regex("^\\s*Uid\\s+(\\S+):\\s*$")
    private val wakeLockPattern = Regex("^\\s*Wake lock .*:\\s*(.*?)\\s+partial(?:\\s.*)?$")

    fun parse(output: String): EvidenceParseResult {
        val apps = linkedMapOf<Int, PartialAppEvidence>()
        val parsedFields = mutableSetOf<EvidenceField>()

        uidPowerPattern.findAll(extractEstimatedPowerSection(output)).forEach { match ->
            val uid = AndroidUidParser.parse(match.groupValues[1]) ?: return@forEach
            val power = match.groupValues[2].toDoubleOrNull() ?: return@forEach
            val stateDurations = cpuProcessStateDurationPattern.findAll(match.groupValues[3])
                .mapNotNull { stateMatch ->
                    val duration = DiagnosticDurationParser.parseMillis(stateMatch.groupValues[2])
                        ?: return@mapNotNull null
                    stateMatch.groupValues[1].lowercase() to duration
                }
                .groupBy({ it.first }, { it.second })
            val foregroundDuration = stateDurations["fg"]?.sumWithoutOverflow()
            val backgroundDurations = stateDurations["bg"].orEmpty() + stateDurations["fgs"].orEmpty()
            val backgroundDuration = backgroundDurations.takeIf { it.isNotEmpty() }?.sumWithoutOverflow()
            apps[uid] = mergePartialEvidence(
                apps[uid],
                PartialAppEvidence(
                    uid = uid,
                    estimatedPowerMah = power,
                    foregroundDurationMillis = foregroundDuration,
                    backgroundDurationMillis = backgroundDuration
                )
            )
            parsedFields += EvidenceField.POWER_MAH
            if (foregroundDuration != null || backgroundDuration != null) {
                parsedFields += EvidenceField.FOREGROUND_TIME
            }
        }

        var currentUid: Int? = null
        output.lineSequence().forEach { line ->
            uidBlockPattern.matchEntire(line)?.let { match ->
                currentUid = AndroidUidParser.parse(match.groupValues[1])
                return@forEach
            }
            val uid = currentUid ?: return@forEach
            val durationText = wakeLockPattern.matchEntire(line)?.groupValues?.get(1) ?: return@forEach
            val durationMillis = DiagnosticDurationParser.parseMillis(durationText) ?: return@forEach
            apps[uid] = mergePartialEvidence(
                apps[uid],
                PartialAppEvidence(uid = uid, wakeLockDurationMillis = durationMillis)
            )
            parsedFields += EvidenceField.WAKELOCK_TIME
        }

        return EvidenceParseResult(apps, parsedFields)
    }

    private fun extractEstimatedPowerSection(output: String): String {
        val lines = output.lines()
        val headerIndex = lines.indexOfFirst { it.trim() == ESTIMATED_POWER_HEADER }
        if (headerIndex < 0) return ""

        val headerIndent = lines[headerIndex].leadingWhitespaceCount()
        val sectionLines = mutableListOf<String>()
        for (line in lines.drop(headerIndex + 1)) {
            val trimmed = line.trim()
            if (
                trimmed.isNotEmpty() &&
                line.leadingWhitespaceCount() <= headerIndent &&
                !trimmed.startsWith("UID ", ignoreCase = true)
            ) {
                break
            }
            sectionLines += line
        }
        return sectionLines.joinToString("\n")
    }

    private fun String.leadingWhitespaceCount(): Int {
        val firstContentIndex = indexOfFirst { !it.isWhitespace() }
        return if (firstContentIndex < 0) length else firstContentIndex
    }

    private fun Iterable<Long>.sumWithoutOverflow(): Long? {
        var total = 0L
        for (value in this) {
            if (total > Long.MAX_VALUE - value) return null
            total += value
        }
        return total
    }
}
