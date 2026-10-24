package com.aegis.apa

import com.aegis.apa.model.*
import com.aegis.apa.tool.DeviceNameResolver
import java.time.Instant

object GuidedDiagnosticFixtures {
    val now: Instant = Instant.parse("2026-10-05T00:00:00Z")
    fun diagnostic() = VerificationFixtures.snapshot().copy(
        verificationMetadata = VerificationFixtures.metadata().copy(reportedAt = null, device = null))
    fun device(temperature: Double? = 0.0) = DeviceSnapshot(
        DeviceInfo(DeviceNameResolver.resolve(DeviceIdentifiers("LOCAL_DEVICE", "LOCAL_MODEL")), "16", 36),
        BatteryInfo(70, "正在放电", temperatureCelsius = temperature, plugged = "未外接电源"),
        DisplayInfo(null, null, null, null, null), RamInfo(0, 0, false), StorageInfo(0, 0),
        UsageSummary(false, 0, emptyList()), emptyList(), emptyList(), RootStatus(false, false, null, false), now)
    fun observation(validity: BatteryObservationValidity = BatteryObservationValidity.VALID, rate: Double? = 2.0) =
        BatteryObservationResult(BatteryObservationPoint(now.minusSeconds(3600), 80, false),
            BatteryObservationPoint(now, 78, false), validity, 3_600_000, 2, rate)
}
