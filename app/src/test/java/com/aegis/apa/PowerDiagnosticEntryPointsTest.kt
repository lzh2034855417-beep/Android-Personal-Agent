package com.aegis.apa

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerDiagnosticEntryPointsTest {
    @Test
    fun ordinaryUsersCanImportWithoutSeeingRootCollection() {
        assertEquals(
            listOf(PowerDiagnosticEntryPoint.IMPORT_BUGREPORT),
            PowerDiagnosticEntryPoints.forRootAvailability(rootAvailable = false)
        )
    }

    @Test
    fun rootUsersSeeImportAndReadOnlyCollectionSeparately() {
        assertEquals(
            listOf(
                PowerDiagnosticEntryPoint.IMPORT_BUGREPORT,
                PowerDiagnosticEntryPoint.ROOT_READ_ONLY
            ),
            PowerDiagnosticEntryPoints.forRootAvailability(rootAvailable = true)
        )
    }
}
