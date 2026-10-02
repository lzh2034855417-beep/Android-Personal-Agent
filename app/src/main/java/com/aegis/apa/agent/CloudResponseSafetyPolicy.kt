package com.aegis.apa.agent

/** Conservative lexical guards. They cannot establish evidence accuracy or catch every paraphrase. */
internal object CloudResponseSafetyPolicy {
    // A period followed by whitespace ends a sentence, unlike dots inside IDs or decimal values.
    private val boundaries = Regex("(?<=[。！？!?；;])|\\R+|(?<=\\.)\\s+")
    private val advancedActions = Regex(
        "\\b(?:freeze|throttle|underclock|overclock|force[- ]stop)\\b(?!\\s+(?:is|means|refers)\\b)(?=\\s+\\S)|" +
            "\\b(?:try|consider|recommend|start)\\s+(?:freezing|throttling|underclocking|overclocking)\\b|" +
            "\\b(?:disable|suspend)\\s+(?:(?:the|this|that|an?)\\s+)?" +
            "(?:app(?:lication)?\\b(?!\\s+(?:notifications?|sync|alerts?|permissions?|background\\s+(?:data|activity)|battery\\s+optimization|auto[- ]?start)\\b)|package\\b|" +
            "system\\s+(?:app|service)\\b|[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)", RegexOption.IGNORE_CASE)
    private val namedAppDisable = Regex("\\b(?:[Dd]isable|[Ss]uspend)\\s+([\\p{Lu}][\\p{L}\\p{N}_-]+)(?=\\s*(?:[.!?;]|$)|\\s+(?:temporarily|to|for|now)\\b)")
    private val ordinaryNamedSettings = setOf("bluetooth", "wifi", "wi-fi", "nfc", "gps", "notifications", "sync", "location", "vibration", "backup", "autofill", "hotspot", "tethering")
    private val actionContext = Regex("(?:please|should|must|can|could|need\\s+to|try|recommend|suggest|consider|to|then|and|instead|next|first|请|建议|需要|可以|应当|先|再|然后)\\s*$", RegexOption.IGNORE_CASE)
    private val listPrefix = Regex("^(?:[-+*•>]\\s*|[0-9]+[.)]\\s*|\\([0-9]+\\)\\s*)+$")
    private val englishNegation = Regex("(?:do\\s+not|don't|never|avoid|cannot|can't|must\\s+not|should\\s+not|not\\s+to|no\\s+need\\s+to)\\s*$", RegexOption.IGNORE_CASE)
    private val chineseNegation = Regex("(?:不要|请勿|勿|不应|不应该|禁止|不必|无需|不能|别)\\s*$")
    private val chineseNonRequest = Regex("(?:已经|已|无需|不需要|不要|不必|尚未|未)$")
    private val reportTerms = Regex("\\b(?:bug\\s+report|report|system\\s+power\\s+diagnostic|diagnostic\\s+summary)\\b|报告|系统耗电诊断|诊断摘要", RegexOption.IGNORE_CASE)
    private val uploadFacts = Regex("^\\s+(?:events?|logs?|attempts?|traffic|history|requests?|statistics)\\b", RegexOption.IGNORE_CASE)
    private val requests = Regex("\\b(?:generate|import|upload|provide|send|attach|share|reupload|re-upload|regenerate)\\b|生成|导入|上传|提供|重新|再次|再做|再抓", RegexOption.IGNORE_CASE)

    fun segments(content: String): List<String> = content.split(boundaries).map(String::trim).filter(String::isNotEmpty)

    private fun isNegated(content: String, start: Int): Boolean {
        val prefix = content.substring((start - 40).coerceAtLeast(0), start)
        return englishNegation.containsMatchIn(prefix) || chineseNegation.containsMatchIn(prefix)
    }

    private fun isActionContext(content: String, match: MatchResult): Boolean {
        val prefix = content.substring(0, match.range.first).trimEnd()
        return prefix.isEmpty() || listPrefix.matches(prefix) || prefix.endsWith(":") || prefix.endsWith("：") ||
            actionContext.containsMatchIn(prefix) ||
            match.value.startsWith("try", true) || match.value.startsWith("consider", true) ||
            match.value.startsWith("recommend", true) || match.value.startsWith("start", true)
    }

    fun containsAdvancedAction(segment: String): Boolean =
        advancedActions.findAll(segment).any {
            !isNegated(segment, it.range.first) && isActionContext(segment, it)
        } || namedAppDisable.findAll(segment).any {
            it.groupValues[1].lowercase(java.util.Locale.ROOT) !in ordinaryNamedSettings &&
                !isNegated(segment, it.range.first) && isActionContext(segment, it)
        }

    fun requestsAttachedReport(segment: String): Boolean {
        val lastReport = reportTerms.findAll(segment).lastOrNull() ?: return false
        return requests.findAll(segment).any {
            // English requests must precede the report object, not describe upload events in it.
            val englishRequest = it.value.first().isLetter() && it.value.first().code < 128
            val prefix = segment.substring((it.range.first - 40).coerceAtLeast(0), it.range.first).trimEnd()
            // A report "提供了" evidence is a fact, unlike "请将报告提供/上传".
            val chineseProvidedFact = it.value == "提供" &&
                reportTerms.containsMatchIn(prefix) && segment.substring(it.range.last + 1).startsWith("了")
            !isNegated(segment, it.range.first) && !chineseNonRequest.containsMatchIn(prefix) &&
                !chineseProvidedFact &&
                (!englishRequest || it.range.first < lastReport.range.first &&
                    !uploadFacts.containsMatchIn(segment.substring(it.range.last + 1)))
        }
    }
}
