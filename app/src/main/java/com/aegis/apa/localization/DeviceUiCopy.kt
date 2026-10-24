package com.aegis.apa.localization

object DeviceUiCopy {
    fun rawLabel(value: String): String = value

    fun installedState(installed: Boolean, language: AppLanguage): String = when (language) {
        AppLanguage.EN -> if (installed) "Installed" else "Not detected"
        AppLanguage.ZH_CN -> if (installed) "已安装" else "未检测到"
    }

    fun batteryStatus(value: String, language: AppLanguage): String = translateKnown(
        value,
        language,
        mapOf(
            "正在充电" to "Charging",
            "已充满" to "Full",
            "正在放电" to "Discharging",
            "未充电" to "Not charging",
            "未获取到" to "Unavailable"
        )
    )

    fun batteryHealth(value: String, language: AppLanguage): String = translateKnown(
        value,
        language,
        mapOf(
            "良好" to "Good",
            "过热" to "Overheating",
            "电压异常" to "Over voltage",
            "无响应" to "Dead",
            "温度过低" to "Too cold",
            "状态异常" to "Failure"
        )
    )

    fun plugged(value: String, language: AppLanguage): String {
        if (language != AppLanguage.EN) return value
        val translations = mapOf(
            "未外接电源" to "Not plugged in",
            "交流电" to "AC",
            "无线充电" to "Wireless charging"
        )
        return value.split(" + ").joinToString(" + ") { part -> translations[part] ?: part }
    }

    fun duration(milliseconds: Long, language: AppLanguage): String {
        val totalMinutes = milliseconds.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (language == AppLanguage.EN) "$hours h $minutes min" else "$hours 小时 $minutes 分"
    }

    private fun translateKnown(value: String, language: AppLanguage, translations: Map<String, String>): String =
        if (language == AppLanguage.EN) translations[value] ?: value else value
}
