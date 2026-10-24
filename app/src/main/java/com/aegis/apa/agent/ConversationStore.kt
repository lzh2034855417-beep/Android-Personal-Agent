package com.aegis.apa.agent

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ConversationLoadResult(
    val messages: List<AgentConversationMessage>,
    val failed: Boolean = false,
    val expired: Boolean = false
)

/** Atomic, bounded, authenticated storage. Key retrieval errors never produce plaintext. */
class ConversationStore(
    private val file: File,
    private val key: () -> SecretKey,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Synchronized fun load(): ConversationLoadResult {
        if (!file.exists()) return ConversationLoadResult(emptyList())
        return try {
            require(file.length() in 29..(ConversationArchive.MAX_BYTES + 28L))
            val encrypted = file.inputStream().use { it.readNBytesCompat(ConversationArchive.MAX_BYTES + 29) }
            require(encrypted.size <= ConversationArchive.MAX_BYTES + 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            val messages = ConversationArchive.decode(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), clock())
            if (messages.isEmpty()) Files.deleteIfExists(file.toPath())
            ConversationLoadResult(messages, expired = messages.isEmpty())
        } catch (_: Exception) {
            ConversationLoadResult(emptyList(), failed = true)
        }
    }

    @Synchronized fun save(messages: List<AgentConversationMessage>) {
        if (messages.isEmpty()) { Files.deleteIfExists(file.toPath()); return }
        val plain = ConversationArchive.encode(messages, clock())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12)
        val encrypted = cipher.iv + cipher.doFinal(plain)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.pending")
        try {
            java.io.FileOutputStream(temporary).use { it.write(encrypted); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (count == -1) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
