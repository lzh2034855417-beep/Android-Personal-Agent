package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage

/** Model instructions, not a guarantee or a replacement for the deterministic local verdict. */
internal object AgentResponseFocusPolicy {
    fun system(language: AppLanguage): String = if (language == AppLanguage.EN) """
        Focused answer contract:
        Start with a direct answer to this question and a priority target: a named app/package or a measured device indicator. If the evidence cannot identify one, say so directly; do not force a culprit or use stronger wording to hide uncertainty.
        Support the answer with at most 2-3 relevant facts. For each available measurement preserve its value, unit, source, and measurement window. If a source/window is missing, state that single limitation rather than inventing it. Distinguish measured facts from your interpretation; do not convert correlation into causation or high consumption into a background anomaly.
        Never invent confidence percentages, severity thresholds, comparison baselines, app behavior, or setting paths. Preserve the local verdict's confidence, evidence limits and maximum advice level. A user's allegation is a hypothesis, not a measurement.
        Give one priority action for the named target: what to inspect/change, likely side effect, how to verify with a comparable usage/charging window, and rollback if a setting is changed. Give an exact settings path only when supplied or reliably known; otherwise describe the setting to look for and acknowledge OEM differences. For an observation or official test, do not invent a change or rollback step.
        If evidence is insufficient, give only the decisive missing evidence and one next step that obtains it. Do not list generic cleaning, rebooting, power-saving, or multiple unrelated fixes without supporting evidence. Be direct but never exaggerate certainty. Use the existing task's plain-text fields, omit empty sections, and avoid repeated disclaimers.
    """.trimIndent() else """
        定向回答规则：
        开头直接回答当前问题，并指出优先对象：报告支持的应用名/包名或具体设备指标。无法定位就直接说无法定位，不强行选一个“元凶”，不靠加重语气掩盖不确定性。
        依据只保留最相关的 2 至 3 项；可用测量须保留原始数值、单位、来源和统计窗口。来源或窗口缺失就简短注明，不自行补全。区分已测事实与解释，不把相关性说成因果，不把耗电多说成后台异常。
        不得编造置信度百分比、严重程度阈值、比较基线、应用行为或设置路径。保留本地裁决的置信度、证据限制和最高建议级别；用户指控只是假设，不是实测事实。
        只给一个优先动作，明确针对谁、检查或调整什么、可能影响什么、怎样在可比的使用/充电窗口下验证；若改设置，说明如何回退。只有报告提供或可靠已知时才给精确设置路径，否则描述需要寻找的设置并说明厂商差异。观察或官方检测不应编造修改与回退步骤。
        证据不足时只指出决定性缺口和一个取得该证据的下一步。没有对应依据，不堆清理、重启、省电或多项无关建议。措辞直接但不夸大确定性，沿用当前任务的纯文本字段，省略空栏目，不重复免责。
    """.trimIndent()

    fun task(intent: PowerQuestionIntent, diagnosticAttached: Boolean, language: AppLanguage): String {
        val english = language == AppLanguage.EN
        return buildString {
            appendLine(if (english) "[Focused answer task]" else "【定向回答任务】")
            when (intent) {
                PowerQuestionIntent.BATTERY_HEALTH -> {
                    appendLine(if (english) {
                        "Answer whether the available evidence supports replacement, not which app drains power. Do not turn a coarse good-health status into a health percentage. Quote capacity/cycles only when actually provided with trustworthy units; do not invent a health ratio. If capacity evidence is missing, one official battery test is the next step. If the user reports swelling, advise stopping charging/use and professional inspection, without claiming swelling was measured."
                    } else {
                        "优先回答是否有足够证据建议更换，不转去分析耗电应用。健康良好不等于健康度百分比；仅引用实际提供且单位可信的容量/循环数据，不编造健康度。缺少容量证据时，下一步只建议官方电池检测。用户描述鼓包时给出停止充电/使用并专业送检的安全建议，但不能说已检测到鼓包。"
                    })
                }
                PowerQuestionIntent.ATTRIBUTION -> {
                    appendLine(if (english) {
                        "Choose the priority target only from candidates already in the local verdict; do not add suspects from installed apps, user accusations, or scheduling counts. Distinguish total consumption, background consumption, and same-window background anomalies. If evidence cannot establish a unique priority, say that explicitly instead of inventing a tie-breaker. Name at most two alternatives only if needed to explain this limitation. A consumption ranking alone permits checking/observing, not upgrading to restriction or freezing."
                    } else {
                        "只从本地裁决已有的候选中选择优先对象；不得从已安装应用、用户指控或调度计数中新增嫌疑。区分耗电总量、后台耗电量和同窗口后台异常。证据不能唯一排序就明确说明，不编造打破平局的理由；必要时最多列两个并列对象解释限制。仅有耗电排名只能核对/观察，不得升级为限制或冻结。"
                    })
                }
                PowerQuestionIntent.DRAIN_RATE -> {
                    appendLine(if (english) {
                        "Prioritize the supplied drain rate, its unit and measurement window, not an app culprit. Use only report-provided, locally validated rates/projections; never infer a rate from one charge reading or foreground time. Do not project full runtime from a window under 2 hours. Do not label a rate abnormal without a comparable baseline. If there is no validated rate, give one APA unplugged observation step; do not invent a numeric result."
                    } else {
                        "优先回答已提供的耗电速率、单位和测量窗口，不转去点名元凶。只引用报告中本地验证的速率/续航估算，不从单点电量或前台时长推算速度；不足 2 小时不外推完整续航，没有可比基线不宣称速度异常。缺少有效速率时只建议一次 APA 未充电续航观察，不编造数值。"
                    })
                }
                PowerQuestionIntent.OTHER -> appendLine(if (english) {
                    "Address only the asked device issue. Prioritize the relevant measured indicator or say which decisive measurement is missing; do not list unrelated apps or missing reports. Do not infer overheating from one temperature without its sensor, charging/workload context and a reliable comparison."
                } else {
                    "只回答当前设备问题，优先指出相关实测指标或决定性的缺失测量，不罗列无关应用/报告。单次温度缺少传感器、充电/负载背景及可靠比较时，不断言过热。"
                })
            }
            if (diagnosticAttached) appendLine(if (english) {
                "The system diagnostic is already attached. Do not ask to upload/import it again; if a new sample is indispensable, explain the specific missing window or source and why the existing sample cannot answer. For health questions its contents are intentionally omitted because power attribution is not battery health evidence."
            } else {
                "系统诊断已经附加，不要求再次上传/导入当前报告；若确实需要新样本，须说清缺少哪个窗口或来源，以及现有样本为何不能回答。健康问题有意省略耗电诊断内容，因为耗电归因不是健康证据。"
            })
            if (english) appendLine(if (intent == PowerQuestionIntent.BATTERY_HEALTH) {
                "Use plain-text fields: Conclusion, Known health evidence, Decisive limitation, Next step. Add Safety only when relevant. Omit empty fields; aim for at most 180 words, without sacrificing critical safety facts."
            } else {
                "Use plain-text fields: Conclusion, Evidence, Priority action, Verification/rollback, Critical limitation. Omit empty fields; aim for at most 220 words. Do not repeat the same limitation in each field."
            })
        }.trimEnd()
    }
}
