package com.aegis.apa

import com.aegis.apa.share.ReportCardCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReportCardCacheTest {
    @Test
    fun preparingShareTargetRemovesOlderGeneratedCards() {
        val directory = Files.createTempDirectory("apa-report-card-test").toFile()
        try {
            val oldPng = directory.resolve("APA-1.png").apply { writeText("old") }
            val unrelated = directory.resolve("keep.txt").apply { writeText("keep") }

            val target = ReportCardCache.prepareTarget(directory, nowMillis = 2L)

            assertFalse(oldPng.exists())
            assertTrue(unrelated.exists())
            assertEquals("APA-2.png", target.name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
