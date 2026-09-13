package com.aegis.apa.tool

import com.aegis.apa.model.EvidenceField

object AlarmEvidenceParser {
    private val summaryPattern = Regex(
        "(?mi)^\\s*(?:UID\\s+)?(u\\d+[as]\\d+|\\d+)\\s*:\\s*(\\d+)\\s+wakeups\\s*,\\s*(\\d+)\\s+alarms"
    )

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
        return EvidenceParseResult(
            apps = apps,
            parsedFields = if (apps.isEmpty()) emptySet() else setOf(EvidenceField.WAKEUP_ALARMS)
        )
    }
}
