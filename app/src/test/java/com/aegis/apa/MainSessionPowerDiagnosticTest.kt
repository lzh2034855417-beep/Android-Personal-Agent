package com.aegis.apa

import com.aegis.apa.model.PowerDiagnosticSnapshot
import com.aegis.apa.model.SystemPowerEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MainSessionPowerDiagnosticTest {
    @Test
    fun diagnosticSelectionIsExplicitAndSnapshotIsTyped() {
        val session = MainSessionViewModel()
        assertNull(session.powerDiagnostic.value)
        assertEquals(PowerDiagnosticUiState.Idle, session.powerDiagnosticState.value)

        session.powerDiagnostic.value = PowerDiagnosticSnapshot(
            Instant.EPOCH, 10, emptyMap(), emptyList(), SystemPowerEvidence(), emptyList()
        )
        session.agent.includePowerDiagnosticReport.value = true

        assertTrue(session.agent.includePowerDiagnosticReport.value)
        assertEquals(Instant.EPOCH, session.powerDiagnostic.value?.sampledAtInstant)
    }
}
