package com.aegis.apa.localization

import com.aegis.apa.agent.GuidedThermalStatus
import com.aegis.apa.model.*

object GuidedDiagnosticCopy {
    private val copy = buildMap<String, Pair<String, String>> {
        fun add(id: String, zh: String, en: String) { put(id, zh to en) }
        add("idle.answer", "确认待机问题的声明", "Review the idle-period declarations")
        add("idle.answer_conflict", "核对相互矛盾的输入", "Review conflicting inputs")
        add("idle.open_observation", "打开粗略续航观察", "Open rough battery observation")
        add("idle.import_report", "导入系统报告", "Import a system report")
        add("idle.observe_condition", "日常使用中自愿观察一个条件", "Voluntarily observe one condition during ordinary use")
        add("idle.verify_background", "打开耗电验证预览（不验证发热）", "Open power verification preview (does not verify heat)")
        add("idle.end", "结束：没有进一步可靠动作", "End: no further reliable action")
        add("guided.end", "结束排查", "End guidance")
        add("heat.answer", "确认发热问题的声明", "Review the heat declarations")
        add("heat.answer_conflict", "核对发热场景的输入冲突", "Review heat-scenario input conflicts")
        add("heat.collect_current", "主动刷新当前基础快照", "Refresh the current basic snapshot")
        add("heat.collect_same_session", "主动采集一份新证据", "Collect new evidence explicitly")
        add("heat.observe_condition", "日常使用中自愿观察一个条件", "Voluntarily observe one condition during ordinary use")
        add("heat.view_system_warning", "查看并遵循可用的系统热提示", "View and follow available system thermal guidance")
        add("heat.end", "结束：当前证据不能可靠判断", "End: current evidence does not support a reliable judgment")
        add("idle.background_activity_correlation:", "后台活动与掉电存在相关线索，原因尚未证实。", "Background activity is a correlated clue; a drain cause is not proven.")
        add("idle.rough_observation_rate", "区间平均掉电速度仅为粗略观察，不代表精准待机归因。", "Average drain over this interval is only rough observation, not precise idle attribution.")
        add("heat.separate_observations:", "这些是独立观察，不能据此连接成同一次发热事件或应用原因。", "These are separate observations; they do not establish one heat event or an app cause.")
        add("heat.charging_use", "用户声明充电与使用同时发生，两者贡献无法分离。", "The user reports concurrent charging and use; their contributions cannot be separated.")
        add("heat.battery_sensor_only:", "只有电池传感器读数，不能确定 CPU、表面温度或发热原因。", "Only a battery sensor reading is available; CPU or surface temperature and the heat cause remain unknown.")
        add("heat.rough_observation_rate:", "掉电速度只作粗略耗电背景，不是温度或发热原因。", "Drain rate is only rough power context, not temperature or a heat cause.")
        add("idle_answers_unreviewed", "待机声明尚未明确确认。", "Idle declarations have not been explicitly reviewed.")
        add("heat_answers_unreviewed", "发热声明尚未明确确认。", "Heat declarations have not been explicitly reviewed.")
        add("idle_answers_changed", "声明已改变，请重新预览确认样本。", "Declarations changed; preview and reconfirm samples.")
        add("heat_answers_changed", "发热声明已改变，请重新确认样本。", "Heat declarations changed; reconfirm samples.")
        add("idle_conditions_unconfirmed", "充电、闲置或重启条件仍未知。", "Charging, idle or reboot conditions remain unknown.")
        add("heat_conditions_unconfirmed", "任务、充电或是否仍热尚未知。", "Activity, charging or ongoing heat remains unknown.")
        add("idle_conditions_conflict", "待机条件与时间区间存在冲突。", "Idle conditions conflict with the declared interval.")
        add("heat_conditions_conflict", "充电时使用与未充电的声明冲突。", "Charging-use activity conflicts with a no-charging declaration.")
        add("idle_evidence_conflict", "证据编号或来源状态有冲突。", "Evidence IDs or source statuses conflict.")
        add("heat_evidence_conflict", "热证据编号或来源状态有冲突。", "Thermal evidence IDs or source statuses conflict.")
        add("idle_window_unknown", "统计窗口不能可靠对应本次区间。", "The statistics window cannot reliably represent this interval.")
        add("idle_app_evidence_incomplete", "应用归属、指标或窗口不完整，不能授权单应用验证。", "App attribution, metrics or windows are incomplete; app verification is unavailable.")
        add("idle_observation_rough", "两点式掉电观察是粗略平均值。", "Two-point drain observation is a rough average.")
        add("idle_quantification_missing", "尚无可用的区间掉电量化证据。", "No usable interval drain measurement is available.")
        add("idle_report_missing", "缺少支持应用线索的系统报告。", "A system report supporting app clues is missing.")
        add("heat_sensor_missing", "缺少可用的电池温度或已识别热状态。", "Usable battery temperature or recognized thermal state is missing.")
        add("heat_sensor_scope", "电池温度不是 CPU 或机身表面温度，不能确定原因。", "Battery temperature is not CPU or surface temperature and does not establish a cause.")
        add("heat_reading_invalid", "读数的类型、来源或窗口不能可靠使用。", "The reading's type, source or window is unusable.")
        add("heat_unit_unknown", "单位未知或不符合该指标，不能换算。", "The unit is unknown or mismatched; conversion is unavailable.")
        add("heat_evidence_incomplete", "来源或字段不完整，请保留其状态限制。", "Source or field coverage is incomplete; its status limits apply.")
        add("heat_event_unlinked", "独立读数和报告尚未关联到同一事件，时间接近也不证明关联。", "Independent readings and reports are not linked to one event; similar times do not prove a link.")
        add("heat_observation_rough", "粗略掉电速度不能作为热指标。", "Rough drain rate cannot serve as a heat metric.")
        add("heat_conditions_user_reported", "任务与充电条件来自用户声明，非系统观测。", "Activity and charging conditions are user declarations, not system observations.")
        add("heat_factors_not_separated", "同时充电与使用，贡献无法分离。", "Concurrent charging and use cannot be separated into contributions.")
        add("heat_recognized_severe_state", "报告记录了已识别的严重热状态；这不证明通知已显示，请查看可用系统提示。", "A recognized severe thermal state was reported; this does not establish that a notification was displayed. View available system guidance.")
        add("answer_time_range_reversed", "起止时间顺序无效，请核对声明。", "The declared time range is invalid; review the times.")
        add("app_groups_omitted", "摘要最多保留二十个应用组，其余未纳入判断。", "The summary retains at most twenty app groups; omitted groups do not inform this assessment.")
        add("report_device_unknown", "报告设备归属未知，可能来自过去或其他手机。", "Report device ownership is unknown; it may be from another phone or the past.")
        add("report_time_unknown", "报告事件时间未知；导入时间不能代替。", "Report event time is unknown; import time cannot replace it.")
        add("diagnostic_evidence_unavailable", "系统诊断证据不可用。", "System diagnostic evidence is unavailable.")
        add("thermal_status_unknown", "报告热状态未知；不能认定没有热限制。", "Reported thermal state is unknown; absence of thermal limits cannot be inferred.")
        add("observation_invalid_rate", "掉电速度数值无效。", "The drain-rate value is invalid.")
        add("observation_missing_battery_level", "观察缺少电量读数。", "Observation is missing a battery-level reading.")
        add("observation_unknown_power_state", "观察期间电源状态未知。", "Power state during observation is unknown.")
        add("observation_started_while_charging", "观察开始时正在充电。", "Observation started while charging.")
        add("observation_charging_during_observation", "观察期间有充电，不能作为掉电证据。", "Charging occurred during observation; it cannot measure drain.")
        add("observation_ended_while_charging", "观察结束时正在充电。", "Observation ended while charging.")
        add("observation_continuity_lost", "观察连续性丢失，无法可靠计算。", "Observation continuity was lost; reliable calculation is unavailable.")
        add("observation_invalid_time_range", "观察时间顺序无效。", "The observation time range is invalid.")
        add("observation_too_short", "观察时间不足，不能可靠计算。", "Observation was too short for a reliable calculation.")
        add("observation_battery_increased", "电量上升，本次不能作为掉电测量。", "Battery level increased; this observation cannot measure drain.")
        add("observation_no_measurable_drop", "未观察到可测量电量下降，不能推断没有耗电。", "No measurable level drop was observed; this does not imply zero drain.")
        add("guided.restart_confirmation_required", "切换场景须确认重新开始。", "Changing scenario requires confirmation to restart.")
        add("guided.invalid_sample_id", "样本编号无效。", "The sample ID is invalid.")
        add("guided.sample_id_conflict", "同一样本编号内容冲突；请使用新样本。", "Contents conflict under one sample ID; use a new sample.")
        add("guided.preview_required", "请先预览要确认的样本。", "Preview the sample before confirming it.")
        add("guided.preview_stale", "输入已改变；请重新预览并确认。", "Inputs changed; preview and confirm again.")
        add("guided.invalid_replacement", "请选择当前已确认的替换样本。", "Select a currently confirmed sample to replace.")
        add("guided.replacement_required", "已有两份样本，请明确选择替换哪一份。", "Two samples are retained; explicitly choose which to replace.")
        add("duplicate_evidence_id", "证据编号重复，摘要被拒绝。", "Duplicate evidence IDs; the summary was rejected.")
        add("invalid_summary", "摘要不符合证据白名单，已拒绝。", "The summary violates the evidence whitelist and was rejected.")
        add("summary_too_large", "摘要超过 48 KiB，已拒绝且未截断。", "The summary exceeds 48 KiB and was rejected without truncation.")
        add("guided.collection_failed", "采集失败；已确认的证据仍可使用。", "Collection failed; confirmed evidence remains available.")
        add("guided.verification_unavailable", "验证图或当前耗电摘要不适用，不能创建记录。", "The verification graph or current power summary is inapplicable; a record cannot be created.")
    }
    private fun mapped(id: String): String? = if (copy.containsKey(id) && !id.endsWith(":")) id else
        listOf("idle.background_activity_correlation:", "heat.separate_observations:", "heat.battery_sensor_only:", "heat.rough_observation_rate:")
            .firstOrNull { id.startsWith(it) && id.length > it.length }
    fun hasCopy(id: String): Boolean = mapped(id) != null
    fun text(id: String, language: AppLanguage): String = mapped(id)?.let { key -> copy.getValue(key).let { if (language == AppLanguage.EN) it.second else it.first } }
        ?: choose(language, "未知本地说明；不能据此采取动作。", "Unknown local explanation; it cannot authorize an action.")
    fun choose(language: AppLanguage, zh: String, en: String) = if (language == AppLanguage.EN) en else zh

