package com.aegis.apa.agent

enum class PowerQuestionIntent {
    OTHER,
    DRAIN_RATE,
    ATTRIBUTION
}

data class PowerQuestionSignals(
    val asksDrainRate: Boolean = false,
    val asksAttribution: Boolean = false
)

object PowerAnalysisPreflight {
    private val batteryContextMarkers = listOf(
        "电池", "电量", "耗电", "掉电", "费电", "续航", "待机"
    )
    private val explicitRateMarkers = listOf(
        "耗电快", "掉电快", "掉得快", "掉得很快", "耗电速度", "掉电速度", "续航",
        "今天耗电", "一天耗电", "每小时耗电", "电量下降", "电量掉", "电池掉",
        "掉电正常", "耗电正常", "费电正常"
    )
    private val attributionCues = listOf(
        "为什么耗电", "耗电原因", "谁在耗电", "谁耗电", "后台耗电", "异常耗电",
        "哪个应用耗电", "哪些应用耗电", "耗电大户", "耗电排行", "最费电",
        "费电应用", "耗电软件", "哪个软件", "哪个app", "哪个 app", "谁造成",
        "谁导致", "什么导致", "为什么", "什么原因"
    )

    fun detect(question: String): PowerQuestionSignals {
        val normalized = question.trim().lowercase()
        val hasBatteryContext = batteryContextMarkers.any(normalized::contains)
        val hasAttributionCue = attributionCues.any(normalized::contains)
        val quantitativeDrop = (normalized.contains("%") || normalized.contains("％")) &&
            normalized.contains("小时") &&
            (normalized.contains("掉") || normalized.contains("降"))
        val barePowerComplaint = listOf("费电", "耗电").any(normalized::contains) &&
            listOf("太", "很", "比较", "有点").any(normalized::contains)
        val asksAboutSpeed = normalized.contains("快") &&
            listOf("耗电", "掉电", "电量", "电池").any(normalized::contains)
        val asksAttribution = hasBatteryContext && hasAttributionCue
        val asksRate = quantitativeDrop || hasBatteryContext && (
            explicitRateMarkers.any(normalized::contains) || barePowerComplaint || asksAboutSpeed
            )
        return PowerQuestionSignals(
            asksDrainRate = asksRate && !(hasAttributionCue && normalized.contains("最费电")),
            asksAttribution = asksAttribution
        )
    }

    fun classify(question: String): PowerQuestionIntent {
        val signals = detect(question)
        return when {
            signals.asksAttribution -> PowerQuestionIntent.ATTRIBUTION
            signals.asksDrainRate -> PowerQuestionIntent.DRAIN_RATE
            else -> PowerQuestionIntent.OTHER
        }
    }

    fun blockingMessage(
        question: String,
        diagnosticAvailable: Boolean,
        diagnosticSelected: Boolean,
        observationAvailable: Boolean = false
    ): String? {
        val signals = detect(question)
        if (!signals.asksDrainRate && !signals.asksAttribution) return null
        if (signals.asksDrainRate && signals.asksAttribution) {
            return "这个问题同时包含两个问题：掉电速度需要先完成续航观察；具体应用归因需要附加系统耗电诊断。请拆成两问分别发送，避免漏答或混用证据。"
        }
        if (signals.asksDrainRate) {
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
