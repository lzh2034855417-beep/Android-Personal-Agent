package com.aegis.apa.localization

import com.aegis.apa.tool.BugReportRejectReason

enum class RuntimeNotice {
    SNAPSHOT_READ_FAILED,
    BUG_REPORT_READ_FAILED,
    ROOT_DIAGNOSTIC_FAILED,
    BATTERY_START_SAVE_FAILED,
    BATTERY_START_READ_FAILED,
    BATTERY_DISCARDED,
    BATTERY_DISCARD_WRITE_AND_CLEAR_FAILED,
    BATTERY_DISCARD_CLEAR_FAILED,
    BATTERY_RESULT_CLEAR_FAILED,
    BATTERY_END_READ_FAILED,
    BATTERY_CLEAR_FAILED,
    CHARGING_EVIDENCE_WRITE_FAILED,
    SUPPLIER_FEEDBACK_COPIED
}

object RuntimeUiCopy {
    fun text(notice: RuntimeNotice, language: AppLanguage): String =
        if (language == AppLanguage.EN) english(notice) else chinese(notice)

    fun bugReportReject(reason: BugReportRejectReason, language: AppLanguage): String =
        if (language == AppLanguage.EN) when (reason) {
            BugReportRejectReason.EMPTY -> "No recognizable system power section was found in the report."
            BugReportRejectReason.UNSUPPORTED_FORMAT -> "Only Android Bug Report ZIP or text files are supported."
            BugReportRejectReason.CORRUPT_ARCHIVE -> "The archive is damaged or could not be read safely. Generate a new report."
            BugReportRejectReason.UNSAFE_ENTRY_NAME -> "The report archive contains an unsafe path and was not read."
            BugReportRejectReason.NESTED_ARCHIVE -> "The report contains a nested archive and was not read."
            BugReportRejectReason.TOO_MANY_ENTRIES -> "The report contains too many files, so reading was stopped."
            BugReportRejectReason.ENTRY_TOO_LARGE -> "A file in the report is too large, so reading was stopped."
            BugReportRejectReason.TOTAL_TOO_LARGE -> "The extracted report is too large, so reading was stopped."
        } else when (reason) {
            BugReportRejectReason.EMPTY -> "报告里没有找到可识别的系统耗电段落。"
            BugReportRejectReason.UNSUPPORTED_FORMAT -> "只支持系统生成的 Bug Report ZIP 或文本文件。"
            BugReportRejectReason.CORRUPT_ARCHIVE -> "压缩包已损坏或无法安全读取，请重新生成报告。"
            BugReportRejectReason.UNSAFE_ENTRY_NAME -> "报告压缩包包含不安全路径，已拒绝读取。"
            BugReportRejectReason.NESTED_ARCHIVE -> "报告包含嵌套压缩包，已拒绝读取。"
            BugReportRejectReason.TOO_MANY_ENTRIES -> "报告文件条目过多，已停止读取。"
            BugReportRejectReason.ENTRY_TOO_LARGE -> "报告中的单个文件过大，已停止读取。"
            BugReportRejectReason.TOTAL_TOO_LARGE -> "报告解压后的内容过大，已停止读取。"
        }

    private fun english(notice: RuntimeNotice): String = when (notice) {
        RuntimeNotice.SNAPSHOT_READ_FAILED -> "Could not refresh device data. The last sample is still shown."
        RuntimeNotice.BUG_REPORT_READ_FAILED -> "Could not read this system report. Generate a new report and try again."
        RuntimeNotice.ROOT_DIAGNOSTIC_FAILED -> "Could not collect the system power diagnostic. Check Root authorization and try again."
        RuntimeNotice.BATTERY_START_SAVE_FAILED -> "Could not save the observation start on this device. Check storage and try again."
        RuntimeNotice.BATTERY_START_READ_FAILED -> "Could not read the current charge level. Try again."
        RuntimeNotice.BATTERY_DISCARDED -> "This measurement was discarded because the phone was charged or charging was uncertain."
        RuntimeNotice.BATTERY_DISCARD_WRITE_AND_CLEAR_FAILED -> "The measurement is invalid in this session, but saving the charging marker and clearing the start point both failed. Do not calculate it; check storage and cancel again."
        RuntimeNotice.BATTERY_DISCARD_CLEAR_FAILED -> "The measurement is invalid, but the saved start point could not be cleared. Cancel again."
        RuntimeNotice.BATTERY_RESULT_CLEAR_FAILED -> "The result was calculated, but the old start point could not be cleared. Tap Clear before starting another observation."
        RuntimeNotice.BATTERY_END_READ_FAILED -> "Could not read the ending charge level. Try again; the observation is still running."
        RuntimeNotice.BATTERY_CLEAR_FAILED -> "Could not clear the saved observation start. Try again."
        RuntimeNotice.CHARGING_EVIDENCE_WRITE_FAILED -> "Power was connected, so this observation is invalid. The charging marker could not be saved; finish or cancel and try again."
        RuntimeNotice.SUPPLIER_FEEDBACK_COPIED -> "Copied. Send it to the developer to extend supplier recognition."
    }

    private fun chinese(notice: RuntimeNotice): String = when (notice) {
        RuntimeNotice.SNAPSHOT_READ_FAILED -> "读取失败，请重试；当前显示的是上次采样。"
        RuntimeNotice.BUG_REPORT_READ_FAILED -> "无法读取这个系统报告，请重新生成后再试。"
        RuntimeNotice.ROOT_DIAGNOSTIC_FAILED -> "无法完成系统耗电采集，请检查 Root 授权后重试。"
        RuntimeNotice.BATTERY_START_SAVE_FAILED -> "无法在本机保存观察起点，请检查存储状态后重试。"
        RuntimeNotice.BATTERY_START_READ_FAILED -> "无法读取当前电量，请重试。"
        RuntimeNotice.BATTERY_DISCARDED -> "已按“充过电或不确定”作废本次测量。"
        RuntimeNotice.BATTERY_DISCARD_WRITE_AND_CLEAR_FAILED -> "本次已在当前会话作废，但充电标记写入和起点清除都失败；请勿继续计算，检查存储后取消重试。"
        RuntimeNotice.BATTERY_DISCARD_CLEAR_FAILED -> "本次已作废，但本机观察起点清除失败；请取消后重试。"
        RuntimeNotice.BATTERY_RESULT_CLEAR_FAILED -> "结果已计算，但旧观察起点清除失败；请点“清除”后再开始下一次。"
        RuntimeNotice.BATTERY_END_READ_FAILED -> "无法读取结束电量，请重试；观察仍在继续。"
        RuntimeNotice.BATTERY_CLEAR_FAILED -> "无法清除本机观察起点，请重试。"
        RuntimeNotice.CHARGING_EVIDENCE_WRITE_FAILED -> "检测到连接电源，本次观察已作废；充电标记写入失败，请结束或取消后重试。"
        RuntimeNotice.SUPPLIER_FEEDBACK_COPIED -> "已复制，可发给开发者补充厂商库"
    }
}
