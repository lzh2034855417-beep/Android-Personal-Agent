package com.aegis.apa.model

import java.time.Duration
import java.time.Instant
import java.util.Locale

data class BatteryObservationPoint(
    val sampledAtInstant: Instant,
    val levelPercent: Int?,
    val charging: Boolean
) {
    companion object {
        fun from(snapshot: DeviceSnapshot): BatteryObservationPoint {
            val battery = snapshot.batteryInfo
            val externallyPowered = battery.plugged != null && battery.plugged != "未外接电源"
            return BatteryObservationPoint(
                sampledAtInstant = snapshot.sampledAtInstant,
                levelPercent = battery.level,
                charging = battery.status == "正在充电" || battery.status == "已充满" || externallyPowered
            )
        }
    }
}

enum class BatteryObservationValidity {
    VALID,
    MISSING_BATTERY_LEVEL,
    STARTED_WHILE_CHARGING,
    ENDED_WHILE_CHARGING,
    INVALID_TIME_RANGE,
    TOO_SHORT,
    BATTERY_INCREASED,
    NO_MEASURABLE_DROP
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
}

object BatteryObservationAnalyzer {
    private val minimumDuration = Duration.ofMinutes(30)

    fun startError(point: BatteryObservationPoint): String? = when {
        point.levelPercent == null -> "未读取到电量，无法开始观察。"
        point.charging -> "请先拔掉充电器，再开始续航观察。"
        else -> null
    }

    fun finish(start: BatteryObservationPoint, end: BatteryObservationPoint): BatteryObservationResult {
        val durationMillis = Duration.between(start.sampledAtInstant, end.sampledAtInstant).toMillis()
        val drop = if (start.levelPercent != null && end.levelPercent != null) {
            start.levelPercent - end.levelPercent
        } else null
        val validity = when {
            start.levelPercent == null || end.levelPercent == null -> BatteryObservationValidity.MISSING_BATTERY_LEVEL
            start.charging -> BatteryObservationValidity.STARTED_WHILE_CHARGING
            end.charging -> BatteryObservationValidity.ENDED_WHILE_CHARGING
            durationMillis <= 0 -> BatteryObservationValidity.INVALID_TIME_RANGE
            durationMillis < minimumDuration.toMillis() -> BatteryObservationValidity.TOO_SHORT
            drop != null && drop < 0 -> BatteryObservationValidity.BATTERY_INCREASED
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
        BatteryObservationValidity.STARTED_WHILE_CHARGING -> "开始时处于充电状态，本次观察无效。"
        BatteryObservationValidity.ENDED_WHILE_CHARGING -> "结束时连接了电源，本次观察无效。"
        BatteryObservationValidity.INVALID_TIME_RANGE -> "起止时间异常，本次观察无效。"
        BatteryObservationValidity.TOO_SHORT -> "观察不足 30 分钟，整数电量误差过大，请继续观察。"
        BatteryObservationValidity.BATTERY_INCREASED -> "结束电量高于开始电量，期间可能充过电，本次观察无效。"
        BatteryObservationValidity.NO_MEASURABLE_DROP -> "电量尚未下降至少 1%，暂时无法计算掉电速度。"
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
        appendLine("有效性：${BatteryObservationAnalyzer.explanation(result.validity)}")
        append("限制：该观察只能说明这段时间的平均掉电速度，不能据此归因到具体应用；应用归因仍需系统耗电诊断。")
    }

    private fun durationText(durationMillis: Long): String {
        val safeMinutes = (durationMillis.coerceAtLeast(0) / 60_000)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return if (hours > 0) "${hours} 小时 ${minutes} 分钟" else "${minutes} 分钟"
    }
}
