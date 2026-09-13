package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.AlarmEvidenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEvidenceParserTest {
    @Test
    fun parsesNumericAndAndroidStyleUidsAcrossVendorWhitespace() {
        val result = AlarmEvidenceParser.parse(
            """
                UID u0a123: 180 wakeups, 220 alarms
                  10456 : 20 wakeups, 25 alarms
            """.trimIndent()
        )

        assertEquals(180L, result.apps.getValue(10123).wakeupCount)
        assertEquals(220L, result.apps.getValue(10123).alarmCount)
        assertEquals(20L, result.apps.getValue(10456).wakeupCount)
        assertTrue(EvidenceField.WAKEUP_ALARMS in result.parsedFields)
    }
}
