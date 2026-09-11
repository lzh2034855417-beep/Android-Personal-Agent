package com.aegis.apa

import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.AllowedRootCommand
import com.aegis.apa.tool.DiagnosticCommandRunner
import com.aegis.apa.tool.RootCommandResult
import com.aegis.apa.tool.SystemPowerDiagnosticsCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SystemPowerDiagnosticsCollectorTest {
    @Test
    fun collectorKeepsSuccessfulSectionsWhenOneTimesOut() {
        val runner = DiagnosticCommandRunner { command ->
            when (command) {
                AllowedRootCommand.POWER -> RootCommandResult(command, DiagnosticSourceStatus.AVAILABLE, "mWakefulness=Awake")
                AllowedRootCommand.ALARM -> RootCommandResult(command, DiagnosticSourceStatus.TIMED_OUT, "", detail = "采集超时")
                else -> RootCommandResult(command, DiagnosticSourceStatus.UNSUPPORTED, "", detail = "设备不支持")
            }
        }
        val collector = SystemPowerDiagnosticsCollector(
            runner = runner,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )

        val snapshot = collector.collect()

        assertEquals(DiagnosticSourceStatus.AVAILABLE, snapshot.sources.getValue("power").status)
        assertEquals(DiagnosticSourceStatus.TIMED_OUT, snapshot.sources.getValue("alarm").status)
        assertEquals(true, snapshot.system.interactive)
    }

    @Test
    fun collectorRunsEveryAllowlistedSourceAndAttachesFindings() {
        val visited = mutableListOf<AllowedRootCommand>()
        val runner = DiagnosticCommandRunner { command ->
            visited += command
            val output = when (command) {
                AllowedRootCommand.PACKAGES -> "package:com.example.chat uid:10123"
                AllowedRootCommand.BATTERYSTATS -> "Uid u0a123: 240.0"
                else -> ""
            }
            RootCommandResult(command, DiagnosticSourceStatus.AVAILABLE, output)
        }

        val snapshot = SystemPowerDiagnosticsCollector(runner = runner).collect()

        assertEquals(AllowedRootCommand.entries.toList(), visited)
        assertTrue(snapshot.findings.isNotEmpty())
    }
}
