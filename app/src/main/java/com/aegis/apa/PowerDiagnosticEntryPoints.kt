package com.aegis.apa

enum class PowerDiagnosticEntryPoint {
    IMPORT_BUGREPORT,
    ROOT_READ_ONLY
}

object PowerDiagnosticEntryPoints {
    fun forRootAvailability(rootAvailable: Boolean): List<PowerDiagnosticEntryPoint> = buildList {
        add(PowerDiagnosticEntryPoint.IMPORT_BUGREPORT)
        if (rootAvailable) add(PowerDiagnosticEntryPoint.ROOT_READ_ONLY)
    }
}
