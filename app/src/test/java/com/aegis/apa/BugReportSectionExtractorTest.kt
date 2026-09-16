package com.aegis.apa

import com.aegis.apa.tool.BugReportReadLimits
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportRejectReason
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.model.DiagnosticSourceStatus
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
    fun defaultLimitsCoverObservedXiaomi17ProMaxEngineeringReport() {
        val limits = BugReportReadLimits()

        assertTrue(limits.maxEntries >= 855)
        assertTrue(limits.maxSkippedBytes >= 269_797_153L)
        assertTrue(limits.maxEntryBytes >= 162_451_080L)
        assertTrue(limits.maxTotalBytes >= 162_451_080L)
    }

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
    fun toleratesSparseNulBytesInXiaomiReportText() {
        val reportWithSparseNuls = reportText.replace("245.5", "245\u0000.5")

        val result = BugReportSectionExtractor.extract(
            input = reportWithSparseNuls.byteInputStream(),
            displayName = "bugreport-Xiaomi.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertTrue((result as BugReportReadResult.Success).sections.first().output.contains("245.5"))
    }

    @Test
    fun rejectsReportTextWithExcessiveNulBytes() {
        val binaryLikeReport = reportText + "\u0000".repeat(1_025)

        val result = BugReportSectionExtractor.extract(
            input = binaryLikeReport.byteInputStream(),
            displayName = "bugreport-device.txt"
        )

        assertEquals(rejected(BugReportRejectReason.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun extractsWhitelistedSectionsFromPriorityServiceMarkers() {
        val priorityReport = """
            DUMP OF SERVICE CRITICAL power:
            Wake Locks: size=2
            DUMP OF SERVICE HIGH thermalservice:
            Thermal Status: 1
        """.trimIndent()

        val result = BugReportSectionExtractor.extract(
            input = priorityReport.byteInputStream(),
            displayName = "bugreport-priority.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("power", "thermalservice"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
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
    fun ignoresOversizedUnrelatedAttachmentBeforeMainReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(512) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxEntryBytes = 256)
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun unrelatedAttachmentDoesNotConsumeMainReportBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(512) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 256,
                maxTotalBytes = 256,
                maxSkippedBytes = 1024
            )
        )

        assertTrue(result is BugReportReadResult.Success)
    }

    @Test
    fun rejectsWhenDiscardedAttachmentsExceedTheirOwnBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(513) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxSkippedBytes = 512)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun directoryPayloadCannotBypassDiscardBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/" to ByteArray(513) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxSkippedBytes = 512)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun cancellationStopsDiscardingDirectoryPayloadPromptly() {
        val payload = ByteArray(1024 * 1024).also { Random(13).nextBytes(it) }
        val archive = zipBytesOf(
            "FS/" to payload,
            "bugreport-device.txt" to reportText.toByteArray()
        )
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport-device.zip",
            isCancelled = { counted.bytesRead > 16 * 1024 }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
        assertTrue(
            "Cancellation should not drain a directory payload",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun ignoresTextWhoseNameOnlyStartsWithBugreportWord() {
        val misleading = """
            DUMP OF SERVICE power:
            should not be selected
        """.trimIndent()
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "bugreportjunk.txt" to misleading,
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun cancellationStopsDiscardingUnrelatedAttachmentPromptly() {
        val attachment = ByteArray(1024 * 1024).also { Random(11).nextBytes(it) }
        val archive = zipBytesOf(
            "FS/random.bin" to attachment,
            "bugreport-device.txt" to reportText.toByteArray()
        )
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport-device.zip",
            isCancelled = { counted.bytesRead > 16 * 1024 }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
        assertTrue(
            "Cancellation should not drain an unrelated compressed attachment",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun ignoresOversizedSystraceTextAttachmentBeforeMainReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "systrace.txt" to "trace".repeat(128),
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxEntryBytes = 256)
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun acceptsMainReportLargerThanLegacyEightMiBLimit() {
        val largeReport = reportText + "\n" + "x".repeat(8 * 1024 * 1024)

        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-large.txt" to largeReport).inputStream(),
            displayName = "bugreport-large.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            DiagnosticSourceStatus.TRUNCATED,
            (result as BugReportReadResult.Success).sections.last().status
        )
    }

    @Test
    fun retainsLateEvidenceFromLargeXiaomiDiagnosticSections() {
        val filler = "x\n".repeat(1_100_000)
        val largeReport = buildString {
            appendLine("DUMP OF SERVICE batterystats:")
            append(filler)
            appendLine("Estimated power use (mAh):")
            appendLine("  UID u0a123: 245.5")
            appendLine("DUMP OF SERVICE packages:")
            append(filler)
            appendLine("  Package [com.example.chat]")
            appendLine("    userId=10123")
        }

        val result = BugReportSectionExtractor.extract(
            input = largeReport.byteInputStream(),
            displayName = "bugreport-Xiaomi.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        val sections = (result as BugReportReadResult.Success).sections.associateBy { it.source }
        assertTrue(sections.getValue("batterystats").output.contains("UID u0a123: 245.5"))
        assertTrue(sections.getValue("packages").output.contains("userId=10123"))
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
    fun plainTextAlsoRespectsPerReportByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt",
            limits = BugReportReadLimits(maxEntryBytes = 32, maxTotalBytes = 1024)
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
    fun ignoresNestedAttachmentBeforeMainXiaomiReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "FS/extra-report.zip" to "nested attachment is not opened",
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun acceptsXiaomiReportAfterManyBoundedAttachments() {
        val attachments = (0 until 256).map { index ->
            "FS/attachment-$index.txt" to ""
        }
        val archiveEntries = attachments + listOf(
            "FS/extra-report.zip" to "nested attachment is not opened",
            "bugreport-device.txt" to reportText
        )

        val result = BugReportSectionExtractor.extract(
            input = zipOf(*archiveEntries.toTypedArray()).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun readsMainReportFromOneNestedXiaomiBugReportArchive() {
        val nestedReport = zipOf("bugreport-device.txt" to reportText)
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/encrypt_voice_trigger.zip" to byteArrayOf(1, 2, 3),
                "bugreport-Xiaomi 17 Pro Max-2026-09-15-154937.zip" to nestedReport
            ).inputStream(),
            displayName = "bugreport-2026-09-15-154647.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
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
