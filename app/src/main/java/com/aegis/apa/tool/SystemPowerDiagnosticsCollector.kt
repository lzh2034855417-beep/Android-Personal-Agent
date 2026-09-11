package com.aegis.apa.tool

import com.aegis.apa.agent.PowerDiagnosticFindingEngine
import com.aegis.apa.model.PowerDiagnosticSnapshot
import java.time.Clock

class SystemPowerDiagnosticsCollector(
    private val runner: DiagnosticCommandRunner = RootCommandRunner,
    private val clock: Clock = Clock.systemUTC()
) {
    fun collect(onProgress: ((completed: Int, total: Int, source: String) -> Unit)? = null): PowerDiagnosticSnapshot {
        val startedNanos = System.nanoTime()
        val commands = AllowedRootCommand.entries
        val sections = commands.mapIndexed { index, command ->
            val result = runner.run(command)
            onProgress?.invoke(index + 1, commands.size, command.source)
            RawDiagnosticSection(
                source = command.source,
                status = result.status,
                output = result.output,
                truncated = result.truncated,
                detail = result.detail
            )
        }
        val elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000
        val parsed = PowerDiagnosticParser.parse(
            sections = sections,
            sampledAt = clock.instant(),
            collectionDurationMillis = elapsedMillis
        )
        return parsed.copy(findings = PowerDiagnosticFindingEngine.find(parsed))
    }
}
