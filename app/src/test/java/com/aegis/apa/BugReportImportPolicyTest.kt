package com.aegis.apa

import com.aegis.apa.tool.BugReportImportPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportImportPolicyTest {
    @Test
    fun acceptsExplicitZipAndTextMimeTypes() {
        assertTrue(BugReportImportPolicy.accepts("application/zip", "bugreport-device.zip"))
        assertTrue(BugReportImportPolicy.accepts("application/x-zip-compressed", "bugreport-device.zip"))
        assertTrue(BugReportImportPolicy.accepts("text/plain", "bugreport-device.txt"))
    }

    @Test
    fun acceptsGenericMimeForZipAndTextFiles() {
        assertTrue(BugReportImportPolicy.accepts("application/octet-stream", "bugreport-device.zip"))
        assertTrue(BugReportImportPolicy.accepts(null, "BUGREPORT-vivo.txt"))
        assertTrue(BugReportImportPolicy.accepts("application/octet-stream", "my-phone-report.zip"))
        assertTrue(BugReportImportPolicy.accepts(null, "系统错误报告.txt"))
        assertFalse(BugReportImportPolicy.accepts("application/octet-stream", "photo.bin"))
        assertFalse(BugReportImportPolicy.accepts(null, null))
    }

    @Test
    fun rejectsUnrelatedMimeEvenWhenItsNameLooksLikeAReport() {
        assertFalse(BugReportImportPolicy.accepts("image/jpeg", "bugreport.jpg"))
        assertFalse(BugReportImportPolicy.accepts("application/pdf", "bugreport.pdf"))
    }

    @Test
    fun acceptsOnlyUserGrantedContentUris() {
        assertTrue(BugReportImportPolicy.acceptsUriScheme("content"))
        assertTrue(BugReportImportPolicy.acceptsUriScheme("CONTENT"))
        assertFalse(BugReportImportPolicy.acceptsUriScheme("file"))
        assertFalse(BugReportImportPolicy.acceptsUriScheme("https"))
        assertFalse(BugReportImportPolicy.acceptsUriScheme(null))
    }
}
