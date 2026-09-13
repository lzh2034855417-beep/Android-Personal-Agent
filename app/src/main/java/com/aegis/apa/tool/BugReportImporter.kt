package com.aegis.apa.tool

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

object BugReportImportPolicy {
    private val directMimeTypes = setOf("application/zip", "text/plain")

    fun acceptsUriScheme(scheme: String?): Boolean =
        scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true

    fun accepts(mimeType: String?, displayName: String?): Boolean {
        val mime = mimeType?.lowercase(Locale.ROOT)
        if (mime in directMimeTypes) return true
        if (mime != null && mime != "application/octet-stream") return false

        val name = displayName?.lowercase(Locale.ROOT) ?: return false
        return name.startsWith("bugreport") && (name.endsWith(".zip") || name.endsWith(".txt"))
    }
}

class BugReportImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri, isCancelled: () -> Boolean = { false }): BugReportReadResult {
        if (!BugReportImportPolicy.acceptsUriScheme(uri.scheme)) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        val displayName = queryDisplayName(uri)
        if (!BugReportImportPolicy.accepts(resolver.getType(uri), displayName)) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        return resolver.openInputStream(uri)?.use { input ->
            BugReportSectionExtractor.extract(
                input = input,
                displayName = displayName,
                isCancelled = isCancelled
            )
        } ?: BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
    }.getOrNull()
}
