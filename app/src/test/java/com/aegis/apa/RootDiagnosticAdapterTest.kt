package com.aegis.apa

import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.AllowedRootCommand
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.tool.PowerDiagnosticParser
import com.aegis.apa.tool.RawDiagnosticSection
import com.aegis.apa.tool.RootCommandResult
import com.aegis.apa.tool.RootDiagnosticAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RootDiagnosticAdapterTest {
    @Test
    fun preservesRootResultStatusOutputTruncationAndDetail() {
        val result = RootCommandResult(
            command = AllowedRootCommand.ALARM,
            status = DiagnosticSourceStatus.TRUNCATED,
            output = "alarm excerpt",
            truncated = true,
            detail = "输出过长"
        )

        assertEquals(
            RawDiagnosticSection(
                source = "alarm",
                status = DiagnosticSourceStatus.TRUNCATED,
                output = "alarm excerpt",
                truncated = true,
                detail = "输出过长"
            ),
            RootDiagnosticAdapter.toSection(result)
        )
    }

    @Test
    fun importedAndRootSectionsProduceTheSameParsedEvidence() {
        val imported = BugReportSectionExtractor.extract(
            input = """
                ------ DUMPSYS package (dumpsys package packages) ------
                package:com.example.chat uid:10123
                ------ DUMPSYS batterystats (dumpsys batterystats --charged) ------
                Estimated power use (mAh):
                  UID u0a123: 240.0 fg: 10.0 bg: 230.0
            """.trimIndent().byteInputStream(),
            displayName = "bugreport.txt"
        ) as BugReportReadResult.Success
        val rootSections = RootDiagnosticAdapter.toSections(
            listOf(
                RootCommandResult(
                    AllowedRootCommand.PACKAGES,
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.example.chat uid:10123"
                ),
                RootCommandResult(
                    AllowedRootCommand.BATTERYSTATS,
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID u0a123: 240.0 fg: 10.0 bg: 230.0"
                )
            )
        )

        val importedSnapshot = PowerDiagnosticParser.parse(imported.sections, Instant.EPOCH)
        val rootSnapshot = PowerDiagnosticParser.parse(rootSections, Instant.EPOCH)

        assertEquals(rootSnapshot.sources, importedSnapshot.sources)
        assertEquals(rootSnapshot.apps, importedSnapshot.apps)
        assertEquals(rootSnapshot.system, importedSnapshot.system)
    }
}
