package com.aegis.apa.tool

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal data class ProcessOutput(
    val exitCode: Int,
    val output: String,
    val truncated: Boolean,
    val timedOut: Boolean
)

internal fun checkCollectionCancellation(cancellationRequested: () -> Boolean) {
    if (Thread.currentThread().isInterrupted || cancellationRequested()) {
        throw CancellationException("Command collection cancelled")
    }
}

/** Drains while the child runs, including bytes beyond the retained prefix so pipes cannot fill. */
internal fun collectProcessOutput(
    process: Process,
    timeoutMillis: Long,
    maxBytes: Int,
    cancellationRequested: () -> Boolean
): ProcessOutput {
    val capture = ByteArrayOutputStream(maxBytes.coerceIn(0, 32 * 1024))
    val truncated = AtomicBoolean(false)
    val readers = mutableListOf<Thread>()
    fun drain(input: InputStream): Thread = thread(name = "command-output-reader", isDaemon = true) {
        try {
            input.use {
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    synchronized(capture) {
                        val remaining = (maxBytes - capture.size()).coerceAtLeast(0)
                        if (remaining > 0) capture.write(buffer, 0, minOf(count, remaining))
                        if (count > remaining) truncated.set(true)
                    }
                }
            }
        } catch (_: Exception) {
            // A partial prefix is usable only with an explicit incomplete-output warning.
            truncated.set(true)
        }
    }
    try {
        checkCollectionCancellation(cancellationRequested)
        runCatching { process.outputStream.close() }
        readers += drain(process.inputStream)
        readers += drain(process.errorStream)
        when (waitForRootProcess(process, timeoutMillis, cancellationRequested)) {
            RootProcessWaitOutcome.CANCELLED -> throw CancellationException("Command collection cancelled")
            RootProcessWaitOutcome.TIMED_OUT -> return ProcessOutput(-1, "", truncated.get(), true)
            RootProcessWaitOutcome.FINISHED -> Unit
        }
        // Some descendants may inherit a pipe after the command exits. Bound the drain wait too.
        val drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (readers.any { it.isAlive } && System.nanoTime() < drainDeadline) {
            checkCollectionCancellation(cancellationRequested)
            readers.filter { it.isAlive }.forEach { it.join(25) }
        }
        if (readers.any { it.isAlive }) truncated.set(true)
        checkCollectionCancellation(cancellationRequested)
        return synchronized(capture) {
            ProcessOutput(process.exitValue(), capture.toString(Charsets.UTF_8.name()), truncated.get(), false)
        }
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CancellationException("Command collection interrupted").also { it.initCause(interrupted) }
    } finally {
        if (runCatching { process.isAlive }.getOrDefault(true)) runCatching { process.destroyForcibly() }
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        // Do not wait indefinitely for a broken stream implementation; readers are daemons and
        // only touch the bounded capture, never caller state, after this point.
        readers.forEach { it.interrupt() }
    }
}
