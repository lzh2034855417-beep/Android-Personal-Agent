package com.aegis.apa

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.aegis.apa.tool.BatteryObservationStore
import com.aegis.apa.tool.ChargingEvidenceWriteResult

class ApaApplication : Application() {
    private val batteryObservationStore by lazy { BatteryObservationStore(this) }
    @Volatile
    var chargingEvidenceWriteFailed: Boolean = false
        private set

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                persistChargingEvidence()
            }
        }
    }

    fun persistChargingEvidence(): ChargingEvidenceWriteResult {
        return batteryObservationStore.markChargingObserved().also { result ->
            when (result) {
                ChargingEvidenceWriteResult.UPDATED -> chargingEvidenceWriteFailed = false
                ChargingEvidenceWriteResult.WRITE_FAILED -> chargingEvidenceWriteFailed = true
                ChargingEvidenceWriteResult.NO_ACTIVE_CHECKPOINT -> Unit
            }
        }
    }

    fun clearChargingEvidenceWriteFailure() {
        chargingEvidenceWriteFailed = false
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
    }
}
