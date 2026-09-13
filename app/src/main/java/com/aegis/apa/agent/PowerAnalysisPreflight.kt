package com.aegis.apa.agent

enum class PowerQuestionIntent {
    OTHER,
    DRAIN_RATE,
    ATTRIBUTION
}

object PowerAnalysisPreflight {
    private val drainRateMarkers = listOf(
        "耗电快", "掉电快", "耗电速度", "掉电速度", "续航", "今天耗电",
        "一天耗电", "每小时耗电", "电量下降", "电量掉"
    )
    private val attributionMarkers = listOf(
        "为什么耗电", "耗电原因", "谁在耗电", "谁耗电", "后台耗电", "异常耗电",
        "哪个应用耗电", "哪些应用耗电", "耗电大户", "耗电排行"
    )

    fun classify(question: String): PowerQuestionIntent {
        val normalized = question.trim().lowercase()
        return when {
            drainRateMarkers.any(normalized::contains) -> PowerQuestionIntent.DRAIN_RATE
            attributionMarkers.any(normalized::contains) -> PowerQuestionIntent.ATTRIBUTION
            else -> PowerQuestionIntent.OTHER
        }
    }

    fun blockingMessage(
        question: String,
        diagnosticAvailable: Boolean,
        diagnosticSelected: Boolean
    ): String? {
        if (classify(question) == PowerQuestionIntent.OTHER) return null
        if (!diagnosticAvailable) {
            return "这次没有系统耗电诊断。请展开“选择报告”，点击“导入系统报告”，选择 Android 系统 Bug Report；导入成功后诊断摘要会自动附加。"
        }
        if (!diagnosticSelected) {
            return "系统报告已经导入，但本次没有附加诊断摘要。请点击“附加诊断摘要”后重新发送；原始 ZIP 不会发送。"
        }
        return null
    }
}
