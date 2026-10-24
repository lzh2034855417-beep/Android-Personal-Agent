package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConversationArchiveTest {
    private val now = 1_000_000_000L
    private val turn = AgentConversationMessage(MessageRole.USER, "电池怎么样？", "L0", "test", "DeepSeek", CloudAdviceScope.LEVEL_0)

    @Test fun restoreCannotOvertakeQueuedClearWhilePreviousSaveCompletes() = runBlocking {
        val folder = Files.createTempDirectory("apa-chat-order-test").toFile()
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        try {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val persistence = ConversationPersistence(ConversationStore(File(folder, "chat"), { key }, { now }), Dispatchers.IO)
            persistence.save(listOf(turn)) {
                callbackStarted.countDown()
                check(releaseCallback.await(5, TimeUnit.SECONDS))
            }
            assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
            persistence.save(emptyList()) { }
            val loaded = CountDownLatch(1)
            val restore = async(Dispatchers.IO) { persistence.load().also { loaded.countDown() } }
            try {
                assertFalse("restore passed the queued clear", loaded.await(300, TimeUnit.MILLISECONDS))
            } finally { releaseCallback.countDown() }
            assertTrue(restore.await().messages.isEmpty())
        } finally { releaseCallback.countDown(); folder.deleteRecursively() }
    }

    @Test fun restoresProviderAndScopeSoOtherProvidersCannotReceiveHistory() {
        val restored = ConversationArchive.decode(ConversationArchive.encode(listOf(turn), now), now)
        assertEquals(listOf(turn), restored)
        assertTrue(CloudHistoryPolicy.select(restored, "Kimi").isEmpty())
        assertTrue(CloudHistoryPolicy.select(restored, "DeepSeek", adviceScope = CloudAdviceScope.GENERAL).isEmpty())
    }

    @Test fun retainsOnlyMostRecentHundredMessages() {
        val restored = ConversationArchive.decode(ConversationArchive.encode((0..149).map { turn.copy(content = "$it") }, now), now)
        assertEquals(100, restored.size)
        assertEquals("50", restored.first().content)
        assertEquals("149", restored.last().content)
    }

    @Test fun expiresAfterSevenDays() {
        assertTrue(ConversationArchive.decode(ConversationArchive.encode(listOf(turn), now), now + 7 * 86400000L).isEmpty())
    }

    @Test fun truncatedOrUnsupportedArchivesNeverRestorePartialHistory() {
        val bytes = ConversationArchive.encode(listOf(turn), now)
        assertThrows(Exception::class.java) { ConversationArchive.decode(bytes.copyOf(bytes.size - 1), now) }
        val unsupported = bytes.clone().apply { this[3] = 99 }
        assertThrows(Exception::class.java) { ConversationArchive.decode(unsupported, now) }
        assertThrows(Exception::class.java) { ConversationArchive.decode(ByteArray(ConversationArchive.MAX_BYTES + 1), now) }
    }

    @Test fun oversizedTextIsRejectedRatherThanWritingAnUnreadableArchive() {
        assertThrows(Exception::class.java) { ConversationArchive.encode(listOf(turn.copy(content = "x".repeat(ConversationArchive.MAX_BYTES))), now) }
    }

    @Test fun encryptsOnDiskAndClearSurvivesANewStoreInstance() {
        val folder = Files.createTempDirectory("apa-chat-test").toFile()
        try {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val store = ConversationStore(File(folder, "chat"), { key }, { now })
            store.save(listOf(turn))
            assertFalse(File(folder, "chat").readBytes().toString(Charsets.UTF_8).contains(turn.content))
            assertEquals(listOf(turn), ConversationStore(File(folder, "chat"), { key }, { now }).load().messages)
            store.save(emptyList())
            assertFalse(File(folder, "chat").exists())
            assertTrue(store.load().messages.isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun failedEncryptionPreservesPreviousHistoryAndTamperingIsReported() {
        val folder = Files.createTempDirectory("apa-chat-test").toFile()
        try {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val file = File(folder, "chat")
            val good = ConversationStore(file, { key }, { now })
            good.save(listOf(turn))
            val broken = ConversationStore(file, { error("keystore unavailable") }, { now })
            assertThrows(Exception::class.java) { broken.save(listOf(turn.copy(content = "new"))) }
            assertEquals(listOf(turn), good.load().messages)
            file.writeBytes(file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            assertTrue(good.load().messages.isEmpty())
            assertTrue(good.load().failed)
        } finally { folder.deleteRecursively() }
    }
}
