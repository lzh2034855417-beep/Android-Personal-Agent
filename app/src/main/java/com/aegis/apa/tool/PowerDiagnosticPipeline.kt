package com.aegis.apa.tool

import com.aegis.apa.agent.LocalPowerAttributionEngine
import com.aegis.apa.agent.PowerDiagnosticFindingEngine
import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.PowerDiagnosticSnapshot
import java.time.Instant

object PowerDiagnosticPipeline {
    /** Imported reports may be large; never parse them on the caller's UI thread. */
    suspend fun analyzeImported(
        imported: BugReportReadResult.Success,
        sampledAt: Instant,
        collectionDurationMillis: Long,
        packageLabelResolver: (String) -> String? = { null }
    ): PowerDiagnosticSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        analyze(imported.sections, DiagnosticInputSource.BUGREPORT, sampledAt,
            collectionDurationMillis, packageLabelResolver, imported.verificationHeaderFields)
    }

    fun analyze(
        sections: List<RawDiagnosticSection>,
        inputSource: DiagnosticInputSource,
        sampledAt: Instant,
        collectionDurationMillis: Long = 0L,
        packageLabelResolver: (String) -> String? = { null },
        verificationHeaderFields: Map<String, String> = emptyMap()
    ): PowerDiagnosticSnapshot {
        val parsed = PowerDiagnosticParser.parse(
            sections = sections,
            sampledAt = sampledAt,
            collectionDurationMillis = collectionDurationMillis
        )
        val sourced = parsed.copy(
            inputSource = inputSource,
            verificationMetadata = DiagnosticWindowMetadataParser.parse(sections, verificationHeaderFields),
            apps = parsed.apps.map { app ->
                app.copy(
                    displayNames = app.packageNames.map { packageName ->
                        normalizeApplicationLabel(packageLabelResolver(packageName)) ?: packageName
                    }
                )
            }
        )
        return sourced.copy(
            findings = PowerDiagnosticFindingEngine.find(sourced),
            localVerdict = LocalPowerAttributionEngine.attribute(sourced)
        )
    }

    private fun normalizeApplicationLabel(rawLabel: String?): String? {
        if (rawLabel == null) return null
        val singleLine = buildString(rawLabel.length.coerceAtMost(MAX_APPLICATION_LABEL_CHARS)) {
            rawLabel.forEach { character ->
                val isFormatCharacter = Character.getType(character) == Character.FORMAT.toInt()
                append(if (character.isISOControl() || isFormatCharacter) ' ' else character)
            }
        }.replace(Regex("\\s+"), " ").trim()
        return singleLine.take(MAX_APPLICATION_LABEL_CHARS).takeIf(String::isNotBlank)
    }

    private const val MAX_APPLICATION_LABEL_CHARS = 80
}
