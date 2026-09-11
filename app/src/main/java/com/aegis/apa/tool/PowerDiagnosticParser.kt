package com.aegis.apa.tool

import com.aegis.apa.model.AppPowerEvidence
import com.aegis.apa.model.DiagnosticSourceResult
import com.aegis.apa.model.DiagnosticSourceStatus
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
    private val packagePattern = Regex("package:(\\S+)\\s+uid:(\\d+)")
    private val uidPowerPattern = Regex("(?m)^\\s*Uid\\s+(\\S+):\\s*([0-9]+(?:\\.[0-9]+)?)")

    fun parse(
        sections: List<RawDiagnosticSection>,
        sampledAt: Instant,
        collectionDurationMillis: Long = 0L
    ): PowerDiagnosticSnapshot {
        val byName = sections.associateBy { it.source }
        val packagesByUid = packagePattern.findAll(byName["packages"]?.output.orEmpty())
            .groupBy(
                keySelector = { it.groupValues[2].toInt() },
                valueTransform = { it.groupValues[1] }
            )
            .mapValues { (_, packages) -> packages.distinct().sorted() }

        val apps = uidPowerPattern.findAll(byName["batterystats"]?.output.orEmpty())
            .mapNotNull { match ->
                val uid = parseUid(match.groupValues[1]) ?: return@mapNotNull null
                val power = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                AppPowerEvidence(
                    uid = uid,
                    packageNames = packagesByUid[uid].orEmpty(),
                    estimatedPowerMah = power
                )
            }
            .toList()

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
                topWakeupSources = wakeupSources
            ),
            findings = emptyList()
        )
    }

    private fun parseUid(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        Regex("u(\\d+)a(\\d+)").matchEntire(token)?.let { match ->
            return match.groupValues[1].toInt() * 100_000 + 10_000 + match.groupValues[2].toInt()
        }
        Regex("u(\\d+)s(\\d+)").matchEntire(token)?.let { match ->
            return match.groupValues[1].toInt() * 100_000 + match.groupValues[2].toInt()
        }
        return null
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
