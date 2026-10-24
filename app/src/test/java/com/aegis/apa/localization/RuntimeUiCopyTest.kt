package com.aegis.apa.localization

import com.aegis.apa.tool.BugReportRejectReason
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeUiCopyTest {
    @Test
    fun englishOperationalFailuresStayEnglish() {
        val expected = mapOf(
            RuntimeNotice.SNAPSHOT_READ_FAILED to "Could not refresh device data. The last sample is still shown.",
            RuntimeNotice.BUG_REPORT_READ_FAILED to "Could not read this system report. Generate a new report and try again.",
            RuntimeNotice.ROOT_DIAGNOSTIC_FAILED to "Could not collect the system power diagnostic. Check Root authorization and try again.",
            RuntimeNotice.BATTERY_START_SAVE_FAILED to "Could not save the observation start on this device. Check storage and try again.",
            RuntimeNotice.BATTERY_START_READ_FAILED to "Could not read the current charge level. Try again.",
            RuntimeNotice.BATTERY_DISCARDED to "This measurement was discarded because the phone was charged or charging was uncertain.",
            RuntimeNotice.BATTERY_DISCARD_WRITE_AND_CLEAR_FAILED to "The measurement is invalid in this session, but saving the charging marker and clearing the start point both failed. Do not calculate it; check storage and cancel again.",
            RuntimeNotice.BATTERY_DISCARD_CLEAR_FAILED to "The measurement is invalid, but the saved start point could not be cleared. Cancel again.",
            RuntimeNotice.BATTERY_RESULT_CLEAR_FAILED to "The result was calculated, but the old start point could not be cleared. Tap Clear before starting another observation.",
            RuntimeNotice.BATTERY_END_READ_FAILED to "Could not read the ending charge level. Try again; the observation is still running.",
            RuntimeNotice.BATTERY_CLEAR_FAILED to "Could not clear the saved observation start. Try again.",
            RuntimeNotice.CHARGING_EVIDENCE_WRITE_FAILED to "Power was connected, so this observation is invalid. The charging marker could not be saved; finish or cancel and try again.",
            RuntimeNotice.SUPPLIER_FEEDBACK_COPIED to "Copied. Send it to the developer to extend supplier recognition."
        )

        expected.forEach { (notice, text) ->
            assertEquals(text, RuntimeUiCopy.text(notice, AppLanguage.EN))
        }
    }

    @Test
    fun everyBugReportRejectionHasASpecificEnglishRecoveryMessage() {
        val messages = BugReportRejectReason.entries.associateWith {
            RuntimeUiCopy.bugReportReject(it, AppLanguage.EN)
        }

        assertEquals(BugReportRejectReason.entries.size, messages.values.distinct().size)
        assertEquals("No recognizable system power section was found in the report.", messages[BugReportRejectReason.EMPTY])
        assertEquals("Only Android Bug Report ZIP or text files are supported.", messages[BugReportRejectReason.UNSUPPORTED_FORMAT])
        assertEquals("The archive is damaged or could not be read safely. Generate a new report.", messages[BugReportRejectReason.CORRUPT_ARCHIVE])
    }
}
