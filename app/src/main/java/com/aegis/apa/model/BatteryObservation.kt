package com.aegis.apa.model

import java.time.Duration
import java.time.Instant
import java.util.Locale

data class BatteryObservationPoint(
    val sampledAtInstant: Instant,
    val levelPercent: Int?,
    val charging: Boolean,
    val powerStateKnown: Boolean = true,
    val elapsedRealtimeMillis: Long? = null
) {
    companion object {
        fun from(snapshot: DeviceSnapshot, elapsedRealtimeMillis: Long? = null): BatteryObservationPoint {
            return from(snapshot.batteryInfo, snapshot.sampledAtInstant, elapsedRealtimeMillis)
        }

        fun from(
            battery: BatteryInfo,
            sampledAtInstant: Instant,
            elapsedRealtimeMillis: Long? = null
        ): BatteryObservationPoint {
            val explicitlyCharging = battery.status == "正在充电" || battery.status == "已充满"
            val externallyPowered = battery.plugged != null && battery.plugged != "未外接电源"
            val explicitlyUnplugged = battery.plugged == "未外接电源"
            return BatteryObservationPoint(
                sampledAtInstant = sampledAtInstant,
                levelPercent = battery.level,
                charging = explicitlyCharging || externallyPowered,
                powerStateKnown = explicitlyCharging || externallyPowered || explicitlyUnplugged,
                elapsedRealtimeMillis = elapsedRealtimeMillis
            )
        }
    }
}

enum class BatteryObservationValidity {
    VALID,
    MISSING_BATTERY_LEVEL,
    UNKNOWN_POWER_STATE,
    STARTED_WHILE_CHARGING,
    CHARGING_DURING_OBSERVATION,
    ENDED_WHILE_CHARGING,
    CONTINUITY_LOST,
    INVALID_TIME_RANGE,
    TOO_SHORT,
    BATTERY_INCREASED,
    NO_MEASURABLE_DROP
}

enum class BatteryObservationQuality {
    ROUGH,
    MODERATE,
    STABLE
}

data class BatteryObservationResult(
    val start: BatteryObservationPoint,
    val end: BatteryObservationPoint,
    val validity: BatteryObservationValidity,
    val durationMillis: Long,
    val dropPercent: Int?,
    val drainPercentPerHour: Double?
) {
    val isUsableEvidence: Boolean get() = validity == BatteryObservationValidity.VALID
    val measurementQuality: BatteryObservationQuality?
        get() = if (!isUsableEvidence || dropPercent == null) null else when {
            durationMillis >= Duration.ofHours(4).toMillis() && dropPercent >= 10 -> BatteryObservationQuality.STABLE
            durationMillis >= Duration.ofHours(2).toMillis() && dropPercent >= 5 -> BatteryObservationQuality.MODERATE
            else -> BatteryObservationQuality.ROUGH
        }
}

object BatteryObservationAnalyzer {
    private val minimumDuration = Duration.ofMinutes(30)

    fun startError(point: BatteryObservationPoint): String? = when {
        point.levelPercent == null -> "未读取到电量，无法开始观察。"
        !point.powerStateKnown -> "系统没有明确返回是否连接电源，无法安全开始观察。"
        point.charging -> "请先拔掉充电器，再开始续航观察。"
        else -> null
    }

    fun finish(
        start: BatteryObservationPoint,
        end: BatteryObservationPoint,
        chargingObserved: Boolean = false,
        continuityLost: Boolean = false
    ): BatteryObservationResult {
        val durationMillis = if (start.elapsedRealtimeMillis != null && end.elapsedRealtimeMillis != null) {
            end.elapsedRealtimeMillis - start.elapsedRealtimeMillis
        } else {
            Duration.between(start.sampledAtInstant, end.sampledAtInstant).toMillis()
        }
        val drop = if (start.levelPercent != null && end.levelPercent != null) {
            start.levelPercent - end.levelPercent
        } else null
        val validity = when {
            start.levelPercent == null || end.levelPercent == null -> BatteryObservationValidity.MISSING_BATTERY_LEVEL
            !start.powerStateKnown || !end.powerStateKnown -> BatteryObservationValidity.UNKNOWN_POWER_STATE
            start.charging -> BatteryObservationValidity.STARTED_WHILE_CHARGING
            chargingObserved -> BatteryObservationValidity.CHARGING_DURING_OBSERVATION
            end.charging -> BatteryObservationValidity.ENDED_WHILE_CHARGING
            continuityLost -> BatteryObservationValidity.CONTINUITY_LOST
            durationMillis <= 0 -> BatteryObservationValidity.INVALID_TIME_RANGE
            drop != null && drop < 0 -> BatteryObservationValidity.BATTERY_INCREASED
            durationMillis < minimumDuration.toMillis() -> BatteryObservationValidity.TOO_SHORT
            drop == 0 -> BatteryObservationValidity.NO_MEASURABLE_DROP
            else -> BatteryObservationValidity.VALID
        }
        val rate = if (validity == BatteryObservationValidity.VALID && drop != null) {
            drop * Duration.ofHours(1).toMillis().toDouble() / durationMillis
        } else null
        return BatteryObservationResult(start, end, validity, durationMillis, drop, rate)
    }

