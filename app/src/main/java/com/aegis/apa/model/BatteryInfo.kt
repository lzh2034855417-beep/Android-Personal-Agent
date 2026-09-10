package com.aegis.apa.model

data class BatteryInfo(
    val level: Int?,
    val status: String,
    val currentMilliAmp: Int? = null,
    val remainingMilliAmpHour: Int? = null,
    val remainingMilliWattHour: Long? = null,
    val temperatureCelsius: Double? = null,
    val voltageMilliVolt: Int? = null,
    val health: String? = null,
    val plugged: String? = null,
    val technology: String? = null,
    val isPresent: Boolean? = null
) {
    val levelText: String get() = level?.let { "$it%" } ?: "未获取到"
}

