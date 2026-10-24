package com.aegis.apa

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.tool.BugReportImportPolicy
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in diagnostic for a user-selected URI. Never opens the report stream. */
class BugReportMetadataDiagnosticTest {
    @Test fun inspectSelectedReportMetadataOnly() {
        val rawUri = InstrumentationRegistry.getArguments().getString("report_uri")
        assumeTrue("Only run when a user-selected report URI is supplied", rawUri != null)
        val uri = Uri.parse(rawUri)
        require(BugReportImportPolicy.acceptsUriScheme(uri.scheme))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val mime = resolver.getType(uri)
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !it.isNull(nameIndex)) name = it.getString(nameIndex)
                if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
            }
        }
        instrumentation.addResults(Bundle().apply {
            putString("report_mime", mime ?: "(null)")
            putString("report_name", name ?: "(null)")
            putString("report_size_bytes", size?.toString() ?: "(null)")
            putString("report_metadata_accepted", BugReportImportPolicy.accepts(mime, name).toString())
        })
    }
}
