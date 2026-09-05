package com.aegis.apa

import com.aegis.apa.tool.UsageSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSummaryTest {
    @Test
    fun selectsTheMostUsedAppsAndSumsForegroundTime() {
        val summary = UsageSummaryBuilder.from(
            accessGranted = true,
            appDurationsMillis = mapOf(
                "pkg.a" to 3_600_000L,
                "pkg.b" to 1_800_000L,
                "pkg.idle" to 0L
            ),
            labels = mapOf("pkg.a" to "App A", "pkg.b" to "App B")
        )

        assertEquals(5_400_000L, summary.foregroundTimeMillis)
        assertEquals("App A", summary.topApps.first().label)
        assertEquals(2, summary.topApps.size)
        assertTrue(summary.accessGranted)
    }
}
