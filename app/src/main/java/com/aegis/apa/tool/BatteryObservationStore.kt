package com.aegis.apa.tool

import android.annotation.SuppressLint
import android.content.Context
import com.aegis.apa.model.BatteryObservationPoint
import java.time.Instant

data class BatteryObservationCheckpoint(
    val start: BatteryObservationPoint,
    val chargingObserved: Boolean
)

enum class ChargingEvidenceWriteResult {
    UPDATED,
    NO_ACTIVE_CHECKPOINT,
    WRITE_FAILED
}

object BatteryObservationCheckpointCodec {
    fun encode(checkpoint: BatteryObservationCheckpoint): String = listOf(
        VERSION,
        checkpoint.start.sampledAtInstant.toEpochMilli().toString(),
        (checkpoint.start.levelPercent ?: -1).toString(),
        checkpoint.start.charging.toString(),
        checkpoint.start.powerStateKnown.toString(),
        checkpoint.chargingObserved.toString()
    ).joinToString("|")

    fun decode(encoded: String?): BatteryObservationCheckpoint? = runCatching {
        val parts = encoded?.split('|') ?: return null
        if (parts.size != 6 || parts[0] != VERSION) return null
        val storedLevel = parts[2].toInt()
        if (storedLevel !in 0..100 && storedLevel != -1) return null
        val level = storedLevel.takeIf { it >= 0 }
        val startCharging = parts[3].strictBoolean() ?: return null
        val powerStateKnown = parts[4].strictBoolean() ?: return null
        val chargingObserved = parts[5].strictBoolean() ?: return null
        BatteryObservationCheckpoint(
            start = BatteryObservationPoint(
                sampledAtInstant = Instant.ofEpochMilli(parts[1].toLong()),
                levelPercent = level,
                charging = startCharging,
                powerStateKnown = powerStateKnown,
                elapsedRealtimeMillis = null
            ),
            chargingObserved = chargingObserved
        )
    }.getOrNull()

    private fun String.strictBoolean(): Boolean? = when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private const val VERSION = "1"
}

class BatteryObservationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): BatteryObservationCheckpoint? =
        BatteryObservationCheckpointCodec.decode(preferences.getString(CHECKPOINT_KEY, null))

    fun saveStart(point: BatteryObservationPoint): Boolean =
        save(BatteryObservationCheckpoint(point.copy(elapsedRealtimeMillis = null), chargingObserved = false))

    fun markChargingObserved(): ChargingEvidenceWriteResult {
        val checkpoint = load() ?: return ChargingEvidenceWriteResult.NO_ACTIVE_CHECKPOINT
        return if (save(checkpoint.copy(chargingObserved = true))) {
            ChargingEvidenceWriteResult.UPDATED
        } else {
            ChargingEvidenceWriteResult.WRITE_FAILED
        }
    }

    @SuppressLint("ApplySharedPref") // Durability matters more than latency for this one tiny checkpoint.
    fun clear(): Boolean = preferences.edit().remove(CHECKPOINT_KEY).commit()

    @SuppressLint("ApplySharedPref") // A process kill immediately after tapping Start must not lose the baseline.
    private fun save(checkpoint: BatteryObservationCheckpoint): Boolean =
        preferences.edit()
            .putString(CHECKPOINT_KEY, BatteryObservationCheckpointCodec.encode(checkpoint))
            .commit()

    private companion object {
        const val FILE_NAME = "apa_battery_observation"
        const val CHECKPOINT_KEY = "active_checkpoint"
    }
}
