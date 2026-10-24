package com.aegis.apa

import com.aegis.apa.tool.collectProcessOutput
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundedProcessExecutionTest {
    @Test
    fun drainsLargeOutputBeforeWaitingAndRetainsOnlyTheLimit() {
        val process = ControlledProcess(ByteArray(256 * 1024) { 'x'.code.toByte() })
        val result = collectProcessOutput(process, 2_000, 1024) { false }
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertEquals("x".repeat(1024), result.output)
        assertTrue(result.truncated)
        assertTrue(process.stdout.closed)
        assertTrue(process.stderr.closed)
        assertTrue(process.stdin.closed)
    }

    @Test
    fun deniedExitPreservesExitCodeForCallerClassification() {
        val process = ControlledProcess("Permission denied".toByteArray(), exit = 1)
        val result = collectProcessOutput(process, 2_000, 1024) { false }
        assertEquals(1, result.exitCode)
        assertEquals("Permission denied", result.output)
        assertFalse(result.truncated)
    }

    @Test
    fun timeoutDestroysProcessAndClosesEveryStream() {
        val process = ControlledProcess("MODEL=Partial".toByteArray(), hangs = true)
        val result = collectProcessOutput(process, 20, 1024) { false }
        assertTrue(result.timedOut)
        assertTrue(process.destroyed)
        assertTrue(process.stdout.closed)
        assertTrue(process.stderr.closed)
        assertTrue(process.stdin.closed)
    }

    @Test
    fun cancellationDuringWaitDestroysProcessAndClosesEveryStream() {
        val process = ControlledProcess("MODEL=Partial".toByteArray(), hangs = true)
        val failure = runCatching {
            collectProcessOutput(process, 2_000, 1024) { process.waited }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(process.destroyed)
        assertTrue(process.stdout.closed)
        assertTrue(process.stderr.closed)
        assertTrue(process.stdin.closed)
    }

    @Test
    fun interruptedWaitPreservesInterruptionAndClosesEveryStream() {
        val process = ControlledProcess(byteArrayOf(), interruptWait = true)
        val failure = try {
            runCatching { collectProcessOutput(process, 2_000, 1024) { false } }.exceptionOrNull()
        } finally {
            assertTrue(Thread.interrupted())
        }
        assertTrue(failure is CancellationException)
        assertTrue(process.destroyed)
        assertTrue(process.stdout.closed)
        assertTrue(process.stderr.closed)
        assertTrue(process.stdin.closed)
    }

    @Test
    fun readFailureRetainsPartialOutputAndDisclosesIncompleteCapture() {
        val process = ControlledProcess("MODEL=Partial\n".toByteArray(), failRead = true)
        val result = collectProcessOutput(process, 2_000, 1024) { false }
        assertEquals("MODEL=Partial\n", result.output)
        assertTrue(result.truncated)
        assertFalse(result.timedOut)
    }

    @Test
    fun drainsErrorStreamAlongsideStandardOutput() {
        val process = ControlledProcess("MODEL=Phone\n".toByteArray(), error = "note".toByteArray())
        val result = collectProcessOutput(process, 2_000, 1024) { false }
        assertTrue(result.output.contains("MODEL=Phone\n"))
        assertTrue(result.output.contains("note"))
        assertFalse(result.truncated)
    }

    private class ControlledProcess(
        bytes: ByteArray,
        private val exit: Int = 0,
        private val hangs: Boolean = false,
        failRead: Boolean = false,
        error: ByteArray = byteArrayOf(),
        private val interruptWait: Boolean = false
    ) : Process() {
        private val drained = CountDownLatch(2)
        val stdout = TrackedInput(bytes, drained, failRead)
        val stderr = TrackedInput(error, drained, false)
        val stdin = TrackedOutput()
        @Volatile var destroyed = false
        @Volatile var waited = false
        override fun getInputStream() = stdout
        override fun getErrorStream() = stderr
        override fun getOutputStream() = stdin
        override fun waitFor(): Int { drained.await(); return exit }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            waited = true
            if (interruptWait) throw InterruptedException("test wait interruption")
            if (hangs) { unit.sleep(timeout); return false }
            return drained.await(timeout, unit)
        }
        override fun exitValue() = exit
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroyed = true; return this }
        override fun isAlive() = hangs && !destroyed
    }

    private class TrackedOutput : ByteArrayOutputStream() {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    private class TrackedInput(
        bytes: ByteArray,
        private val drained: CountDownLatch,
        private val failRead: Boolean
    ) : InputStream() {
        private val source = ByteArrayInputStream(bytes)
        @Volatile var closed = false
        override fun read(): Int {
            val buffer = ByteArray(1)
            return if (read(buffer, 0, 1) == -1) -1 else buffer[0].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = source.read(buffer, offset, length)
            if (count < 0) {
                drained.countDown()
                if (failRead) throw IOException("incomplete stream")
            }
            return count
        }
        override fun close() { closed = true; source.close(); drained.countDown() }
    }
}