    fun action(id: String, language: AppLanguage): String {
        val purpose = text(id, language)
        val detail = when (id) {
            "idle.open_observation" -> "请在普通使用中主动开始和结束已有观察；所有速度均为粗略值。" to "Explicitly start and finish the existing observation during ordinary use; every rate is rough."
            "idle.import_report" -> "主动选择系统报告；可能来自其他设备或过去，导入后只提供候选预览。" to "Choose a system report explicitly; it may be historical or from another device. Import only offers a candidate preview."
            "heat.collect_current", "heat.collect_same_session" -> "点击后只读取当前基础快照，不自动重复采样；读数不能连接为历史事件。" to "Click to read one current basic snapshot, with no repeated sampling; readings cannot establish a historical event."
            "idle.verify_background" -> "仅验证耗电，不验证发热；需同一完整应用组、适用证据与现有规则许可，预览后才保存。" to "Verify power only, not heat; require one complete app group, applicable evidence and existing policy allowance. Save only after preview."
            "heat.view_system_warning" -> "已识别严重状态，但不能证明通知已显示；查看设备已有提示，不要求继续负载或复现高温。" to "A severe state was recognized, but a displayed notification is not established. View available device guidance; do not continue load or reproduce heat."
            "idle.observe_condition", "heat.observe_condition" -> "仅在自愿的日常使用中观察一个条件；不要求制造热量或运行压力测试，不证明因果。" to "Observe one condition voluntarily during ordinary use; do not create heat or run stress tests. This does not prove causality."
            else -> "核对用户声明与证据；未知可保留，无可靠动作时结束。" to "Review user declarations and evidence; unknown may remain unknown. End when no reliable action exists."
        }
        return choose(language, "目的：$purpose\n前提与局限：${detail.first}\n停止条件：出现系统提示、不适或不愿继续时停止；不要求继续负载。\n返回判据：主动更新证据并重新预览确认；不能保证窗口可比或判定优化有效。",
            "Purpose: $purpose\nPrerequisite: ${detail.second}\nStop: Stop on system guidance, discomfort or unwillingness to continue; continuing load is never required.\nOn return: Update evidence explicitly and preview again; comparable windows or effective optimization are not guaranteed.")
    }

