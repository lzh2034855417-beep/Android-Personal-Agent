package com.aegis.apa.tool

import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticSourceResult
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.EvidenceCoverage
import com.aegis.apa.model.EvidenceField
import com.aegis.apa.model.EvidenceFieldStatus
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.SystemPowerEvidence
import java.time.Instant

data class RawDiagnosticSection(
    val source: String,
    val status: DiagnosticSourceStatus,
    val output: String,
    val truncated: Boolean = false,
    val detail: String? = null
)

object PowerDiagnosticParser {

    fun parse(
        sections: List<RawDiagnosticSection>,
        sampledAt: Instant,
        collectionDurationMillis: Long = 0L
    ): PowerDiagnosticSnapshot {
        val byName = sections.associateBy { it.source }
        val batteryEvidence = BatteryStatsEvidenceParser.parse(byName["batterystats"]?.output.orEmpty())
        val batteryDrainWindow = BatteryStatsEvidenceParser.parseDrainWindow(
            byName["batterystats"]?.output.orEmpty()
        )
        val alarmEvidence = AlarmEvidenceParser.parse(byName["alarm"]?.output.orEmpty())
        val jobEvidence = JobSchedulerEvidenceParser.parse(byName["jobscheduler"]?.output.orEmpty())
        val evidenceByUid = linkedMapOf<Int, PartialAppEvidence>()
        listOf(batteryEvidence, alarmEvidence, jobEvidence).forEach { parsed ->
            parsed.apps.values.forEach { partial ->
                evidenceByUid[partial.uid] = mergePartialEvidence(evidenceByUid[partial.uid], partial)
            }
        }
        val packagesByUid = PackageUidResolver.resolve(
            output = byName["packages"]?.output.orEmpty(),
            targetUids = evidenceByUid.keys
        )
        val apps = evidenceByUid.values
            .sortedBy { it.uid }
            .map { evidence ->
                val packages = packagesByUid[evidence.uid].orEmpty()
                AppPowerEvidence(
                    uid = evidence.uid,
                    packageNames = packages,
                    estimatedPowerMah = evidence.estimatedPowerMah,
                    foregroundPowerMah = evidence.foregroundPowerMah,
                    backgroundPowerMah = evidence.backgroundPowerMah,
                    wakeLockDurationMillis = evidence.wakeLockDurationMillis,
                    wakeupCount = evidence.wakeupCount,
                    alarmCount = evidence.alarmCount,
                    jobCount = evidence.jobCount,
                    foregroundDurationMillis = evidence.foregroundDurationMillis,
                    backgroundDurationMillis = evidence.backgroundDurationMillis,
                    sharedUid = packages.size > 1
                )
            }

        val powerOutput = byName["power"]?.output.orEmpty()
        val wakefulness = findValue(powerOutput, "mWakefulness")
        val explicitInteractive = findBoolean(powerOutput, "mInteractive")
        val interactive = explicitInteractive ?: wakefulness?.equals("Awake", ignoreCase = true)
        val deviceIdleMode = findBoolean(powerOutput, "mDeviceIdleMode")
            ?: findBoolean(byName["deviceidle"]?.output.orEmpty(), "mDeviceIdleMode")
        val thermalStatus = Regex("(?:Current Thermal Status:|mStatus=)\\s*(\\d+)")
            .find(byName["thermalservice"]?.output.orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
        val wakeupSources = byName["wakeup_sources"]?.output.orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("name ", ignoreCase = true) }
            .map { it.substringBefore(' ') }
            .distinct()
            .take(5)
            .toList()

        val sources = sections.associate { section ->
            val status = if (section.truncated && section.status == DiagnosticSourceStatus.AVAILABLE) {
                DiagnosticSourceStatus.TRUNCATED
            } else {
                section.status
            }
            section.source to DiagnosticSourceResult(section.source, status, section.detail)
        }

        val coverage = EvidenceCoverage(
            fields = mapOf(
                EvidenceField.UID_PACKAGES to fieldStatus(byName["packages"], packagesByUid.isNotEmpty()),
                EvidenceField.POWER_MAH to fieldStatus(byName["batterystats"], EvidenceField.POWER_MAH in batteryEvidence.parsedFields),
                EvidenceField.FOREGROUND_TIME to fieldStatus(byName["batterystats"], EvidenceField.FOREGROUND_TIME in batteryEvidence.parsedFields),
                EvidenceField.WAKELOCK_TIME to fieldStatus(byName["batterystats"], EvidenceField.WAKELOCK_TIME in batteryEvidence.parsedFields),
                EvidenceField.WAKEUP_ALARMS to fieldStatus(byName["alarm"], EvidenceField.WAKEUP_ALARMS in alarmEvidence.parsedFields),
                EvidenceField.JOBS to fieldStatus(byName["jobscheduler"], EvidenceField.JOBS in jobEvidence.parsedFields)
            )
        )

        return PowerDiagnosticSnapshot(
            sampledAtInstant = sampledAt,
            collectionDurationMillis = collectionDurationMillis,
            sources = sources,
            apps = apps,
            system = SystemPowerEvidence(
                interactive = interactive,
                wakefulness = wakefulness,
                deviceIdleMode = deviceIdleMode,
                thermalStatus = thermalStatus,
                topWakeupSources = wakeupSources,
                batteryDrainWindow = batteryDrainWindow
            ),
            findings = emptyList(),
            evidenceCoverage = coverage
        )
    }

    private fun fieldStatus(section: RawDiagnosticSection?, parsed: Boolean): EvidenceFieldStatus = when {
        section == null -> EvidenceFieldStatus.SOURCE_UNAVAILABLE
        section.status != DiagnosticSourceStatus.AVAILABLE && section.status != DiagnosticSourceStatus.TRUNCATED ->
            EvidenceFieldStatus.SOURCE_UNAVAILABLE
        section.truncated || section.status == DiagnosticSourceStatus.TRUNCATED -> EvidenceFieldStatus.TRUNCATED
        parsed -> EvidenceFieldStatus.PARSED
        else -> EvidenceFieldStatus.NOT_PARSED
    }

    private fun findValue(output: String, key: String): String? =
        Regex("(?m)^\\s*${Regex.escape(key)}=([^\\s]+)")
            .find(output)?.groupValues?.get(1)

    private fun findBoolean(output: String, key: String): Boolean? =
        findValue(output, key)?.let {
            when {
                it.equals("true", ignoreCase = true) -> true
                it.equals("false", ignoreCase = true) -> false
                else -> null
            }
        }
}
