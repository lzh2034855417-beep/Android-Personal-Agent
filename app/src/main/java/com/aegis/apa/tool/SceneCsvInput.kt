package com.aegis.apa.tool

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bound untrusted document input before allocating the complete CSV or decoding UTF-8. */
object SceneCsvInput {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun read(stream: InputStream): String {
        val buffer = ByteArray(8192)
        val bytes = ByteArrayOutputStream()
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "CSV 超过 2 MiB 限制。" }
            bytes.write(buffer, 0, count)
        }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }
}
