package com.aegis.apa

import com.aegis.apa.tool.AndroidUidParser
import com.aegis.apa.tool.DiagnosticDurationParser
import com.aegis.apa.tool.PartialAppEvidence
import com.aegis.apa.tool.mergePartialEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerEvidenceParsingTest {
    @Test
    fun rejectsUidThatCannotFitAndroidIntegerRange() {
        assertNull(AndroidUidParser.parse("u999999999999999999999a999999999999999999999"))
    }

    @Test
    fun acceptsOnlyAndroidRegularApplicationSuffixRange() {
        assertEquals(19_999, AndroidUidParser.parse("u0a9999"))
        assertNull(AndroidUidParser.parse("u0a10000"))
    }

    @Test
    fun rejectsDurationThatWouldOverflowLong() {
        assertNull(DiagnosticDurationParser.parseMillis("999999999999999999999h"))
        assertNull(DiagnosticDurationParser.parseMillis("9223372036854775807h"))
    }

    @Test
    fun discardsCounterWhenMergingWouldOverflow() {
        val overflowed = mergePartialEvidence(
            PartialAppEvidence(uid = 10123, alarmCount = Long.MAX_VALUE),
            PartialAppEvidence(uid = 10123, alarmCount = 1L)
        )
        val mergedAgain = mergePartialEvidence(
            overflowed,
            PartialAppEvidence(uid = 10123, alarmCount = 100L)
        )

        assertNull(mergedAgain.alarmCount)
    }
}
