package com.aegis.apa.navigation

sealed interface ShizukuDestination {
    data object InstalledApp : ShizukuDestination
    data class Browser(val url: String) : ShizukuDestination
}

object ShizukuNavigationPolicy {
    fun destination(isInstalled: Boolean): ShizukuDestination =
        if (isInstalled) {
            ShizukuDestination.InstalledApp
        } else {
            ShizukuDestination.Browser("https://shizuku.rikka.app/download/")
        }
}
