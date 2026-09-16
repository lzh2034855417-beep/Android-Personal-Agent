package com.aegis.apa.tool

import com.aegis.apa.model.DiagnosticSourceStatus
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class BugReportReadLimits(
    val maxEntries: Int = 4_096,
    val maxEntryBytes: Long = 192L * 1024 * 1024,
    val maxTotalBytes: Long = 192L * 1024 * 1024,
    val maxSkippedBytes: Long = 384L * 1024 * 1024,
    val maxSectionChars: Int = 2 * 1024 * 1024,
    val maxBatteryStatsSectionChars: Int = 12 * 1024 * 1024,
    val maxPackagesSectionChars: Int = 10 * 1024 * 1024
)

sealed interface BugReportReadResult {
    data class Success(val sections: List<RawDiagnosticSection>) : BugReportReadResult
    data class Rejected(val reason: BugReportRejectReason) : BugReportReadResult
    data object Cancelled : BugReportReadResult
}

enum class BugReportRejectReason {
    EMPTY,
    UNSUPPORTED_FORMAT,
    CORRUPT_ARCHIVE,
    UNSAFE_ENTRY_NAME,
    NESTED_ARCHIVE,
    TOO_MANY_ENTRIES,
    ENTRY_TOO_LARGE,
    TOTAL_TOO_LARGE
}

object BugReportSectionExtractor {
    private val zipSignature = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val dumSysMarker = Regex("^------\\s+DUMPSYS\\s+([A-Za-z0-9_.-]+)\\s+.*------\\s*$", RegexOption.IGNORE_CASE)
    private val serviceMarker = Regex(
        "^DUMP OF SERVICE\\s+(?:(?:CRITICAL|HIGH|NORMAL)\\s+)?([A-Za-z0-9_.-]+):\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val drivePrefix = Regex("^[A-Za-z]:")
    private val allowedSources = setOf(
        "batterystats",
        "alarm",
        "jobscheduler",
        "packages",
        "power",
        "deviceidle",
        "thermalservice"
    )

    fun extract(
        input: InputStream,
        displayName: String?,
        limits: BugReportReadLimits = BugReportReadLimits(),
        isCancelled: () -> Boolean = { false }
    ): BugReportReadResult {
        if (isCancelled()) return BugReportReadResult.Cancelled
        if (!limits.areValid()) return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)

        val buffered = PushbackInputStream(input, zipSignature.size)
        val prefix = ByteArray(zipSignature.size)
        val prefixSize = readPrefix(buffered, prefix, isCancelled)
        if (prefixSize < 0) return BugReportReadResult.Cancelled
        if (prefixSize == 0) return BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
        buffered.unread(prefix, 0, prefixSize)

        return if (prefixSize == zipSignature.size && prefix.contentEquals(zipSignature)) {
            extractZip(buffered, limits, isCancelled)
        } else {
            extractPlainText(buffered, displayName, limits, isCancelled)
        }
    }

    private fun extractPlainText(
        input: InputStream,
        displayName: String?,
        limits: BugReportReadLimits,
        isCancelled: () -> Boolean
    ): BugReportReadResult {
        if (!isTextName(displayName)) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        return when (
            val read = readSectionsStreaming(
                input = input,
                entryLimit = limits.maxEntryBytes,
                totalRemaining = limits.maxTotalBytes,
                limits = limits,
                isCancelled = isCancelled
            )
        ) {
            CandidateRead.Cancelled -> BugReportReadResult.Cancelled
            CandidateRead.EntryTooLarge -> BugReportReadResult.Rejected(BugReportRejectReason.ENTRY_TOO_LARGE)
            CandidateRead.TotalTooLarge -> BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
            CandidateRead.Unsupported -> BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
            is CandidateRead.Empty -> BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
            is CandidateRead.Sections -> BugReportReadResult.Success(read.value)
        }
    }

