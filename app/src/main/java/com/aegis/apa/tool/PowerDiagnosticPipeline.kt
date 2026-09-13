package com.aegis.apa.tool

import com.aegis.apa.agent.LocalPowerAttributionEngine
import com.aegis.apa.agent.PowerDiagnosticFindingEngine
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import java.time.Instant

object PowerDiagnosticPipeline {
    fun analyze(
        sections: List<RawDiagnosticSection>,
        inputSource: DiagnosticInputSource,
        sampledAt: Instant,
        collectionDurationMillis: Long = 0L
    ): PowerDiagnosticSnapshot {
        val parsed = PowerDiagnosticParser.parse(
            sections = sections,
            sampledAt = sampledAt,
            collectionDurationMillis = collectionDurationMillis
        )
        val sourced = parsed.copy(inputSource = inputSource)
        return sourced.copy(
            findings = PowerDiagnosticFindingEngine.find(sourced),
            localVerdict = LocalPowerAttributionEngine.attribute(sourced)
        )
    }
}
