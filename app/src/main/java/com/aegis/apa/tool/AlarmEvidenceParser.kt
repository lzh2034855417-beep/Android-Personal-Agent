package com.aegis.apa.tool

import com.aegis.apa.model.EvidenceField

object AlarmEvidenceParser {
    private val summaryPattern = Regex(
        "(?mi)^\\s*(?:UID\\s+)?(u\\d+[as]\\d+|\\d+)\\s*:\\s*(\\d+)\\s+wakeups\\s*,\\s*(\\d+)\\s+alarms"
    )
    private val packageStatsPattern = Regex(
        "(?i)^\\s*(?:\\*ACTIVE\\*\\s+)?(u\\d+[as]\\d+|\\d+):\\S+\\s+.*?,\\s*(\\d+)\\s+wakeups:\\s*$"
    )
    private val currentAlarmCountPattern = Regex(
        "(?i)^\\s*(?:\\*ACTIVE\\*\\s+)?\\S+\\s+\\d+\\s+wakes\\s+(\\d+)\\s+alarms,\\s+last\\s+.*:\\s*$"
    )
    private val legacyAlarmCountPattern = Regex("(?i)^\\s*(\\d+)\\s+alarms:.*$")

    fun parse(output: String): EvidenceParseResult {
        val apps = linkedMapOf<Int, PartialAppEvidence>()
        summaryPattern.findAll(output).forEach { match ->
            val uid = AndroidUidParser.parse(match.groupValues[1]) ?: return@forEach
            val wakeups = match.groupValues[2].toLongOrNull() ?: return@forEach
            val alarms = match.groupValues[3].toLongOrNull() ?: return@forEach
            apps[uid] = mergePartialEvidence(
                apps[uid],
                PartialAppEvidence(uid = uid, wakeupCount = wakeups, alarmCount = alarms)
            )
        }
        var currentUid: Int? = null
        var packageIndent = -1
        output.lineSequence().forEach { line ->
            val packageMatch = packageStatsPattern.matchEntire(line)
            if (packageMatch != null) {
                currentUid = null
                packageIndent = -1
                val match = packageMatch
                val uid = AndroidUidParser.parse(match.groupValues[1]) ?: return@forEach
                val wakeups = match.groupValues[2].toLongOrNull() ?: return@forEach
                currentUid = uid
                packageIndent = line.leadingWhitespaceCount()
                apps[uid] = mergePartialEvidence(
                    apps[uid],
                    PartialAppEvidence(uid = uid, wakeupCount = wakeups)
                )
                return@forEach
            }

            val uid = currentUid ?: return@forEach
            val indent = line.leadingWhitespaceCount()
            val alarms = (
                currentAlarmCountPattern.matchEntire(line)?.groupValues?.get(1)
                    ?: legacyAlarmCountPattern.matchEntire(line)?.groupValues?.get(1)
                )?.toLongOrNull()
            if (alarms != null && indent > packageIndent) {
                apps[uid] = mergePartialEvidence(
                    apps[uid],
                    PartialAppEvidence(uid = uid, alarmCount = alarms)
                )
            } else if (line.isNotBlank() && indent <= packageIndent) {
                currentUid = null
                packageIndent = -1
            }
        }
        return EvidenceParseResult(
            apps = apps,
            parsedFields = if (apps.isEmpty()) emptySet() else setOf(EvidenceField.WAKEUP_ALARMS)
        )
    }

    private fun String.leadingWhitespaceCount(): Int {
        val firstContentIndex = indexOfFirst { !it.isWhitespace() }
        return if (firstContentIndex < 0) length else firstContentIndex
    }
}
