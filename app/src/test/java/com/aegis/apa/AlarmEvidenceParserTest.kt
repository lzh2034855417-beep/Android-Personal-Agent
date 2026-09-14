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

    @Test
    fun parsesOfficialAlarmStatsPackageBlocks() {
        val result = AlarmEvidenceParser.parse(
            """
                Alarm Stats:
                  *ACTIVE* u0a123:com.example.chat 1h 2m running, 180 wakeups:
                    5m 120 wakes 200 alarms, last -2m:
                      act=com.example.SYNC
                    1m 20 wakes 20 alarms, last -1m:
                      act=com.example.RETRY
                  u0a123:com.example.push 2m running, 5 wakeups:
                    7 alarms: act=com.example.PUSH
                  10456:com.example.reader 1m running, 20 wakeups:
                    30s 15 wakes 25 alarms, last -5m:
                      act=com.example.REFRESH
            """.trimIndent()
        )

        assertEquals(185L, result.apps.getValue(10123).wakeupCount)
        assertEquals(227L, result.apps.getValue(10123).alarmCount)
        assertEquals(20L, result.apps.getValue(10456).wakeupCount)
        assertEquals(25L, result.apps.getValue(10456).alarmCount)
        assertTrue(EvidenceField.WAKEUP_ALARMS in result.parsedFields)
    }

    @Test
    fun malformedHeaderCannotDonateChildCountsToPreviousUid() {
        val result = AlarmEvidenceParser.parse(
            """
                Alarm Stats:
                  u0a123:com.example.chat 1m running, 5 wakeups:
                    1m 5 wakes 7 alarms, last -1m:
                  u999999999999999999999a1:com.example.bad 1m running, 8 wakeups:
                    1m 8 wakes 900 alarms, last -1m:
            """.trimIndent()
        )

        assertEquals(5L, result.apps.getValue(10123).wakeupCount)
        assertEquals(7L, result.apps.getValue(10123).alarmCount)
    }
}
