package com.aegis.apa

import androidx.lifecycle.SavedStateHandle
import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import com.aegis.apa.model.BatteryObservationPoint
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MainSessionViewModelTest {
    @Test fun activeBatteryObservationDoesNotRestoreWithoutDiskCheckpoint() {
        val handle = SavedStateHandle()
        val firstSession = MainSessionViewModel(handle)
        val start = BatteryObservationPoint(
            Instant.parse("2026-09-14T00:00:00Z"),
            80,
            charging = false,
            elapsedRealtimeMillis = 50_000
        )
        firstSession.startBatteryObservation(start)

        val restoredSession = MainSessionViewModel(handle)

        restoredSession.reconcileBatteryObservation(null)

        assertNull(restoredSession.batteryObservationStart.value)
    }

    @Test fun restoredActiveObservationUsesUserConfirmedRoughResult() {
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        val restoredSession = MainSessionViewModel()
        restoredSession.reconcileBatteryObservation(
            BatteryObservationPoint(startedAt, 80, charging = false),
            chargingWasObserved = false
        )

        restoredSession.finishBatteryObservation(
            BatteryObservationPoint(startedAt.plusSeconds(3600), 77, charging = false),
            userReportedCharging = false
        )

        assertEquals(
            com.aegis.apa.model.BatteryObservationValidity.VALID,
            restoredSession.batteryObservationResult.value?.validity
        )
        assertEquals(
            com.aegis.apa.model.BatteryObservationQuality.ROUGH,
            restoredSession.batteryObservationResult.value?.measurementQuality
        )
        assertTrue(restoredSession.batteryObservationResult.value?.isUsableEvidence == true)
    }

    @Test fun completedBatteryObservationRestoresFromSavedState() {
        val handle = SavedStateHandle()
        val firstSession = MainSessionViewModel(handle)
        val start = Instant.parse("2026-09-14T00:00:00Z")
        firstSession.startBatteryObservation(
            BatteryObservationPoint(start, 80, charging = false, elapsedRealtimeMillis = 1_000)
        )
        firstSession.finishBatteryObservation(
            BatteryObservationPoint(
                start.plusSeconds(2 * 60 * 60),
                74,
                charging = false,
                elapsedRealtimeMillis = 3_601_000
            ),
            userReportedCharging = false
        )

        val restored = MainSessionViewModel(handle).batteryObservationResult.value

        assertTrue(restored?.isUsableEvidence == true)
        assertEquals(3.0, restored?.drainPercentPerHour!!, 0.001)
    }

    @Test fun batteryObservationRejectsChargingStartAndKeepsNoActiveSession() {
        val session = MainSessionViewModel()

        session.startBatteryObservation(
            BatteryObservationPoint(Instant.parse("2026-09-14T00:00:00Z"), 80, charging = true)
        )

        assertNull(session.batteryObservationStart.value)
        assertTrue(session.batteryObservationNotice.value.orEmpty().contains("拔掉充电器"))
    }

    @Test fun batteryObservationFinishesIntoUsableLocalEvidence() {
        val session = MainSessionViewModel()
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        session.startBatteryObservation(BatteryObservationPoint(startedAt, 80, charging = false))

        session.finishBatteryObservation(
            BatteryObservationPoint(startedAt.plusSeconds(2 * 60 * 60), 74, charging = false),
            userReportedCharging = false
        )

        assertNull(session.batteryObservationStart.value)
        assertTrue(session.batteryObservationResult.value?.isUsableEvidence == true)
        assertEquals(3.0, session.batteryObservationResult.value?.drainPercentPerHour!!, 0.001)
    }

    @Test fun endingTooEarlyKeepsTheOriginalObservationRunning() {
        val session = MainSessionViewModel()
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        val start = BatteryObservationPoint(startedAt, 80, charging = false)
        session.startBatteryObservation(start)

        session.finishBatteryObservation(
            BatteryObservationPoint(startedAt.plusSeconds(10 * 60), 79, charging = false),
            userReportedCharging = false
        )

        assertEquals(start, session.batteryObservationStart.value)
        assertNull(session.batteryObservationResult.value)
        assertTrue(session.batteryObservationNotice.value.orEmpty().contains("不足 30 分钟"))
    }

    @Test fun duplicateStartDoesNotMoveTheOriginalBaseline() {
        val session = MainSessionViewModel()
        val first = BatteryObservationPoint(Instant.parse("2026-09-14T00:00:00Z"), 80, charging = false)
        val second = BatteryObservationPoint(Instant.parse("2026-09-14T00:01:00Z"), 79, charging = false)
        session.startBatteryObservation(first)

        session.startBatteryObservation(second)

        assertEquals(first, session.batteryObservationStart.value)
        assertTrue(session.batteryObservationNotice.value.orEmpty().contains("已经在进行中"))
    }

    @Test fun chargingEventContaminatesAndEndsObservation() {
        val session = MainSessionViewModel()
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        session.startBatteryObservation(BatteryObservationPoint(startedAt, 80, charging = false))
        session.markBatteryObservationChargingObserved()

        session.finishBatteryObservation(
            BatteryObservationPoint(startedAt.plusSeconds(3600), 77, charging = false),
            userReportedCharging = false
        )

        assertNull(session.batteryObservationStart.value)
        assertEquals(
            com.aegis.apa.model.BatteryObservationValidity.CHARGING_DURING_OBSERVATION,
            session.batteryObservationResult.value?.validity
        )
    }

    @Test fun userReportedChargingInvalidatesObservationEvenWithoutReceiverEvent() {
        val session = MainSessionViewModel()
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        session.startBatteryObservation(BatteryObservationPoint(startedAt, 80, charging = false))

        session.finishBatteryObservation(
            BatteryObservationPoint(
                startedAt.plusSeconds(3600),
                levelPercent = null,
                charging = false,
                powerStateKnown = false
            ),
            userReportedCharging = true
        )

        assertEquals(
            com.aegis.apa.model.BatteryObservationValidity.CHARGING_DURING_OBSERVATION,
            session.batteryObservationResult.value?.validity
        )
        assertFalse(session.batteryObservationResult.value?.isUsableEvidence ?: true)
    }

    @Test fun diskCheckpointRestoreUsesWallClockAndRoughQuality() {
        val session = MainSessionViewModel()
        val startedAt = Instant.parse("2026-09-14T00:00:00Z")
        session.reconcileBatteryObservation(
            BatteryObservationPoint(
                sampledAtInstant = startedAt,
                levelPercent = 80,
                charging = false,
                elapsedRealtimeMillis = 5_000
            ),
            chargingWasObserved = false
        )

        session.finishBatteryObservation(
            BatteryObservationPoint(
                sampledAtInstant = startedAt.plusSeconds(2 * 60 * 60),
                levelPercent = 74,
                charging = false,
                elapsedRealtimeMillis = 60_000
            ),
            userReportedCharging = false
        )

        val result = session.batteryObservationResult.value
        assertEquals(2 * 60 * 60 * 1000L, result?.durationMillis)
        assertEquals(com.aegis.apa.model.BatteryObservationQuality.ROUGH, result?.measurementQuality)
    }

    @Test fun repeatedReconciliationCannotReplaceCurrentCheckpointButMergesChargingEvidence() {
        val session = MainSessionViewModel()
        val first = BatteryObservationPoint(Instant.parse("2026-09-14T00:00:00Z"), 80, charging = false)
        val different = BatteryObservationPoint(Instant.parse("2026-09-14T01:00:00Z"), 70, charging = false)
        session.reconcileBatteryObservation(first)

        session.reconcileBatteryObservation(different, chargingWasObserved = true)
        assertEquals(first, session.batteryObservationStart.value)

        session.reconcileBatteryObservation(first, chargingWasObserved = true)
        session.finishBatteryObservation(
            BatteryObservationPoint(first.sampledAtInstant.plusSeconds(3600), 77, charging = false),
            userReportedCharging = false
        )
        assertEquals(
            com.aegis.apa.model.BatteryObservationValidity.CHARGING_DURING_OBSERVATION,
            session.batteryObservationResult.value?.validity
        )
    }

    @Test fun onlineInterruptionExplainsThatRetryIsANewRequest() {
        val session = MainSessionViewModel()
        session.beginAnalysis(online = true)
        session.interruptAnalysis()
        val notice = session.messages.value.single()
        assertTrue(notice.content.contains("在线请求可能仍在处理"))
        assertTrue(notice.content.contains("重试会再次发送"))
        assertNull(notice.cloudProvider)
    }

    @Test fun interruptedAnalysisKeepsConversationAndAllowsRetry() {
        val session = MainSessionViewModel()
        session.messages.value = listOf(AgentConversationMessage(MessageRole.USER, "question"))
        session.beginAnalysis()
        session.interruptAnalysis()
        assertFalse(session.analyzing.value)
        assertEquals("question", session.messages.value.first().content)
        assertEquals(MessageRole.ERROR, session.messages.value.last().role)
        session.interruptAnalysis()
        assertEquals(2, session.messages.value.size)
        session.beginAnalysis()
        assertTrue(session.analyzing.value)
    }

    @Test fun oldRequestCannotChangeRetriedConversationOrBusyState() {
        val session = MainSessionViewModel()
        val old = session.beginAnalysis()
        session.interruptAnalysis()
        val fresh = session.beginAnalysis()
        val before = session.messages.value
        session.interruptAnalysis(old)
        session.appendAnalysisMessage(old, AgentConversationMessage(MessageRole.ASSISTANT, "stale"))
        session.finishAnalysis(old)
        assertEquals(before, session.messages.value)
        assertTrue(session.analyzing.value)
        session.appendAnalysisMessage(fresh, AgentConversationMessage(MessageRole.ASSISTANT, "fresh"))
        session.finishAnalysis(fresh)
        assertEquals("fresh", session.messages.value.last().content)
        assertFalse(session.analyzing.value)
    }
}
