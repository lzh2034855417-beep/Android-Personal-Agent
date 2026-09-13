package com.aegis.apa.tool

import com.aegis.apa.model.EvidenceField

object JobSchedulerEvidenceParser {
    private val jobPattern = Regex("(?mi)^\\s*JOB\\s+#(u\\d+[as]\\d+|\\d+)/\\d+:")

    fun parse(output: String): EvidenceParseResult {
        val counts = linkedMapOf<Int, Long>()
        jobPattern.findAll(output).forEach { match ->
            val uid = AndroidUidParser.parse(match.groupValues[1]) ?: return@forEach
            counts[uid] = (counts[uid] ?: 0L) + 1L
        }
        val apps = counts.mapValues { (uid, count) ->
            PartialAppEvidence(uid = uid, jobCount = count)
        }
        return EvidenceParseResult(
            apps = apps,
            parsedFields = if (apps.isEmpty()) emptySet() else setOf(EvidenceField.JOBS)
        )
    }
}
