package com.aegis.apa.tool

object RootDiagnosticAdapter {
    fun toSection(result: RootCommandResult): RawDiagnosticSection = RawDiagnosticSection(
        source = result.command.source,
        status = result.status,
        output = result.output,
        truncated = result.truncated,
        detail = result.detail
    )

    fun toSections(results: List<RootCommandResult>): List<RawDiagnosticSection> =
        results.map(::toSection)
}
