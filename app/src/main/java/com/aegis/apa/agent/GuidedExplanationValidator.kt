package com.aegis.apa.agent

import com.aegis.apa.localization.AppLanguage
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import java.util.Collections

class GuidedReferenceNote(ids: List<String>, val text: String) {
    val ids: List<String> = Collections.unmodifiableList(ArrayList(ids))
    override fun equals(other: Any?) = other is GuidedReferenceNote && ids == other.ids && text == other.text
    override fun hashCode() = listOf(ids, text).hashCode()
}
class GuidedExplanationText(factNotes: List<GuidedReferenceNote>,
    candidateNotes: List<GuidedReferenceNote>, gapNotes: List<GuidedReferenceNote>, val actionNote: String) {
    val factNotes: List<GuidedReferenceNote> = Collections.unmodifiableList(ArrayList(factNotes))
    val candidateNotes: List<GuidedReferenceNote> = Collections.unmodifiableList(ArrayList(candidateNotes))
    val gapNotes: List<GuidedReferenceNote> = Collections.unmodifiableList(ArrayList(gapNotes))
}
sealed interface GuidedExplanationResult {
    data class Accepted(val text: GuidedExplanationText) : GuidedExplanationResult
    data class Rejected(val reasonId: String) : GuidedExplanationResult
}

object GuidedExplanationValidator {
    const val MAX_RAW_BYTES = 16384
    fun validate(raw: String, payload: GuidedPayloadResult.Ready): GuidedExplanationResult {
        val bytes = try { guidedUtf8Size(raw) }
        catch (_: IllegalArgumentException) { return GuidedExplanationResult.Rejected("guided.ai.invalid_unicode") }
        if (bytes > MAX_RAW_BYTES) return GuidedExplanationResult.Rejected("guided.ai.response_too_large")
        val document = try { GuidedAndroidJson.decode(raw) }
        catch (_: Exception) { return GuidedExplanationResult.Rejected("guided.ai.invalid_json") }
        return validateDecoded(document, payload)
    }
    internal fun validateDecoded(document: Any?, payload: GuidedPayloadResult.Ready): GuidedExplanationResult {
        return try {
            val root = document as? Map<*, *> ?: invalid("invalid_schema")
            if (root.keys != setOf("version", "factNotes", "candidateNotes", "gapNotes", "actionId", "actionNote")) invalid("invalid_schema")
            if (root["version"] != 1 && root["version"] != 1L) invalid("invalid_schema")
            if (root["actionId"] !is String || root["actionId"] != payload.actionId) invalid("invalid_action")
            val facts = notes(root["factNotes"], 20, payload.factIds)
            val candidates = notes(root["candidateNotes"], 3, payload.candidateIds)
            val gaps = notes(root["gapNotes"], 20, payload.gapIds)
            val action = text(root["actionNote"])
            GuidedExplanationResult.Accepted(GuidedExplanationText(facts, candidates, gaps, action))
        } catch (error: InvalidExplanation) { GuidedExplanationResult.Rejected(error.reasonId) }
    }

    private fun notes(raw: Any?, limit: Int, allowed: Set<String>): List<GuidedReferenceNote> {
        val rows = raw as? List<*> ?: invalid("invalid_schema")
        if (rows.size > limit) invalid("text_limit")
        return rows.map { rawRow ->
            val row = rawRow as? Map<*, *> ?: invalid("invalid_schema")
            if (row.keys != setOf("ids", "text")) invalid("invalid_schema")
            val ids = row["ids"] as? List<*> ?: invalid("invalid_schema")
            if (ids.isEmpty() || ids.size > 20) invalid("invalid_reference")
            val strings = ids.map { it as? String ?: invalid("invalid_schema") }
            if (strings.distinct().size != strings.size || strings.any { it !in allowed }) invalid("invalid_reference")
            GuidedReferenceNote(strings, text(row["text"]))
        }
    }

    private val commands = Regex("\\badb\\s+shell\\b|\\bsu\\s+-c\\b|\\bpm\\s+(?:disable(?:-user)?|suspend|hide|uninstall)\\b|\\bcmd\\s+package\\b|\\bam\\s+force-stop\\b|\\bsettings\\s+put\\b|(?:建议|请|可以|应当|执行|先).{0,12}(?:冻结|限频|刷入)", RegexOption.IGNORE_CASE)
    private fun text(raw: Any?): String {
        val value = raw as? String ?: invalid("invalid_schema")
        try { guidedUtf8Size(value) } catch (_: IllegalArgumentException) { invalid("invalid_unicode") }
        if (value.codePointCount(0, value.length) > 500) invalid("text_limit")
        if (commands.containsMatchIn(value) || CloudResponseSafetyPolicy.segments(value).any(CloudResponseSafetyPolicy::containsAdvancedAction)) invalid("capability_violation")
        return value
    }
    private class InvalidExplanation(val reasonId: String) : IllegalArgumentException()
    private fun invalid(suffix: String): Nothing = throw InvalidExplanation("guided.ai.$suffix")
}

