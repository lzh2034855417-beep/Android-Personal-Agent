package com.aegis.apa.agent

enum class PowerQuestionIntent {
    OTHER,
    DRAIN_RATE,
    ATTRIBUTION
}

object PowerAnalysisPreflight {
    private val drainRateMarkers = listOf(
        "耗电快", "掉电快", "耗电速度", "掉电速度", "续航", "今天耗电",
        "一天耗电", "每小时耗电", "电量下降", "电量掉", "掉了", "掉电正常",
        "耗电正常", "费电正常"
    )
    private val attributionMarkers = listOf(
        "为什么耗电", "耗电原因", "谁在耗电", "谁耗电", "后台耗电", "异常耗电",
        "哪个应用耗电", "哪些应用耗电", "耗电大户", "耗电排行", "最费电",
        "费电应用", "耗电软件", "哪个软件", "哪个app", "哪个 app", "谁造成",
        "谁导致", "什么导致"
    )

    fun classify(question: String): PowerQuestionIntent {
        val normalized = question.trim().lowercase()
        val asksWhy = normalized.contains("为什么") || normalized.contains("什么原因") || normalized.contains("怎么回事")
        return when {
            attributionMarkers.any(normalized::contains) -> PowerQuestionIntent.ATTRIBUTION
            asksWhy && (
                drainRateMarkers.any(normalized::contains) ||
                    listOf("耗电", "掉电", "费电").any(normalized::contains)
                ) -> PowerQuestionIntent.ATTRIBUTION
            drainRateMarkers.any(normalized::contains) -> PowerQuestionIntent.DRAIN_RATE
            else -> PowerQuestionIntent.OTHER
        }
    }

    fun blockingMessage(
        question: String,
        diagnosticAvailable: Boolean,
        diagnosticSelected: Boolean,
        observationAvailable: Boolean = false
    ): String? {
        val intent = classify(question)
        if (intent == PowerQuestionIntent.OTHER) return null
        if (intent == PowerQuestionIntent.DRAIN_RATE) {
            return if (observationAvailable) null else {
                "目前只有单点快照，不能判断掉电速度。请展开“选择报告”，先开始续航观察，拔掉充电器正常使用至少 30 分钟后结束观察；若还想定位具体应用，再导入 Android 系统 Bug Report。"
            }
        }
        if (!diagnosticAvailable) {
            return "这次没有系统耗电诊断。请展开“选择报告”，点击“导入系统报告”，选择 Android 系统 Bug Report；导入成功后诊断摘要会自动附加。"
        }
        if (!diagnosticSelected) {
            return "系统报告已经导入，但本次没有附加诊断摘要。请点击“附加诊断摘要”后重新发送；原始 ZIP 不会发送。"
        }
        return null
    }
}
