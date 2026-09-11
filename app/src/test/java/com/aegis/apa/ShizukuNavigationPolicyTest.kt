package com.aegis.apa

import com.aegis.apa.navigation.ShizukuDestination
import com.aegis.apa.navigation.ShizukuNavigationPolicy
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuNavigationPolicyTest {
    @Test
    fun missingShizukuUsesOfficialHttpsDownloadPageInsteadOfAnAppStore() {
        val destination = ShizukuNavigationPolicy.destination(isInstalled = false)

        assertTrue(destination is ShizukuDestination.Browser)
        val uri = URI((destination as ShizukuDestination.Browser).url)
        assertEquals("https", uri.scheme)
        assertEquals("shizuku.rikka.app", uri.host)
        assertEquals("/download/", uri.path)
        assertTrue(!destination.url.startsWith("market://"))
        assertTrue(!destination.url.contains("play.google.com"))
    }

    @Test
    fun installedShizukuStillUsesTheLocalApp() {
        val destination = ShizukuNavigationPolicy.destination(isInstalled = true)

        assertEquals(ShizukuDestination.InstalledApp, destination)
    }
}