/**
 * Existing Android strict streaming parser owns grammar/tokenization; this adapter collects values,
 * rejects duplicate decoded names, and bounds recursion. The small lexical preflight closes its
 * permissive strings/keywords/BOM/object-semicolon handling, not a second JSON grammar parser.
 * These gaps are present in installed Android36.1 JsonReader nextString/readEscapeCharacter/
 * decodeLiteral/nextInObject.
 * Android fixtures exercise the actual runtime; JVM fixtures test only decoded semantic contracts.
 */
internal object GuidedAndroidJson {
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private data class NumberToken(val raw: String)
    fun decode(raw: String): Any? {
        checkLexical(raw)
        JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            var remaining = 16384
            fun value(depth: Int): Any? {
                require(depth <= 32 && --remaining >= 0)
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        val result = linkedMapOf<String, Any?>()
                        while (reader.hasNext()) {
                            val name = reader.nextName(); guidedUtf8Size(name)
                            require(!result.containsKey(name))
                            result[name] = value(depth + 1)
                        }
                        reader.endObject(); result
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray(); val result = mutableListOf<Any?>()
                        while (reader.hasNext()) result.add(value(depth + 1))
                        reader.endArray(); result
                    }
                    JsonToken.STRING -> reader.nextString().also { guidedUtf8Size(it) }
                    JsonToken.NUMBER -> reader.nextString().let { token ->
                        require(number.matches(token)); token.toLongOrNull() ?: NumberToken(token)
                    }
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> { reader.nextNull(); null }
                    else -> throw IllegalArgumentException()
                }
            }
            val result = value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
    }

    internal fun checkLexical(raw: String) {
        var quoted = false; var i = 0
        while (i < raw.length) {
            val c = raw[i++]
            if (!quoted) {
                if (c == '"') quoted = true
                require(c != '\uFEFF' && c != ';')
                if (c in "tTfFnN") {
                    val start = i - 1
                    while (i < raw.length && (raw[i] in 'a'..'z' || raw[i] in 'A'..'Z')) i++
                    require(raw.substring(start, i) in setOf("true", "false", "null"))
                }
                continue
            }
            when {
                c == '"' -> quoted = false
                c.code < 32 -> throw IllegalArgumentException()
                c == '\\' -> {
                    require(i < raw.length)
                    when (raw[i++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> { require(i + 4 <= raw.length && raw.substring(i, i + 4).all { it in "0123456789abcdefABCDEF" }); i += 4 }
                        else -> throw IllegalArgumentException()
                    }
                }
            }
        }
        require(!quoted)
    }
}

/** Only fixed local copy crosses the error/UI boundary; response/error bodies never become labels. */
object GuidedExplanationCopy {
    private val messages = mapOf(
        "invalid_input" to ("当前输入无法用于解释。" to "The current input cannot be explained."),
        "stale_assessment" to ("本地结果已改变，请重新预览。" to "The local result changed. Preview again."),
        "payload_too_large" to ("解释载荷超过 16 KiB，未发送。" to "The explanation payload exceeds 16 KiB and was not sent."),
        "invalid_unicode" to ("解释数据包含无效字符编码。" to "The explanation data contains invalid Unicode."),
        "invalid_json" to ("模型返回的 JSON 格式无效。" to "The model returned invalid JSON."),
        "invalid_schema" to ("模型返回的解释结构无效。" to "The explanation structure is invalid."),
        "invalid_reference" to ("模型引用超出本次本地证据范围。" to "The model referenced IDs outside this local evidence."),
        "invalid_action" to ("模型没有保留本地主动作。" to "The model did not preserve the local primary action."),
        "text_limit" to ("模型解释超过文本或条目上限。" to "The explanation exceeds a text or item limit."),
        "capability_violation" to ("模型解释违反本地操作限制。" to "The explanation violates local action limits."),
        "response_too_large" to ("模型响应超过大小上限。" to "The model response exceeds the size limit."),
        "invalid_envelope" to ("模型响应封装无效。" to "The model response envelope is invalid."),
        "refused" to ("模型拒绝解释本次排查。" to "The model refused to explain this diagnosis."),
        "cancelled" to ("解释已停止；远端可能已收到请求。" to "Explanation stopped; the remote service may have received the request."),
        "empty_response" to ("模型返回了空解释。" to "The model returned an empty explanation."),
        "missing_key" to ("请先保存 API Key。" to "Save an API Key first."),
        "expired_key" to ("API Key 已过期，请重新保存。" to "The API Key expired. Save it again."),
        "unknown_provider" to ("当前模型服务或协议不受支持。" to "The current provider or protocol is unsupported."),
        "http_error" to ("模型服务请求失败。" to "The model service request failed."),
        "network_error" to ("无法完成在线解释，请检查连接。" to "The online explanation could not complete. Check the connection.")
    )
    fun reason(reasonId: String, language: AppLanguage): String {
        val value = messages[reasonId.removePrefix("guided.ai.")] ?: messages.getValue("invalid_schema")
        return if (language == AppLanguage.EN) value.second else value.first
    }
}
