package com.aegis.apa.tool

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SceneField {
    TIMESTAMP,
    BATTERY_PERCENT,
    TEMPERATURE_CELSIUS,
    CURRENT_MILLI_AMP,
    POWER_MILLI_WATT,
    FOREGROUND_APP
}

data class SceneSample(
    val timestampMillis: Long? = null,
    val batteryPercent: Int? = null,
    val temperatureCelsius: Double? = null,
    val currentMilliAmp: Double? = null,
    val powerMilliWatt: Double? = null,
    val foregroundApp: String? = null
)

data class SceneImportResult(
    val samples: List<SceneSample>,
    val recognizedFields: Set<SceneField>,
    val error: String? = null
)

object SceneCsvParser {
    private val localDateTimeFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    )

    fun parse(text: String): SceneImportResult {
        val rows = text.lineSequence()
            .filter { it.isNotBlank() }
            .map(::splitRow)
            .toList()
        if (rows.isEmpty()) return SceneImportResult(emptyList(), emptySet(), "文件为空或没有可读取的 CSV 行。")
        if (rows.any { it == null }) return SceneImportResult(emptyList(), emptySet(), "CSV 引号未闭合，请重新导出后再试。")

        val header = requireNotNull(rows.first()).map { it.removePrefix("\uFEFF") }
        val fieldsByIndex = header.map(::fieldForHeader)
        val recognizedFields = fieldsByIndex.filterNotNull().toSet()
        if (recognizedFields.isEmpty()) {
            return SceneImportResult(emptyList(), emptySet(), "未识别到时间、电量、温度、电流、功耗或前台应用列。")
        }

        val samples = rows.drop(1).mapNotNull { row ->
            requireNotNull(row)
            val sample = SceneSample(
                timestampMillis = valueAt(row, fieldsByIndex, SceneField.TIMESTAMP)?.let(::parseTimestamp),
                batteryPercent = valueAt(row, fieldsByIndex, SceneField.BATTERY_PERCENT)?.let(::parsePercent),
                temperatureCelsius = valueAt(row, fieldsByIndex, SceneField.TEMPERATURE_CELSIUS)?.let(::parseNumber),
                currentMilliAmp = valueAt(row, fieldsByIndex, SceneField.CURRENT_MILLI_AMP)?.let(::parseNumber),
                powerMilliWatt = valueAt(row, fieldsByIndex, SceneField.POWER_MILLI_WATT)?.let(::parseNumber),
                foregroundApp = valueAt(row, fieldsByIndex, SceneField.FOREGROUND_APP)?.trim()?.ifBlank { null }
            )
            sample.takeIf {
                it.batteryPercent != null || it.temperatureCelsius != null || it.currentMilliAmp != null ||
                    it.powerMilliWatt != null || it.foregroundApp != null
            }
        }
        if (samples.isEmpty()) {
            return SceneImportResult(emptyList(), recognizedFields, "已识别列名，但没有可用的遥测样本。")
        }
        return SceneImportResult(samples, recognizedFields)
    }

    private fun valueAt(row: List<String>, fields: List<SceneField?>, wanted: SceneField): String? {
        val index = fields.indexOf(wanted)
        return row.getOrNull(index)
    }

    private fun fieldForHeader(header: String): SceneField? = when (normalize(header)) {
        "time", "timestamp", "datetime", "date", "时间", "记录时间", "采样时间" -> SceneField.TIMESTAMP
        "battery", "batterypercent", "batterypercentage", "电量", "电池电量", "剩余电量" -> SceneField.BATTERY_PERCENT
        "temperature", "temp", "温度", "电池温度" -> SceneField.TEMPERATURE_CELSIUS
        "current", "currentma", "电流", "电流ma" -> SceneField.CURRENT_MILLI_AMP
        "power", "powermw", "功耗", "功耗mw" -> SceneField.POWER_MILLI_WATT
        "app", "package", "foregroundapp", "应用", "前台应用", "包名" -> SceneField.FOREGROUND_APP
        else -> null
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[\\s_%()（）℃°\\[\\]]"), "")

    private fun parsePercent(value: String): Int? = parseNumber(value)?.toInt()?.takeIf { it in 0..100 }

    private fun parseNumber(value: String): Double? = value.trim()
        .replace(",", "")
        .replace(Regex("[^0-9+-.]"), "")
        .toDoubleOrNull()

    private fun parseTimestamp(value: String): Long? {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { numeric ->
            return if (numeric < 10_000_000_000L) numeric * 1_000 else numeric
        }
        runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        return localDateTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching {
                LocalDateTime.parse(trimmed, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    private fun splitRow(line: String): List<String>? {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> {
                    if (quoted && line.getOrNull(index + 1) == '"') {
                        current.append('"')
                        index++
                    } else quoted = !quoted
                }
                ',' -> if (quoted) current.append(character) else {
                    values += current.toString().trim()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        if (quoted) return null
        values += current.toString().trim()
        return values
    }
}

object SceneReportBuilder {
    fun build(import: SceneImportResult): String = buildString {
        appendLine("【Scene 一天续航导入摘要】")
        appendLine("可用样本：${import.samples.size} 条")
        appendLine("已识别字段：${import.recognizedFields.size} 项")

        val timedBatterySamples = import.samples
            .filter { it.timestampMillis != null && it.batteryPercent != null }
            .sortedBy { it.timestampMillis }
        val first = timedBatterySamples.firstOrNull()
        val last = timedBatterySamples.lastOrNull()
        val coverageHours = if (first != null && last != null) {
            (last.timestampMillis!! - first.timestampMillis!!) / 3_600_000.0
        } else null
        if (first != null && last != null && coverageHours != null && coverageHours > 0.0) {
            val delta = last.batteryPercent!! - first.batteryPercent!!
            appendLine("样本覆盖：${"%.1f".format(java.util.Locale.US, coverageHours)} 小时")
            appendLine(
                "电量变化：${first.batteryPercent}% → ${last.batteryPercent}%（" +
                    if (delta < 0) "下降 ${-delta}%）" else if (delta > 0) "上升 ${delta}%）" else "无变化）"
            )
            if (delta < 0) {
                appendLine("推算耗电：${"%.1f".format(java.util.Locale.US, -delta / coverageHours)}%/小时")
            }
        } else {
            appendLine("没有足够的带时间电量样本，无法推算全天耗电或续航。")
        }

        import.samples.mapNotNull { it.temperatureCelsius }.takeIf { it.isNotEmpty() }?.let { temperatures ->
            appendLine("平均温度：${oneDecimal(temperatures.average())}°C")
            appendLine("最高温度：${oneDecimal(temperatures.max())}°C")
        }
        import.samples.mapNotNull { it.powerMilliWatt }.takeIf { it.isNotEmpty() }?.let { powers ->
            appendLine("平均功耗：${oneDecimal(powers.average())} mW")
        }
        import.samples.mapNotNull { it.currentMilliAmp }.takeIf { it.isNotEmpty() }?.let { currents ->
            appendLine("平均电流：${oneDecimal(currents.average())} mA")
        }
        import.samples.mapNotNull { it.foregroundApp }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { appendLine("前台应用线索：$it") }
        appendLine("说明：以上仅基于导入样本，不代表亮屏时长、后台耗电、真实电池寿命或必须更换电池。")
    }.trim()

    private fun oneDecimal(value: Double): String = "%.1f".format(java.util.Locale.US, value)
}
