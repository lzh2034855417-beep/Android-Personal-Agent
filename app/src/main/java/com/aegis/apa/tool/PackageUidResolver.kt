package com.aegis.apa.tool

object PackageUidResolver {
    private val packagePattern = Regex("package:(\\S+)\\s+uid:(\\d+)")
    private val packageBlockPattern = Regex("^\\s*Package\\s+\\[([A-Za-z0-9._]+)](?:\\s+.*)?$")
    private val packageUidPattern = Regex("^\\s*(?:userId|appId)=(\\d+)\\b.*$")

    fun resolve(output: String, targetUids: Set<Int> = emptySet()): Map<Int, List<String>> {
        val packagesByUid = linkedMapOf<Int, MutableSet<String>>()
        val packagesByAppId = linkedMapOf<Int, MutableSet<String>>()
        val targetUidsByAppId = targetUids.groupBy { uid -> uid % PER_USER_RANGE }
        packagePattern.findAll(output).forEach { match ->
            val uid = match.groupValues[2].toIntOrNull() ?: return@forEach
            packagesByUid.getOrPut(uid, ::linkedSetOf) += match.groupValues[1]
        }

        var currentPackage: String? = null
        var packageIndent = -1
        output.lineSequence().forEach { line ->
            packageBlockPattern.matchEntire(line)?.let { match ->
                currentPackage = match.groupValues[1]
                packageIndent = line.leadingWhitespaceCount()
                return@forEach
            }

            val packageName = currentPackage ?: return@forEach
            val indent = line.leadingWhitespaceCount()
            val uid = packageUidPattern.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
            if (uid != null && uid < PER_USER_RANGE && indent > packageIndent) {
                packagesByAppId.getOrPut(uid, ::linkedSetOf) += packageName
            } else if (line.isNotBlank() && indent <= packageIndent) {
                currentPackage = null
                packageIndent = -1
            }
        }

        packagesByAppId.forEach { (appId, packages) ->
            packagesByUid.getOrPut(appId, ::linkedSetOf) += packages
            targetUidsByAppId[appId].orEmpty().forEach { targetUid ->
                packagesByUid.getOrPut(targetUid, ::linkedSetOf) += packages
            }
        }

        return packagesByUid.mapValues { (_, packages) -> packages.sorted() }
    }

    private fun String.leadingWhitespaceCount(): Int {
        val firstContentIndex = indexOfFirst { !it.isWhitespace() }
        return if (firstContentIndex < 0) length else firstContentIndex
    }

    private const val PER_USER_RANGE = 100_000
}
