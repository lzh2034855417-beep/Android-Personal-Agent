package com.aegis.apa.tool

import com.aegis.apa.model.DeviceIdentifiers
import com.aegis.apa.model.DeviceIdentity
import com.aegis.apa.model.DeviceNameSource
import de.boehrsi.devicemarketingnames.DeviceMarketingNames
import java.util.Locale

/** Offline lookup results are names, never evidence of hardware specifications. */
object DeviceNameResolver {
    private val curatedNames = mapOf(("xiaomi" to "2509FPN0BC") to "Xiaomi 17 Pro Max")

    fun resolve(identifiers: DeviceIdentifiers,
        offlineLookup: (String) -> String? = DeviceMarketingNames::getSingleNameFromModel
    ): DeviceIdentity {
        val maker = clean(identifiers.manufacturer)
        val brand = clean(identifiers.brand) ?: maker
        val model = clean(identifiers.modelCode)
        val code = model?.uppercase(Locale.ROOT)
        val displayBrand = brand?.lowercase(Locale.ROOT)?.replaceFirstChar { it.titlecase(Locale.ROOT) } ?: "Android"
        val curated = curatedNames[maker?.lowercase(Locale.ROOT) to code]
        if (curated != null) return DeviceIdentity(identifiers, curated, DeviceNameSource.CURATED)
        // A model-only database must not bypass the manufacturer constraint of curated entries.
        val reservedCode = curatedNames.keys.any { it.second == code }
        val offlineName = if (code != null && !reservedCode) {
            runCatching { offlineLookup(code) }.getOrNull()?.let(::clean)?.takeUnless { it.equals(model, true) }
        } else null
        if (offlineName != null) {
            val prefixed = brand == null || offlineName.equals(displayBrand, true) || offlineName.startsWith("$displayBrand ", true)
            return DeviceIdentity(identifiers, if (prefixed) offlineName else "$displayBrand $offlineName", DeviceNameSource.OFFLINE_DATABASE)
        }
        return DeviceIdentity(identifiers,
            if (model == null) "$displayBrand 未识别机型" else "$displayBrand · 系统型号 $model",
            if (model == null) DeviceNameSource.UNAVAILABLE else DeviceNameSource.SYSTEM_MODEL)
    }

    private fun clean(value: String?): String? = value?.trim()?.takeUnless {
        it.isEmpty() || it.lowercase(Locale.ROOT) in setOf("unknown", "null", "n/a")
    }
}

fun publicDeviceName(manufacturer: String?, modelCode: String?): String =
    DeviceNameResolver.resolve(DeviceIdentifiers(manufacturer, modelCode)).displayName
