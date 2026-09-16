package com.aegis.apa

import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.AllowedRootCommand
import com.aegis.apa.tool.RootCommandRunner
import com.aegis.apa.tool.RootProcessWaitOutcome
import com.aegis.apa.tool.waitForRootProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

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

    @Test
    fun cancellationDestroysTheActiveProcess() {
        val process = WaitingProcess()

        val outcome = waitForRootProcess(
            process = process,
            timeoutMillis = 8_000,
            cancellationRequested = { true }
        )

        assertEquals(RootProcessWaitOutcome.CANCELLED, outcome)
        assertTrue(process.destroyed)
    }

    @Test
    fun interruptedWaitDestroysTheActiveProcessAndBecomesCancellation() {
        val process = WaitingProcess(interruptOnWait = true)

        val failure = runCatching {
            waitForRootProcess(
                process = process,
                timeoutMillis = 8_000,
                cancellationRequested = { false }
            )
        }.exceptionOrNull()
        val interruptWasPreserved = Thread.interrupted()

        assertTrue(failure is CancellationException)
        assertTrue(process.destroyed)
        assertTrue(interruptWasPreserved)
    }

    private class WaitingProcess(
        private val interruptOnWait: Boolean = false
    ) : Process() {
        var destroyed = false

        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 0
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (interruptOnWait) throw InterruptedException("test interruption")
            return false
        }
        override fun exitValue(): Int = if (destroyed) 0 else throw IllegalThreadStateException()
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process {
            destroyed = true
            return this
        }
        override fun isAlive(): Boolean = !destroyed
    }
}