    private fun extractZip(
        input: InputStream,
        limits: BugReportReadLimits,
        isCancelled: () -> Boolean,
        allowNestedReport: Boolean = true
    ): BugReportReadResult {
        var entryCount = 0
        var reportBytes = 0L
        var skippedBytes = 0L
        var sawNestedArchive = false
        return try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    if (isCancelled()) return BugReportReadResult.Cancelled
                    val entry = zip.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > limits.maxEntries) {
                        return BugReportReadResult.Rejected(BugReportRejectReason.TOO_MANY_ENTRIES)
                    }
                    if (!isSafeEntryName(entry.name)) {
                        return BugReportReadResult.Rejected(BugReportRejectReason.UNSAFE_ENTRY_NAME)
                    }
                    if (!entry.isDirectory && isZipEntry(entry.name)) {
                        sawNestedArchive = true
                        if (allowNestedReport && isReportZipEntry(entry.name)) {
                            val nestedInput = NonClosingInputStream(
                                CheckedInputStream(
                                    input = zip,
                                    entryLimit = limits.maxEntryBytes,
                                    totalRemaining = limits.maxTotalBytes,
                                    isCancelled = isCancelled
                                )
                            )
                            return extractZip(
                                input = nestedInput,
                                limits = limits,
                                isCancelled = isCancelled,
                                allowNestedReport = false
                            )
                        }
                        when (val skipped = skipBounded(zip, limits.maxSkippedBytes - skippedBytes, isCancelled)) {
                            BoundedSkip.Cancelled -> return BugReportReadResult.Cancelled
                            BoundedSkip.TotalTooLarge -> {
                                return BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
                            }
                            is BoundedSkip.Bytes -> skippedBytes += skipped.value
                        }
                        zip.closeEntry()
                        continue
                    }
                    if (!isReportTextEntry(entry.name)) {
                        when (val skipped = skipBounded(zip, limits.maxSkippedBytes - skippedBytes, isCancelled)) {
                            BoundedSkip.Cancelled -> return BugReportReadResult.Cancelled
                            BoundedSkip.TotalTooLarge -> {
                                return BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
                            }
                            is BoundedSkip.Bytes -> skippedBytes += skipped.value
                        }
                        zip.closeEntry()
                        continue
                    }
                    when (
                        val read = readSectionsStreaming(
                            input = zip,
                            entryLimit = limits.maxEntryBytes,
                            totalRemaining = limits.maxTotalBytes - reportBytes,
                            limits = limits,
                            isCancelled = isCancelled
                        )
                    ) {
                        CandidateRead.Cancelled -> return BugReportReadResult.Cancelled
                        CandidateRead.EntryTooLarge -> {
                            return BugReportReadResult.Rejected(BugReportRejectReason.ENTRY_TOO_LARGE)
                        }
                        CandidateRead.TotalTooLarge -> {
                            return BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
                        }
                        CandidateRead.Unsupported -> {
                            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
                        }
                        is CandidateRead.Empty -> {
                            reportBytes += read.bytesRead
                            zip.closeEntry()
                        }
                        is CandidateRead.Sections -> {
                            zip.closeEntry()
                            return BugReportReadResult.Success(read.value)
                        }
                    }
                }
            }
            BugReportReadResult.Rejected(
                if (sawNestedArchive) BugReportRejectReason.NESTED_ARCHIVE else BugReportRejectReason.EMPTY
            )
        } catch (_: CancelledReadException) {
            BugReportReadResult.Cancelled
        } catch (error: LimitReadException) {
            BugReportReadResult.Rejected(error.reason)
        } catch (_: ZipException) {
            BugReportReadResult.Rejected(BugReportRejectReason.CORRUPT_ARCHIVE)
        } catch (_: IOException) {
            BugReportReadResult.Rejected(BugReportRejectReason.CORRUPT_ARCHIVE)
        }
    }

    private fun readSectionsStreaming(
        input: InputStream,
        entryLimit: Long,
        totalRemaining: Long,
        limits: BugReportReadLimits,
        isCancelled: () -> Boolean
    ): CandidateRead {
        if (totalRemaining <= 0) return CandidateRead.TotalTooLarge
        val outputs = linkedMapOf<String, StringBuilder>()
        val truncated = mutableSetOf<String>()
        var currentSource: String? = null
        val line = StringBuilder()
        var lineTruncated = false
        var nulCharCount = 0

        fun consumeLine() {
            val value = line.toString().removeSuffix("\r")
            val markerSource = if (lineTruncated) null else markerSource(value)
            if (markerSource != null) {
                currentSource = markerSource.takeIf(allowedSources::contains)
                currentSource?.let { outputs.putIfAbsent(it, StringBuilder()) }
            } else {
                currentSource?.let { source ->
                    val output = outputs.getValue(source)
                    val separatorLength = if (output.isEmpty()) 0 else 1
                    val sectionLimit = when (source) {
                        "batterystats" -> limits.maxBatteryStatsSectionChars
                        "packages" -> limits.maxPackagesSectionChars
                        else -> limits.maxSectionChars
                    }
                    val available = sectionLimit - output.length - separatorLength
                    if (available <= 0) {
                        truncated += source
                    } else {
                        if (separatorLength == 1) output.append('\n')
                        output.append(value.take(available))
                        if (lineTruncated || value.length > available) truncated += source
                    }
                }
            }
            line.clear()
            lineTruncated = false
        }

        val bounded = CheckedInputStream(input, entryLimit, totalRemaining, isCancelled)
        return try {
            val reader = InputStreamReader(bounded, Charsets.UTF_8)
            val chars = CharArray(16 * 1024)
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                for (index in 0 until count) {
                    when (val char = chars[index]) {
                        '\u0000' -> {
                            nulCharCount += 1
                            if (nulCharCount > MAX_TOLERATED_NUL_CHARS) return CandidateRead.Unsupported
                        }
                        '\n' -> consumeLine()
                        else -> {
                            if (line.length < MAX_BUFFERED_LINE_CHARS) line.append(char)
                            else lineTruncated = true
                        }
                    }
                }
            }
            if (line.isNotEmpty() || lineTruncated) consumeLine()
            if (bounded.bytesRead == 0L || outputs.isEmpty()) {
                CandidateRead.Empty(bounded.bytesRead)
            } else {
                CandidateRead.Sections(
                    outputs.map { (source, output) ->
                        val wasTruncated = source in truncated
                        RawDiagnosticSection(
                            source = source,
                            status = if (wasTruncated) {
                                DiagnosticSourceStatus.TRUNCATED
                            } else {
                                DiagnosticSourceStatus.AVAILABLE
                            },
                            output = output.toString(),
                            truncated = wasTruncated,
                            detail = if (wasTruncated) "导入段落超过大小限制" else null
                        )
                    },
                    bounded.bytesRead
                )
            }
        } catch (_: CancelledReadException) {
            CandidateRead.Cancelled
        } catch (error: LimitReadException) {
            when (error.reason) {
                BugReportRejectReason.ENTRY_TOO_LARGE -> CandidateRead.EntryTooLarge
                else -> CandidateRead.TotalTooLarge
            }
        }
    }

    private fun markerSource(line: String): String? {
        val raw = dumSysMarker.matchEntire(line.trim())?.groupValues?.get(1)
            ?: serviceMarker.matchEntire(line.trim())?.groupValues?.get(1)
            ?: return null
        return when (raw.lowercase(Locale.ROOT)) {
            "package", "packages" -> "packages"
            else -> raw.lowercase(Locale.ROOT)
        }
    }

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith('/') &&
            '\\' !in name &&
            !drivePrefix.containsMatchIn(name) &&
            name.split('/').none { it == ".." }

    private fun isReportTextEntry(name: String): Boolean {
        val basename = name.substringAfterLast('/').lowercase(Locale.ROOT)
        return basename == "bugreport.txt" ||
            (basename.startsWith("bugreport-") && basename.endsWith(".txt"))
    }

    private fun isZipEntry(name: String): Boolean =
        name.lowercase(Locale.ROOT).endsWith(".zip")

    private fun isReportZipEntry(name: String): Boolean {
        val basename = name.substringAfterLast('/').lowercase(Locale.ROOT)
        return basename == "bugreport.zip" ||
            (basename.startsWith("bugreport-") && basename.endsWith(".zip"))
    }

    private fun isTextName(name: String?): Boolean {
        val value = name?.lowercase(Locale.ROOT) ?: return true
        return value.endsWith(".txt") || value.startsWith("bugreport")
    }

    private fun readPrefix(input: InputStream, target: ByteArray, isCancelled: () -> Boolean): Int {
        var offset = 0
        while (offset < target.size) {
            if (isCancelled()) return -1
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun skipBounded(
        input: InputStream,
        totalRemaining: Long,
        isCancelled: () -> Boolean
    ): BoundedSkip {
        if (totalRemaining <= 0) return BoundedSkip.TotalTooLarge
        val buffer = ByteArray(16 * 1024)
        var size = 0L
        while (true) {
            if (isCancelled()) return BoundedSkip.Cancelled
            val read = input.read(buffer)
            if (read < 0) break
            size += read
            if (size > totalRemaining) return BoundedSkip.TotalTooLarge
        }
        return BoundedSkip.Bytes(size)
    }

    private fun BugReportReadLimits.areValid(): Boolean =
        maxEntries > 0 && maxEntryBytes > 0 && maxTotalBytes > 0 && maxSkippedBytes > 0 &&
            maxSectionChars > 0 && maxBatteryStatsSectionChars > 0 && maxPackagesSectionChars > 0

    private sealed interface BoundedSkip {
        data class Bytes(val value: Long) : BoundedSkip
        data object Cancelled : BoundedSkip
        data object TotalTooLarge : BoundedSkip
    }

    private sealed interface CandidateRead {
        data class Sections(val value: List<RawDiagnosticSection>, val bytesRead: Long) : CandidateRead
        data class Empty(val bytesRead: Long) : CandidateRead
        data object Cancelled : CandidateRead
        data object Unsupported : CandidateRead
        data object EntryTooLarge : CandidateRead
        data object TotalTooLarge : CandidateRead
    }

    private class CheckedInputStream(
        input: InputStream,
        private val entryLimit: Long,
        private val totalRemaining: Long,
        private val isCancelled: () -> Boolean
    ) : FilterInputStream(input) {
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            checkCancelled()
            return super.read().also { if (it >= 0) record(1) }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkCancelled()
            return super.read(buffer, offset, length).also { if (it > 0) record(it.toLong()) }
        }

        private fun checkCancelled() {
            if (isCancelled()) throw CancelledReadException()
        }

        private fun record(count: Long) {
            bytesRead += count
            val entryExceeded = bytesRead > entryLimit
            val totalExceeded = bytesRead > totalRemaining
            if (entryExceeded || totalExceeded) {
                val reason = if (entryExceeded && entryLimit <= totalRemaining) {
                    BugReportRejectReason.ENTRY_TOO_LARGE
                } else {
                    BugReportRejectReason.TOTAL_TOO_LARGE
                }
                throw LimitReadException(reason)
            }
        }
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class CancelledReadException : IOException()
    private class LimitReadException(val reason: BugReportRejectReason) : IOException()

    private const val MAX_BUFFERED_LINE_CHARS = 64 * 1024
    private const val MAX_TOLERATED_NUL_CHARS = 1_024
}
