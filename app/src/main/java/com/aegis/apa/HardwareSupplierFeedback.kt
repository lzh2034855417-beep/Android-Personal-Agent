package com.aegis.apa

import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.tool.HardwareSupplierRecognition
import com.aegis.apa.tool.HardwareSupplyInfo
import com.aegis.apa.tool.HardwareSupplySource

object HardwareSupplierFeedback {
    fun format(
        deviceName: String,
        androidVersion: String,
        hardware: HardwareSupplyInfo?,
        appVersion: String,
        language: AppLanguage = AppLanguage.ZH_CN
    ): String? {
        hardware ?: return null
        if (!HardwareSupplierRecognition.hasUnrecognizedSupplier(hardware)) return null

        return if (language == AppLanguage.EN) buildString {
            appendLine("APA hardware recognition feedback")
            appendLine("Device: $deviceName")
            appendLine("Android: $androidVersion")
            appendLine("RAM vendor: ${hardware.ramVendor ?: "Not read"}")
            appendLine("RAM specification: ${hardware.ramType ?: "Not read"}")
            appendLine("ROM vendor: ${hardware.storageVendor ?: "Not read"}")
            appendLine("ROM model: ${hardware.storageModel ?: "Not read"}")
            appendLine("ROM specification: ${hardware.storageSpec ?: "Not read"}")
            appendLine("Source: ${hardware.source.label(language)}")
            appendLine("APA: $appVersion")
            append("Privacy: no serial number, account, app list, original system report, or API key is included.")
        } else buildString {
            appendLine("APA 硬件识别反馈")
            appendLine("设备：$deviceName")
            appendLine("Android：$androidVersion")
            appendLine("RAM 厂商：${hardware.ramVendor ?: "未读取"}")
            appendLine("RAM 规格：${hardware.ramType ?: "未读取"}")
            appendLine("ROM 厂商：${hardware.storageVendor ?: "未读取"}")
            appendLine("ROM 型号：${hardware.storageModel ?: "未读取"}")
            appendLine("ROM 规格：${hardware.storageSpec ?: "未读取"}")
            appendLine("来源：${hardware.source.label(language)}")
            appendLine("APA：$appVersion")
            append("说明：不含序列号、账号、应用列表、原始系统报告或密钥。")
        }
    }

    private fun HardwareSupplySource.label(language: AppLanguage): String = when (this) {
        HardwareSupplySource.ANDROID_BUGREPORT -> "Android bugreport"
        HardwareSupplySource.ROOT_SYSFS -> if (language == AppLanguage.EN) "System nodes" else "系统节点"
        HardwareSupplySource.COMBINED -> if (language == AppLanguage.EN) "Android bugreport + system nodes" else "Android bugreport + 系统节点"
    }
}
