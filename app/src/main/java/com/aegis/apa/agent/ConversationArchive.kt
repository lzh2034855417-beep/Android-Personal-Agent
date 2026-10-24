package com.aegis.apa.agent

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only displayed messages enter this schema, never attachment bodies or credentials. */
object ConversationArchive {
    const val MAX_BYTES = 512 * 1024
    private const val MAX_FIELD_BYTES = 64 * 1024
    private const val TTL = 7 * 86400000L

    fun encode(messages: List<AgentConversationMessage>, savedAt: Long): ByteArray {
        val records = messages.takeLast(100).map { message ->
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeText(message.role.name)
                out.writeText(message.content)
                out.writeText(message.attachedReportLabel)
                out.writeText(message.source)
                out.writeText(message.cloudProvider)
                out.writeText(message.cloudAdviceScope?.name)
            }
            bytes.toByteArray()
        }
        var size = 16
        val kept = records.asReversed().takeWhile { record ->
            (size + record.size <= MAX_BYTES).also { if (it) size += record.size }
        }.asReversed()
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(1)
                out.writeLong(savedAt)
                out.writeInt(kept.size)
                kept.forEach(out::write)
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray, now: Long): List<AgentConversationMessage> {
        require(bytes.size <= MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 1) { "Unsupported conversation schema" }
            val savedAt = input.readLong()
            require(savedAt >= 0 && savedAt <= now + 300000L)
            val count = input.readInt()
            require(count in 0..100)
            val messages = List(count) {
                AgentConversationMessage(
                    role = MessageRole.valueOf(requireNotNull(input.readText())),
                    content = requireNotNull(input.readText()),
                    attachedReportLabel = input.readText(),
                    source = input.readText(),
                    cloudProvider = input.readText(),
                    cloudAdviceScope = input.readText()?.let(CloudAdviceScope::valueOf)
                )
            }
            require(input.available() == 0)
            if (now - savedAt >= TTL) emptyList() else messages
        }
    }

    private fun DataOutputStream.writeText(text: String?) {
        if (text == null) { writeInt(-1); return }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "Conversation field is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String? {
        val length = readInt()
        if (length == -1) return null
        require(length in 0..MAX_FIELD_BYTES && length <= available())
        val bytes = ByteArray(length).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
}