    fun explanation(validity: BatteryObservationValidity): String = when (validity) {
        BatteryObservationValidity.VALID -> "观察有效，可用于判断这段时间的平均掉电速度。"
        BatteryObservationValidity.MISSING_BATTERY_LEVEL -> "系统没有返回起点或终点电量，本次观察无效。"
        BatteryObservationValidity.UNKNOWN_POWER_STATE -> "系统没有明确返回是否连接电源，本次观察无效。"
        BatteryObservationValidity.STARTED_WHILE_CHARGING -> "开始时处于充电状态，本次观察无效。"
        BatteryObservationValidity.CHARGING_DURING_OBSERVATION -> "观察期间检测到连接电源，本次观察无效，请拔电后重新开始。"
        BatteryObservationValidity.ENDED_WHILE_CHARGING -> "结束时连接了电源，本次观察无效。"
        BatteryObservationValidity.CONTINUITY_LOST -> "观察期间应用进程被系统重建，无法确认全程未充电，请重新开始。"
        BatteryObservationValidity.INVALID_TIME_RANGE -> "起止时间异常，本次观察无效。"
        BatteryObservationValidity.TOO_SHORT -> "观察不足 30 分钟，整数电量误差过大，请继续观察。"
        BatteryObservationValidity.BATTERY_INCREASED -> "结束电量高于开始电量，期间可能充过电，本次观察无效。"
        BatteryObservationValidity.NO_MEASURABLE_DROP -> "电量尚未下降至少 1%，暂时无法计算掉电速度。"
    }

    fun qualityLabel(quality: BatteryObservationQuality): String = when (quality) {
        BatteryObservationQuality.ROUGH -> "粗略"
        BatteryObservationQuality.MODERATE -> "较稳定"
        BatteryObservationQuality.STABLE -> "稳定"
    }
}

object BatteryObservationReportBuilder {
    fun build(result: BatteryObservationResult): String = buildString {
        appendLine("【APA 续航观察】")
        appendLine("起点：${SampleTime.format(result.start.sampledAtInstant)}，${result.start.levelPercent?.let { "$it%" } ?: "电量未知"}")
        appendLine("终点：${SampleTime.format(result.end.sampledAtInstant)}，${result.end.levelPercent?.let { "$it%" } ?: "电量未知"}")
        appendLine("观察时长：${durationText(result.durationMillis)}")
        result.dropPercent?.let { appendLine("电量变化：下降 $it 个百分点") }
        result.drainPercentPerHour?.let {
            appendLine("平均掉电速度：${String.format(Locale.US, "%.2f", it)}%/小时")
        }
        result.measurementQuality?.let {
            appendLine("测量可靠性：${BatteryObservationAnalyzer.qualityLabel(it)}（观察越长、掉电跨度越大，整数电量误差越小）")
        }
        appendLine("有效性：${BatteryObservationAnalyzer.explanation(result.validity)}")
        appendLine("限制：该观察只能说明这段时间的平均掉电速度，不能据此归因到具体应用；应用归因仍需系统耗电诊断。")
        append("下一步：若这段时间主要是熄屏待机且你仍觉得掉电异常，请导入系统 Bug Report 定位后台来源；若是亮屏高负载，请在相同使用条件下再测一轮作对照。")
    }

    private fun durationText(durationMillis: Long): String {
        val safeMinutes = (durationMillis.coerceAtLeast(0) / 60_000)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return if (hours > 0) "${hours} 小时 ${minutes} 分钟" else "${minutes} 分钟"
    }
}
