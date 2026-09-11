package com.aegis.apa

import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.AllowedRootCommand
import com.aegis.apa.tool.RootCommandRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCommandRunnerTest {
    @Test
    fun diagnosticCommandsAreClosedAndReadOnly() {
        assertEquals(
            setOf("BATTERYSTATS", "POWER", "ALARM", "JOBS", "DEVICE_IDLE", "THERMAL", "WAKEUP", "PACKAGES"),
            AllowedRootCommand.entries.map { it.name }.toSet()
        )
        val forbidden = Regex("reset|force-stop|uninstall|disable|settings\\s+put|>\\s*/sys", RegexOption.IGNORE_CASE)
        assertTrue(AllowedRootCommand.entries.none { forbidden.containsMatchIn(it.shell) })
    }

    @Test
    fun classificationDoesNotExposeDeniedOutput() {
        val result = RootCommandRunner.classifyForTest(
            command = AllowedRootCommand.ALARM,
            exitCode = 1,
            output = "su: permission denied: private details",
            truncated = false
        )

        assertEquals(DiagnosticSourceStatus.PERMISSION_DENIED, result.status)
        assertEquals("", result.output)
        assertTrue(result.detail.orEmpty().contains("Root"))
    }

    @Test
    fun successfulOversizedOutputIsMarkedTruncated() {
        val result = RootCommandRunner.classifyForTest(
            command = AllowedRootCommand.POWER,
            exitCode = 0,
            output = "bounded",
            truncated = true
        )

        assertEquals(DiagnosticSourceStatus.TRUNCATED, result.status)
        assertTrue(result.truncated)
    }

    @Test
    fun missingWakeupSourcesIsReportedAsUnsupported() {
        val result = RootCommandRunner.classifyForTest(
            command = AllowedRootCommand.WAKEUP,
            exitCode = 1,
            output = "",
            truncated = false
        )

        assertEquals(DiagnosticSourceStatus.UNSUPPORTED, result.status)
        assertEquals("", result.output)
        assertTrue(result.detail.orEmpty().contains("不支持"))
    }
}
