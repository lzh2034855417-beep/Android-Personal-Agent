package com.aegis.apa

import com.aegis.apa.tool.BugReportReadLimits
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportRejectReason
import com.aegis.apa.tool.BugReportSectionExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BugReportSectionExtractorTest {
    private val reportText = """
        ------ DUMPSYS batterystats (dumpsys batterystats --checkin) ------
        Estimated power use (mAh):
          UID u0a123: 245.5
        ------ DUMPSYS alarm (dumpsys alarm) ------
        u0a123: 180 wakeups
    """.trimIndent()

    @Test
    fun extractsWhitelistedSectionsFromPlainText() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
        assertTrue(result.sections.first().output.contains("UID u0a123: 245.5"))
    }

    @Test
    fun readsReportTextInsideZip() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-device.txt" to reportText).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun rejectsEntryWhoseNameContainsParentTraversal() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("../bugreport.txt" to reportText).inputStream(),
            displayName = "bugreport.zip"
        )

        assertEquals(rejected(BugReportRejectReason.UNSAFE_ENTRY_NAME), result)
    }

    @Test
    fun rejectsMoreThanConfiguredEntryCount() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("a.txt" to "a", "b.txt" to "b", "c.txt" to "c").inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntries = 2)
        )

        assertEquals(rejected(BugReportRejectReason.TOO_MANY_ENTRIES), result)
    }

    @Test
    fun rejectsAnEntryOverItsByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport.txt" to "x".repeat(17)).inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16)
        )

        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), result)
    }

    @Test
    fun stopsInflatingImmediatelyAfterEntryLimitIsExceeded() {
        val payload = ByteArray(1024 * 1024).also { Random(7).nextBytes(it) }
        val archive = zipBytesOf("bugreport.txt" to payload)
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16)
        )

        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), result)
        assertTrue(
            "Rejected ZIP should not drain the remaining compressed payload",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun rejectsArchiveOverItsTotalByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-a.txt" to "a".repeat(11), "bugreport-b.txt" to "b".repeat(11)).inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16, maxTotalBytes = 20)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun rejectsNestedZipBeforeReadingItsPayload() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("reports/inner.zip" to "not read").inputStream(),
            displayName = "bugreport.zip"
        )

        assertEquals(rejected(BugReportRejectReason.NESTED_ARCHIVE), result)
    }

    @Test
    fun returnsUnsupportedForUnknownBinary() {
        val result = BugReportSectionExtractor.extract(
            input = byteArrayOf(0, 1, 2, 0, 3).inputStream(),
            displayName = "dump.bin"
        )

        assertEquals(rejected(BugReportRejectReason.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun stopsWhenAlreadyCancelled() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt",
            isCancelled = { true }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
    }

    private fun rejected(reason: BugReportRejectReason) = BugReportReadResult.Rejected(reason)

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        zipBytesOf(*entries.map { (name, value) -> name to value.toByteArray() }.toTypedArray())

    private fun zipBytesOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead += 1 }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    }
}
