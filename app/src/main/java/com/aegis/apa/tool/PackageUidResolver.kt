package com.aegis.apa.tool

object PackageUidResolver {
    private val packagePattern = Regex("package:(\\S+)\\s+uid:(\\d+)")

    fun resolve(output: String): Map<Int, List<String>> = packagePattern.findAll(output)
        .groupBy(
            keySelector = { it.groupValues[2].toInt() },
            valueTransform = { it.groupValues[1] }
        )
        .mapValues { (_, packages) -> packages.distinct().sorted() }
}
