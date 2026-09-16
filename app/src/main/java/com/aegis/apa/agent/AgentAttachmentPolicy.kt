package com.aegis.apa.agent

object AgentAttachmentPolicy {
    fun showAppReport(isOnline: Boolean): Boolean = isOnline

    fun disclosure(isOnline: Boolean): String = if (isOnline) {
        "在线发送：问题、基础快照、当前等级报告，以及你勾选的应用报告、使用习惯排行、系统耗电诊断和同一服务最近最多 12 条对话。" +
            "Root 原始输出不会发送；若选择 L2，容量、循环、温度等 Root 数据摘要可能随等级报告发送。" +
            "导入或采集后的系统耗电诊断默认勾选，可在发送前取消。"
    } else {
        "本地分析仅在本机使用基础快照及当前勾选的系统耗电诊断，不会上传；只给建议，不执行系统或 Scene 操作。"
    }
}
