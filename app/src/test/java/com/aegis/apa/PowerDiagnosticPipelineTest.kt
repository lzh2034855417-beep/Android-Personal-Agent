package com.aegis.apa

import com.aegis.apa.model.DiagnosticInputSource
import com.aegis.apa.model.DiagnosticSourceStatus
import com.aegis.apa.tool.PowerDiagnosticPipeline
import com.aegis.apa.tool.RawDiagnosticSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PowerDiagnosticPipelineTest {
    @Test
    fun importedSectionsReceiveALocalVerdictBeforeAi() {
        val snapshot = PowerDiagnosticPipeline.analyze(
            sections = listOf(
                RawDiagnosticSection(
                    "packages",
                    DiagnosticSourceStatus.AVAILABLE,
                    "package:com.example.chat uid:10123"
                ),
                RawDiagnosticSection(
                    "batterystats",
                    DiagnosticSourceStatus.AVAILABLE,
                    "Estimated power use (mAh):\n  UID u0a123: 240.0"
                )
            ),
            inputSource = DiagnosticInputSource.BUGREPORT,
            sampledAt = Instant.EPOCH,
            collectionDurationMillis = 50
        )

        assertEquals(DiagnosticInputSource.BUGREPORT, snapshot.inputSource)
        assertEquals("com.example.chat", snapshot.apps.single().packageNames.single())
        assertTrue(snapshot.localVerdict?.totalConsumption?.isNotEmpty() == true)
        assertTrue(snapshot.findings.isNotEmpty())
    }
}
