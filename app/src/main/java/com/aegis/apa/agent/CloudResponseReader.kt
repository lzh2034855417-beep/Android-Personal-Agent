package com.aegis.apa.agent

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

object CloudResponseReader {
    const val MAX_BYTES = 1024 * 1024

    /** Reads at most the limit plus one byte, and closes the stream on every path. */
    fun read(stream: InputStream): String = stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count == -1) break
            if (output.size() + count > MAX_BYTES) throw AgentFailureException(AgentFailure.RESPONSE_TOO_LARGE)
            output.write(buffer, 0, count)
        }
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: CharacterCodingException) {
            throw AgentFailureException(AgentFailure.INVALID_RESPONSE)
        }
    }
}
