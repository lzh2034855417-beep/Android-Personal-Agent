package com.aegis.apa.tool

import com.aegis.apa.model.DiagnosticSourceStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class BugReportReadLimits(
    val maxEntries: Int = 128,
    val maxEntryBytes: Long = 8L * 1024 * 1024,
    val maxTotalBytes: Long = 32L * 1024 * 1024,
    val maxSectionChars: Int = 2 * 1024 * 1024
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
    private val serviceMarker = Regex("^DUMP OF SERVICE\\s+([A-Za-z0-9_.-]+):\\s*$", RegexOption.IGNORE_CASE)
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
        return when (val read = readBounded(input, limits.maxTotalBytes, limits.maxTotalBytes, isCancelled)) {
            BoundedRead.Cancelled -> BugReportReadResult.Cancelled
            BoundedRead.EntryTooLarge,
            BoundedRead.TotalTooLarge -> BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
            is BoundedRead.Bytes -> sectionsResult(read.value, limits)
        }
    }

    private fun extractZip(
        input: InputStream,
        limits: BugReportReadLimits,
        isCancelled: () -> Boolean
    ): BugReportReadResult {
        val sections = mutableListOf<RawDiagnosticSection>()
        var entryCount = 0
        var totalBytes = 0L
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
                    if (!entry.isDirectory && entry.name.lowercase(Locale.ROOT).endsWith(".zip")) {
                        return BugReportReadResult.Rejected(BugReportRejectReason.NESTED_ARCHIVE)
                    }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    when (
                        val read = readBounded(
                            input = zip,
                            entryLimit = limits.maxEntryBytes,
                            totalRemaining = limits.maxTotalBytes - totalBytes,
                            isCancelled = isCancelled
                        )
                    ) {
                        BoundedRead.Cancelled -> return BugReportReadResult.Cancelled
                        BoundedRead.EntryTooLarge -> return BugReportReadResult.Rejected(BugReportRejectReason.ENTRY_TOO_LARGE)
                        BoundedRead.TotalTooLarge -> return BugReportReadResult.Rejected(BugReportRejectReason.TOTAL_TOO_LARGE)
                        is BoundedRead.Bytes -> {
                            totalBytes += read.value.size
                            zip.closeEntry()
                            if (isReportTextEntry(entry.name)) {
                                when (val parsed = sectionsResult(read.value, limits)) {
                                    is BugReportReadResult.Success -> sections += parsed.sections
                                    is BugReportReadResult.Rejected -> if (parsed.reason != BugReportRejectReason.EMPTY) return parsed
                                    BugReportReadResult.Cancelled -> return parsed
                                }
                            }
                        }
                    }
                }
            }
            mergeSections(sections, limits)
        } catch (_: ZipException) {
            BugReportReadResult.Rejected(BugReportRejectReason.CORRUPT_ARCHIVE)
        } catch (_: IOException) {
            BugReportReadResult.Rejected(BugReportRejectReason.CORRUPT_ARCHIVE)
        }
    }

    private fun sectionsResult(bytes: ByteArray, limits: BugReportReadLimits): BugReportReadResult {
        if (bytes.isEmpty()) return BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
        if (bytes.any { it == 0.toByte() }) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        val text = String(bytes, StandardCharsets.UTF_8)
        val sections = extractSections(text, limits)
        return if (sections.isEmpty()) {
            BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
        } else {
            BugReportReadResult.Success(sections)
        }
    }

    private fun extractSections(text: String, limits: BugReportReadLimits): List<RawDiagnosticSection> {
        val outputs = linkedMapOf<String, StringBuilder>()
        val truncated = mutableSetOf<String>()
        var currentSource: String? = null

        text.lineSequence().forEach { line ->
            val markerSource = markerSource(line)
            if (markerSource != null) {
                currentSource = markerSource.takeIf(allowedSources::contains)
                currentSource?.let { outputs.putIfAbsent(it, StringBuilder()) }
                return@forEach
            }
            currentSource?.let { source ->
                val output = outputs.getValue(source)
                val separatorLength = if (output.isEmpty()) 0 else 1
                val available = limits.maxSectionChars - output.length - separatorLength
                if (available <= 0) {
                    truncated += source
                } else {
                    if (separatorLength == 1) output.append('\n')
                    output.append(line.take(available))
                    if (line.length > available) truncated += source
                }
            }
        }

        return outputs.map { (source, output) ->
            val wasTruncated = source in truncated
            RawDiagnosticSection(
                source = source,
                status = if (wasTruncated) DiagnosticSourceStatus.TRUNCATED else DiagnosticSourceStatus.AVAILABLE,
                output = output.toString(),
                truncated = wasTruncated,
                detail = if (wasTruncated) "导入段落超过大小限制" else null
            )
        }
    }

    private fun mergeSections(
        sections: List<RawDiagnosticSection>,
        limits: BugReportReadLimits
    ): BugReportReadResult {
        if (sections.isEmpty()) return BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
        val merged = sections.groupBy { it.source }.map { (source, values) ->
            val text = values.joinToString("\n") { it.output }.take(limits.maxSectionChars)
            val truncated = values.any { it.truncated } || values.sumOf { it.output.length.toLong() } > limits.maxSectionChars
            RawDiagnosticSection(
                source = source,
                status = if (truncated) DiagnosticSourceStatus.TRUNCATED else DiagnosticSourceStatus.AVAILABLE,
                output = text,
                truncated = truncated,
                detail = if (truncated) "导入段落超过大小限制" else null
            )
        }
        return BugReportReadResult.Success(merged)
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
        return basename.startsWith("bugreport") || basename.endsWith(".txt")
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

    private fun readBounded(
        input: InputStream,
        entryLimit: Long,
        totalRemaining: Long,
        isCancelled: () -> Boolean
    ): BoundedRead {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var size = 0L
        while (true) {
            if (isCancelled()) return BoundedRead.Cancelled
            val read = input.read(buffer)
            if (read < 0) break
            size += read
            if (size > entryLimit) return BoundedRead.EntryTooLarge
            if (size > totalRemaining) return BoundedRead.TotalTooLarge
            output.write(buffer, 0, read)
        }
        return BoundedRead.Bytes(output.toByteArray())
    }

    private fun BugReportReadLimits.areValid(): Boolean =
        maxEntries > 0 && maxEntryBytes > 0 && maxTotalBytes > 0 && maxSectionChars > 0

    private sealed interface BoundedRead {
        data class Bytes(val value: ByteArray) : BoundedRead
        data object Cancelled : BoundedRead
        data object EntryTooLarge : BoundedRead
        data object TotalTooLarge : BoundedRead
    }
}
