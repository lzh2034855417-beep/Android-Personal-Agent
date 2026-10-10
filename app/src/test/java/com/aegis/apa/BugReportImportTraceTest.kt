package com.aegis.apa

import com.aegis.apa.tool.BugReportImportTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportImportTraceTest {
    @Test fun rejectedImportShowsTypeAndStageWithoutReplacingOriginalError() {
        val trace = BugReportImportTrace(true)
        trace.record("stage=metadata mime=application/x-zip accepted=false")
        val shown = trace.appendTo("读取失败")
        assertTrue(shown.startsWith("读取失败"))
        assertTrue(shown.contains("stage=metadata mime=application/x-zip accepted=false"))
    }

    @Test fun disabledTraceNeverAddsDebugInformation() {
        val trace = BugReportImportTrace(false)
        trace.record("stage=metadata mime=application/zip")
        assertEquals("读取失败", trace.appendTo("读取失败"))
    }

    @Test fun emptyTraceDoesNotAddAnEmptyDiagnosticHeading() {
        assertEquals("读取失败", BugReportImportTrace(true).appendTo("读取失败"))
    }

    @Test fun traceBoundsEventsAndPreventsControlCharacterInjection() {
        val trace = BugReportImportTrace(true)
        trace.record("stage=metadata\nFORGED")
        trace.record("x".repeat(1000))
        trace.record("stage=stream")
        trace.record("stage=extract")
        trace.record("SHOULD_NOT_APPEAR")
        val shown = trace.appendTo("读取失败")
        assertFalse(shown.contains("metadata\nFORGED"))
        assertFalse(shown.contains("x".repeat(241)))
        assertFalse(shown.contains("SHOULD_NOT_APPEAR"))
        assertTrue(shown.contains("stage=extract"))
    }
}
