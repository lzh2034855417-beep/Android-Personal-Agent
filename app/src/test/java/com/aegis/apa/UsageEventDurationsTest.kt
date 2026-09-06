package com.aegis.apa

import com.aegis.apa.tool.UsageEventDurations
import com.aegis.apa.tool.UsageEventRecord
import com.aegis.apa.tool.UsageEventKind.*
import org.junit.Assert.*
import org.junit.Test

class UsageEventDurationsTest {
    @Test fun clipsCrossMidnightSessionAndIgnoresYesterdayAndFuture() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("old", "A", 10, RESUME),
            UsageEventRecord("old", "A", 20, PAUSE),
            UsageEventRecord("app", "A", 90, RESUME),
            UsageEventRecord("app", "A", 120, PAUSE),
            UsageEventRecord("future", "A", 210, RESUME)
        ), 100, 200)
        assertEquals(mapOf("app" to 20L), result.durations)
    }
    @Test fun countsOngoingSessionOnlyUntilSampleTime() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 130, RESUME)
        ), 100, 200)
        assertEquals(70L, result.durations["app"])
    }
    @Test fun overlappingActivitiesInSamePackageAreNotCountedTwice() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 110, RESUME),
            UsageEventRecord("app", "B", 120, RESUME),
            UsageEventRecord("app", "A", 130, PAUSE),
            UsageEventRecord("app", "B", 140, PAUSE)
        ), 100, 200)
        assertEquals(30L, result.durations["app"])
    }
    @Test fun screenOffClosesActiveSessions() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 110, RESUME),
            UsageEventRecord("", "", 150, CLOSE_ALL)
        ), 100, 200)
        assertEquals(40L, result.durations["app"])
    }
    @Test fun startupDoesNotCountUnobservedDowntime() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 110, RESUME),
            UsageEventRecord("", "", 150, RESET)
        ), 100, 200)
        assertTrue(result.durations.isEmpty())
        assertTrue(result.isPartial)
    }
    @Test fun orphanPauseDoesNotInventUsageSinceMidnight() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 150, PAUSE)
        ), 100, 200)
        assertTrue(result.durations.isEmpty())
        assertTrue(result.isPartial)
    }
    @Test fun duplicateResumeDoesNotResetSessionStart() {
        val result = UsageEventDurations.calculate(listOf(
            UsageEventRecord("app", "A", 110, RESUME),
            UsageEventRecord("app", "A", 130, RESUME),
            UsageEventRecord("app", "A", 140, PAUSE)
        ), 100, 200)
        assertEquals(30L, result.durations["app"])
    }
}
