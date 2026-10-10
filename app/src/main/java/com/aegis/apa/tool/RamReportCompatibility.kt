package com.aegis.apa.tool

import java.util.Locale

/** Report fields are interpreted according to their source, never the phone model. */
internal object RamReportCompatibility {
    private val bootRecord = Regex(
        "^\\s*(?:[0-9]+\\s+)?B\\s*-\\s*[0-9]+\\s*-\\s*Manufacturer ID\\s*=\\s*(0x[0-9a-f]+|[0-9]+),\\s*Device Type\\s*=\\s*(0x[0-9a-f]+|[0-9]+)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val attachmentPaths = setOf(
        "fs/sys/project_info/ddr_manufacture_info",
        "fs/sys/project_info/component_info/ddr",
        "fs/sys/class/sec/sec_hw_param/ddr_info",
        "fs/sys/devices/virtual/sec/sec_hw_param/ddr_info"
    )

    fun isAttachment(name: String) = name.lowercase(Locale.ROOT) in attachmentPaths

    fun bootFields(line: String): Map<String, String> {
        val match = bootRecord.matchEntire(line) ?: return emptyMap()
        val vendor = byteValue(match.groupValues[1]) ?: return emptyMap()
        val type = byteValue(match.groupValues[2]) ?: return emptyMap()
        if (vendor == 0 || type == 0) return emptyMap()
        return mapOf("DDR MR Manufacturer ID" to hex(vendor), "DDR Qualcomm Device Type" to hex(type))
    }

    fun attachmentFields(name: String, text: String): String {
        if ('\u0000' in text || '\uFFFD' in text) return ""
        return when (name.lowercase(Locale.ROOT)) {
            "fs/sys/project_info/ddr_manufacture_info" -> {
                // OnePlus project_info.c prints this node with %d, not hexadecimal.
                val raw = text.trim()
                if (!raw.matches(Regex("[0-9]{1,3}"))) return ""
                val id = raw.toInt().takeIf { it in 1..255 } ?: return ""
                "DDR MR Manufacturer ID = ${hex(id)}"
            }
            "fs/sys/project_info/component_info/ddr" -> {
                val raw = Regex("(?im)^MANU:\\s*(Samsung|Hynix|Micron|Nanya|Winbond|Elpida|Qimonda|ESMT)\\b")
                    .find(text)?.groupValues?.get(1) ?: return ""
                "DDR Vendor Name = $raw"
            }
            "fs/sys/class/sec/sec_hw_param/ddr_info", "fs/sys/devices/virtual/sec/sec_hw_param/ddr_info" -> {
                val code = Regex("\"DDRV\"\\s*:\\s*\"(SEC|HYN|MIC|NAN|WIN|ESM)\"").find(text)
                    ?.groupValues?.get(1) ?: return ""
                val vendor = when (code) {
                    "SEC" -> "Samsung"
                    "HYN" -> "Hynix"
                    "MIC" -> "Micron"
                    "NAN" -> "Nanya"
                    "WIN" -> "Winbond"
                    else -> "ESMT"
                }
                "DDR Vendor Name = $vendor"
            }
            else -> ""
        }
    }

    private fun byteValue(raw: String): Int? =
        if (raw.startsWith("0x", ignoreCase = true)) raw.substring(2).toIntOrNull(16)?.takeIf { it in 0..255 }
        else raw.toIntOrNull()?.takeIf { it in 0..255 }

    private fun hex(value: Int) = "0x${value.toString(16)}"
}