    fun fact(fact: GuidedEvidence, language: AppLanguage): String {
        val unknown = choose(language, "未知", "Unknown")
        val label = kind(fact.kind, language)
        val value = when (val v = fact.value) {
            is GuidedValue.Number -> "${v.value} ${unit(v.unit, language)}"
            is GuidedValue.Code -> if (fact.kind == GuidedEvidenceKind.THERMAL_STATUS) {
                val status = GuidedThermalStatus.fromCode(v.value)
                status?.let { thermal(it, language) } ?: unknown
            } else choose(language, if (v.value == "yes") "是" else "否", if (v.value == "yes") "Yes" else "No")
            null -> unknown
        }
        val window = fact.window
        return choose(language,
            "$label：$value\n证据编号：${fact.id}\n来源：${source(fact.source, language)} · ${status(fact.sourceStatus.name, language)} · ${status(fact.fieldStatus.name, language)} · ${status(fact.estimate.name, language)}\n应用组：${fact.packages.joinToString().ifEmpty { "无" }}\n采集 / 导入时间：${fact.collectedAt}\n报告事件时间：${fact.reportedAt ?: unknown}\n统计窗口：${window?.let { "${status(it.scope.name, language)} · ${it.startedAt ?: unknown} → ${it.endedAt ?: unknown} · ${it.batteryDurationMillis ?: unknown} ms · ${source(it.source, language)} · 充电 ${status(it.charging.name, language)} · 重置 ${it.resetId ?: unknown}" } ?: unknown}",
            "$label: $value\nEvidence ID: ${fact.id}\nSource: ${source(fact.source, language)} · ${status(fact.sourceStatus.name, language)} · ${status(fact.fieldStatus.name, language)} · ${status(fact.estimate.name, language)}\nApp group: ${fact.packages.joinToString().ifEmpty { "None" }}\nCollection / import time: ${fact.collectedAt}\nReport event time: ${fact.reportedAt ?: unknown}\nStatistics window: ${window?.let { "${status(it.scope.name, language)} · ${it.startedAt ?: unknown} → ${it.endedAt ?: unknown} · ${it.batteryDurationMillis ?: unknown} ms · ${source(it.source, language)} · charging ${status(it.charging.name, language)} · reset ${it.resetId ?: unknown}" } ?: unknown}")
    }
    fun kind(kind: GuidedEvidenceKind, language: AppLanguage): String = when (kind) {
        GuidedEvidenceKind.OVERALL_POWER -> "整体耗电" to "Overall power"
        GuidedEvidenceKind.APP_TOTAL_POWER -> "应用组总耗电" to "App-group total power"
        GuidedEvidenceKind.APP_FOREGROUND_POWER -> "应用组前台耗电" to "App-group foreground power"
        GuidedEvidenceKind.APP_BACKGROUND_POWER -> "应用组后台耗电" to "App-group background power"
        GuidedEvidenceKind.FOREGROUND_DURATION -> "前台累计时长" to "Cumulative foreground duration"
        GuidedEvidenceKind.BACKGROUND_DURATION -> "后台累计时长" to "Cumulative background duration"
        GuidedEvidenceKind.WAKELOCK_DURATION -> "唤醒锁累计时长" to "Cumulative wakelock duration"
        GuidedEvidenceKind.WAKEUP_COUNT -> "唤醒次数" to "Wakeup count"
        GuidedEvidenceKind.ALARM_COUNT -> "闹钟次数" to "Alarm count"
        GuidedEvidenceKind.JOB_COUNT -> "任务次数" to "Job count"
        GuidedEvidenceKind.BATTERY_TEMPERATURE -> "电池传感器温度" to "Battery sensor temperature"
        GuidedEvidenceKind.THERMAL_STATUS -> "报告热状态（传感器 / 全局出处不可恢复）" to "Reported thermal state (sensor / global provenance unavailable)"
        GuidedEvidenceKind.CHARGING -> "当前充电读数" to "Current charging reading"
        GuidedEvidenceKind.OBSERVATION_RATE -> "粗略平均掉电速度" to "Rough average drain rate"
    }.let { choose(language, it.first, it.second) }
    fun unit(unit: String, language: AppLanguage) = when (unit) {
        "mah" -> "mAh"; "celsius" -> "°C"; "millis" -> "ms"
        "count" -> choose(language, "次", "count")
        "percent_per_hour" -> choose(language, "%/小时（粗略）", "%/hour (rough)")
        else -> choose(language, "未知单位", "Unknown unit")
    }
    fun source(source: String, language: AppLanguage) = when (source) {
        "battery" -> choose(language, "基础电池快照", "Basic battery snapshot")
        "observation" -> choose(language, "两点式续航观察", "Two-point battery observation")
        "thermalservice" -> choose(language, "系统 thermalservice 报告", "System thermalservice report")
        "batterystats" -> choose(language, "系统 batterystats 报告", "System batterystats report")
        "packages", "alarm", "jobscheduler", "power", "deviceidle", "wakeup_sources" -> choose(language, "系统来源 $source", "System source $source")
        else -> choose(language, "未知来源", "Unknown source")
    }
    fun status(status: String, language: AppLanguage): String = when (status) {
        "AVAILABLE" -> "可用" to "Available"; "TRUNCATED" -> "截断" to "Truncated"
        "UNSUPPORTED" -> "不支持" to "Unsupported"; "PERMISSION_DENIED" -> "权限拒绝" to "Permission denied"
        "PARSE_FAILED" -> "解析失败" to "Parse failed"; "TIMED_OUT" -> "超时" to "Timed out"
        "NOT_PRESENT" -> "字段不存在" to "Not present"; "SOURCE_UNAVAILABLE" -> "来源不可用" to "Source unavailable"
        "PARSED" -> "已解析" to "Parsed"
        "NOT_PARSED" -> "未解析" to "Not parsed"; "ACTUAL" -> "实际读数" to "Actual reading"
        "ESTIMATED" -> "估算" to "Estimated"; "YES" -> "是（用户声明）" to "Yes (user declaration)"
        "NO" -> "否" to "No"; "SINCE_LAST_CHARGE" -> "上次充电以来" to "Since last charge"
        "SOURCE_CUMULATIVE" -> "来源累计区间" to "Source cumulative interval"
        "EXPLICIT_WINDOW" -> "明确窗口" to "Explicit window"
        else -> "未知 / 不确定" to "Unknown / unsure"
    }.let { choose(language, it.first, it.second) }
    private fun thermal(status: GuidedThermalStatus, language: AppLanguage): String = when (status) {
        GuidedThermalStatus.NONE -> "未报告限制" to "No reported throttling"
        GuidedThermalStatus.LIGHT -> "轻度" to "Light"; GuidedThermalStatus.MODERATE -> "中度" to "Moderate"
        GuidedThermalStatus.SEVERE -> "严重" to "Severe"; GuidedThermalStatus.CRITICAL -> "危急" to "Critical"
        GuidedThermalStatus.EMERGENCY -> "紧急" to "Emergency"; GuidedThermalStatus.SHUTDOWN -> "关闭状态" to "Shutdown"
    }.let { choose(language, it.first, it.second) }
}
