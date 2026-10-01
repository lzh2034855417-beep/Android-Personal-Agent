package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage

object AgentAttachmentPolicy {
    fun showAppReport(isOnline: Boolean): Boolean = isOnline

    fun disclosure(isOnline: Boolean, language: AppLanguage = AppLanguage.ZH_CN): String = if (language == AppLanguage.EN) {
        if (isOnline) {
            "Sent online: your question, the selected capability report and base snapshot, optional app and usage reports, system power diagnostic, and up to 12 recent messages with the same provider. With no report selected, only the question and conversation are sent. " +
                "Root raw output is not sent; an L2 summary may include capacity, cycle, and temperature readings. Imported or collected power diagnostics are selected by default and can be removed before sending."
        } else {
            "On-device analysis uses only the local snapshot and selected power diagnostic. Nothing is uploaded, and APA suggests actions without executing system or Scene changes."
        }
    } else if (isOnline) {
        "在线发送：问题、你本次选中的等级报告及其基础快照，以及你勾选的应用报告、使用习惯排行、系统耗电诊断和同一服务最近最多 12 条对话；不选择任何报告时只发送问题和对话。" +
            "Root 原始输出不会发送；若选择 L2，容量、循环、温度等 Root 数据摘要可能随等级报告发送。" +
            "导入或采集后的系统耗电诊断默认勾选，可在发送前取消。"
    } else {
        "本地分析仅在本机使用基础快照及当前勾选的系统耗电诊断，不会上传；只给建议，不执行系统或 Scene 操作。"
    }
}
