package com.aegis.apa.model

/** Limits Unicode code points, not grapheme clusters; repairs malformed UTF-16 before encoding. */
fun boundedUnicodeText(value: String, maxCodePoints: Int): String {
    require(maxCodePoints >= 0)
    val result = StringBuilder()
    var offset = 0
    var count = 0
    while (offset < value.length && count < maxCodePoints) {
        val codePoint = value.codePointAt(offset)
        offset += Character.charCount(codePoint)
        result.appendCodePoint(when {
            Character.isISOControl(codePoint) -> ' '.code
            codePoint in 0xD800..0xDFFF -> 0xFFFD
            else -> codePoint
        })
        count++
    }
    return result.toString()
}
