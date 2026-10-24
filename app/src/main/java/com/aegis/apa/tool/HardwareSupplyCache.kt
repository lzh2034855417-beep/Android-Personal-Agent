package com.aegis.apa.tool

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Tiny derived hardware cache. The app places it in noBackupFilesDir, never saving raw reports. */
class HardwareSupplyCache(private val file: File, private val manufacturer: String, private val model: String) {
    @Synchronized
    fun load(): HardwareSupplyInfo? = runCatching {
        if (!file.isFile || file.length() > 4096) return null
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        if (properties.getProperty("version") != "1" ||
            properties.getProperty("manufacturer") != manufacturer || properties.getProperty("model") != model) return null
        val source = HardwareSupplySource.valueOf(properties.getProperty("source"))
        HardwareSupplyInfo(
            ramVendor = validField(properties.getProperty("ramVendor")),
            ramType = validField(properties.getProperty("ramType")),
            storageVendor = validField(properties.getProperty("storageVendor")),
            storageModel = validField(properties.getProperty("storageModel")),
            storageSpec = validField(properties.getProperty("storageSpec")), source = source
        ).takeIf { it.hasDetails }
    }.getOrNull()

    @Synchronized
    fun saveLocal(info: HardwareSupplyInfo?): Boolean {
        if (info?.source != HardwareSupplySource.ROOT_SYSFS || !info.hasDetails) return false
        return save(info.mergeMissingFrom(load()))
    }

    fun matchesReport(headers: Map<String, String>): Boolean =
        validField(headers["ro.product.manufacturer"])?.equals(manufacturer, ignoreCase = true) == true &&
            validField(headers["ro.product.model"]) == model

    @Synchronized
    fun saveReport(info: HardwareSupplyInfo?, headers: Map<String, String>, confirmedCurrentDevice: Boolean): Boolean {
        if (!confirmedCurrentDevice || !matchesReport(headers) ||
            info?.source != HardwareSupplySource.ANDROID_BUGREPORT || !info.hasDetails) return false
        // A report may only supplement known local fields, never relabel them.
        return save(load()?.mergeMissingFrom(info) ?: info)
    }

    private fun save(info: HardwareSupplyInfo): Boolean = runCatching {
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("manufacturer", manufacturer)
            setProperty("model", model)
            setProperty("source", info.source.name)
            listOf("ramVendor" to info.ramVendor, "ramType" to info.ramType,
                "storageVendor" to info.storageVendor, "storageModel" to info.storageModel,
                "storageSpec" to info.storageSpec).forEach { (key, value) ->
                validField(value)?.let { setProperty(key, it) }
            }
        }
        if (properties.size <= 4) return false
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.new")
        FileOutputStream(pending).use { output -> properties.store(output, null); output.fd.sync() }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)

    private fun validField(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.length <= 160 && it.none(Char::isISOControl)
    }
}
