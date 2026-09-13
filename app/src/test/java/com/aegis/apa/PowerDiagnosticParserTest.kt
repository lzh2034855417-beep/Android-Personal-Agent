package com.aegis.apa

import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.model.EvidenceField
import com.aegis.apa.model.EvidenceFieldStatus
import com.aegis.apa.tool.PowerDiagnosticParser
import com.aegis.apa.tool.RawDiagnosticSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PowerDiagnosticParserTest {
    @Test
    fun mapsEstimatedUidPowerToInstalledPackages() {
        val snapshot = PowerDiagnosticParser.parse(
            sections = listOf(
                RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE, "package:com.example.reader uid:10123"),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                    Estimated power use (mAh):
                      Capacity: 7448, Computed drain: 2975, actual drain: 2975
                      UID u0a123: 245.5 fg: 10.0 bg: 235.5
                    """.trimIndent()
                )
            ),
            sampledAt = Instant.parse("2026-09-11T00:00:00Z")
        )

        assertEquals(listOf("com.example.reader"), snapshot.apps.single().packageNames)
        assertEquals(245.5, snapshot.apps.single().estimatedPowerMah!!, 0.001)
    }

    @Test
    fun ignoresNetworkUidRowsOutsideEstimatedPowerData() {
        val snapshot = PowerDiagnosticParser.parse(
            sections = listOf(
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                    Estimated power use (mAh):
                      UID u0a266: 2719 fg: 184 bg: 2517
                      UID u0a434: 645 fg: 432 bg: 63.2
                    Network:
                      Uid u0a184: 9999 (1333 packets over 12h)
                    """.trimIndent()
                )
            ),
            sampledAt = Instant.EPOCH
        )

        assertEquals(listOf(10266, 10434), snapshot.apps.mapNotNull { it.uid })
        assertEquals(listOf(2719.0, 645.0), snapshot.apps.mapNotNull { it.estimatedPowerMah })
    }

    @Test
    fun sharedUidKeepsEveryPackageAndTruncationStatus() {
        val snapshot = PowerDiagnosticParser.parse(
            sections = listOf(
                RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE, "package:com.one uid:10123\npackage:com.two uid:10123"),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.TRUNCATED,
                    "Estimated power use (mAh):\n  UID u0a123: 50.0",
                    truncated = true
                )
            ),
            sampledAt = Instant.EPOCH
        )

        assertEquals(listOf("com.one", "com.two"), snapshot.apps.single().packageNames)
        assertEquals(DiagnosticSourceStatus.TRUNCATED, snapshot.sources.getValue("batterystats").status)
    }

    @Test
    fun parsesPowerIdleAndThermalWithoutInventingMalformedValues() {
        val snapshot = PowerDiagnosticParser.parse(
            sections = listOf(
                RawDiagnosticSection("power", DiagnosticSourceStatus.AVAILABLE, "mWakefulness=Awake\nmIsPowered=false\nmDeviceIdleMode=true"),
                RawDiagnosticSection("thermalservice", DiagnosticSourceStatus.AVAILABLE, "Current Thermal Status: hot")
            ),
            sampledAt = Instant.EPOCH
        )

        assertEquals("Awake", snapshot.system.wakefulness)
        assertEquals(true, snapshot.system.interactive)
        assertEquals(true, snapshot.system.deviceIdleMode)
        assertNull(snapshot.system.thermalStatus)
        assertTrue(snapshot.findings.isEmpty())
    }

    @Test
    fun mergesIndependentEvidenceByUidAndReportsFieldCoverage() {
        val snapshot = PowerDiagnosticParser.parse(
            sections = listOf(
                RawDiagnosticSection("packages", DiagnosticSourceStatus.AVAILABLE, "package:com.example.chat uid:10123"),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    """
                        Estimated power use (mAh):
                          UID u0a123: 240.0
                        Uid u0a123:
                          Wake lock sync: 12m 0s partial (2 times) realtime
                    """.trimIndent()
                ),
                RawDiagnosticSection("alarm", DiagnosticSourceStatus.AVAILABLE, "UID u0a123: 180 wakeups, 200 alarms"),
                RawDiagnosticSection("jobscheduler", DiagnosticSourceStatus.AVAILABLE, "JOB #u0a123/1: com.example.chat/.SyncJob")
            ),
            sampledAt = Instant.EPOCH
        )

        val app = snapshot.apps.single()
        assertEquals(listOf("com.example.chat"), app.packageNames)
        assertEquals(240.0, app.estimatedPowerMah!!, 0.001)
        assertEquals(12 * 60_000L, app.wakeLockDurationMillis)
        assertEquals(180L, app.wakeupCount)
        assertEquals(200L, app.alarmCount)
        assertEquals(1L, app.jobCount)
        assertEquals(EvidenceFieldStatus.PARSED, snapshot.evidenceCoverage.fields.getValue(EvidenceField.JOBS))
    }

    @Test
    fun availableButUnrecognizedSourceIsNotReportedAsZero() {
        val snapshot = PowerDiagnosticParser.parse(
            listOf(RawDiagnosticSection("alarm", DiagnosticSourceStatus.AVAILABLE, "vendor format unknown")),
            Instant.EPOCH
        )

        assertTrue(snapshot.apps.isEmpty())
        assertEquals(EvidenceFieldStatus.NOT_PARSED, snapshot.evidenceCoverage.fields.getValue(EvidenceField.WAKEUP_ALARMS))
    }
}
