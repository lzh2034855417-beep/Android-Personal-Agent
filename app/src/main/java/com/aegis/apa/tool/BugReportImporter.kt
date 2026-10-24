package com.aegis.apa.tool

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.aegis.apa.BuildConfig
import java.util.Locale

object BugReportImportPolicy {
    private val directMimeTypes = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "text/plain"
    )

    fun acceptsUriScheme(scheme: String?): Boolean =
        scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true

    fun accepts(mimeType: String?, displayName: String?): Boolean {
        val mime = mimeType?.lowercase(Locale.ROOT)
        if (mime in directMimeTypes) return true
        if (mime != null && mime != "application/octet-stream") return false

        val name = displayName?.lowercase(Locale.ROOT) ?: return false
        return name.endsWith(".zip") || name.endsWith(".txt")
    }
}

class BugReportImporter(
    private val resolver: ContentResolver,
    private val onDiagnostic: (String) -> Unit = {}
) {
    fun import(uri: Uri, isCancelled: () -> Boolean = { false }): BugReportReadResult =
        import(uri, {}, isCancelled)

    fun import(uri: Uri, onBytesRead: (Long) -> Unit, isCancelled: () -> Boolean): BugReportReadResult {
        if (!BugReportImportPolicy.acceptsUriScheme(uri.scheme)) {
            diagnostic("stage=scheme accepted=false")
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        diagnostic("stage=metadata_query")
        val metadataStarted = System.nanoTime()
        val displayName = queryDisplayName(uri)
        val mime = resolver.getType(uri)
        val accepted = BugReportImportPolicy.accepts(mime, displayName)
        val safeMime = mime?.take(120)?.replace(Regex("[^A-Za-z0-9/;=+._ -]"), "?")
        val zipName = displayName?.endsWith(".zip", ignoreCase = true)
        val txtName = displayName?.endsWith(".txt", ignoreCase = true)
        diagnostic("stage=metadata mime=$safeMime zipName=$zipName txtName=$txtName accepted=$accepted")
        if (BuildConfig.DEBUG) Log.d("APA-ReportImport", "stage=metadata elapsed_ms=${(System.nanoTime() - metadataStarted) / 1_000_000}")
        if (!accepted) {
            return BugReportReadResult.Rejected(BugReportRejectReason.UNSUPPORTED_FORMAT)
        }
        diagnostic("stage=open_stream")
        val readStarted = System.nanoTime()
        return resolver.openInputStream(uri)?.use { input ->
            BugReportSectionExtractor.extract(
                input = input,
                displayName = displayName,
                onBytesRead = onBytesRead,
                isCancelled = isCancelled
            ).also { result ->
                diagnostic("stage=extract result=${when (result) {
                    is BugReportReadResult.Rejected -> result.reason.name + " detail=" + result.diagnosticDetail
                    is BugReportReadResult.Success -> "SUCCESS"
                    BugReportReadResult.Cancelled -> "CANCELLED"
                }} elapsed_ms=${(System.nanoTime() - readStarted) / 1_000_000}")
            }
        } ?: BugReportReadResult.Rejected(BugReportRejectReason.EMPTY)
    }

    private fun diagnostic(message: String) {
        if (BuildConfig.DEBUG) {
            onDiagnostic(message)
            Log.d("APA-ReportImport", message)
        }
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
