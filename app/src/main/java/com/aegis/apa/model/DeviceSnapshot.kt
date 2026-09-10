package com.aegis.apa.model

data class DeviceSnapshot(
    val deviceInfo: DeviceInfo,
    val batteryInfo: BatteryInfo,
    val displayInfo: DisplayInfo,
    val ramInfo: RamInfo,
    val storageInfo: StorageInfo,
    val usageSummary: UsageSummary,
    val installedApps: List<InstalledApp>,
    val detectedApps: List<DetectedApp>,
    val rootStatus: RootStatus,
    val sampledAtInstant: java.time.Instant
) {
    val sampledAt: String get() = SampleTime.format(sampledAtInstant)
}
