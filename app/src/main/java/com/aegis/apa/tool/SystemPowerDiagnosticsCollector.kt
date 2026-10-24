package com.aegis.apa.tool

import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.VerificationDeviceContext
import com.aegis.apa.model.VerificationOwnership
import java.time.Clock
import java.util.concurrent.CancellationException

class SystemPowerDiagnosticsCollector(
    private val runner: DiagnosticCommandRunner = RootCommandRunner,
    private val clock: Clock = Clock.systemUTC(),
    private val packageLabelResolver: (String) -> String? = { null },
    private val currentDeviceContext: VerificationDeviceContext? = null
) {
    fun collect(
        onProgress: ((completed: Int, total: Int, source: String) -> Unit)? = null,
        cancellationRequested: () -> Boolean = { false }
    ): PowerDiagnosticSnapshot {
        val startedNanos = System.nanoTime()
        val commands = AllowedRootCommand.entries
        val results = commands.mapIndexed { index, command ->
            ensureNotCancelled(cancellationRequested)
            val result = runner.run(command, cancellationRequested)
            ensureNotCancelled(cancellationRequested)
            onProgress?.invoke(index + 1, commands.size, command.source)
            result
        }
        val elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = RootDiagnosticAdapter.toSections(results),
            inputSource = DiagnosticInputSource.ROOT,
            sampledAt = clock.instant(),
            collectionDurationMillis = elapsedMillis,
            packageLabelResolver = packageLabelResolver
        )
        return snapshot.copy(verificationMetadata = snapshot.verificationMetadata?.copy(
            device = currentDeviceContext?.copy(ownership = VerificationOwnership.LOCAL_COLLECTION)
        ))
    }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (cancellationRequested()) {
            throw CancellationException("Root diagnostic collection cancelled")
        }
    }
}
