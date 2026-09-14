package com.aegis.apa

import com.aegis.apa.model.BatteryObservationPoint
import com.aegis.apa.tool.BatteryObservationCheckpoint
import com.aegis.apa.tool.BatteryObservationCheckpointCodec
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryObservationCheckpointCodecTest {
    @Test fun roundTripDropsBootRelativeClockAndKeepsChargingEvidence() {
        val checkpoint = BatteryObservationCheckpoint(
            start = BatteryObservationPoint(
                sampledAtInstant = Instant.parse("2026-09-14T00:00:00Z"),
                levelPercent = 82,
                charging = false,
                powerStateKnown = true,
                elapsedRealtimeMillis = 99_000
            ),
            chargingObserved = true
        )

        val restored = BatteryObservationCheckpointCodec.decode(
            BatteryObservationCheckpointCodec.encode(checkpoint)
        )!!

        assertEquals(checkpoint.start.sampledAtInstant, restored.start.sampledAtInstant)
        assertEquals(82, restored.start.levelPercent)
        assertFalse(restored.start.charging)
        assertNull(restored.start.elapsedRealtimeMillis)
        assertEquals(true, restored.chargingObserved)
    }

    @Test fun corruptedOrUnknownCheckpointIsRejected() {
        assertNull(BatteryObservationCheckpointCodec.decode(null))
        assertNull(BatteryObservationCheckpointCodec.decode("2|broken"))
        assertNull(BatteryObservationCheckpointCodec.decode("1|0|82|maybe|true|false"))
        assertNull(BatteryObservationCheckpointCodec.decode("1|0|200|false|true|false"))
    }
}
