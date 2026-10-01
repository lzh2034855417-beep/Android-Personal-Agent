package com.aegis.apa

import com.aegis.apa.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ChatRequestControlTest {
    @Test fun duplicateMessagesHaveDistinctUiKeysWithoutChangingArchiveEquality() {
        val first = AgentConversationMessage(MessageRole.USER, "same")
        val second = AgentConversationMessage(MessageRole.USER, "same")
        assertEquals(first, second)
        assertNotEquals(first.displayId, second.displayId)
        val messages = listOf(first, second)
        assertEquals(second.displayId, messages.takeLast(1).first().displayId)
    }
    @Test fun sessionKeepsOnlyHundredRecentMessagesBeforeQueuingPersistence() {
        val state = ConversationMessageState()
        var saved: List<AgentConversationMessage> = emptyList()
        state.onChanged = { saved = it }
        state.value = (0..149).map { AgentConversationMessage(MessageRole.USER, "message $it") }
        assertEquals(100, state.value.size)
        assertEquals("message 50", state.value.first().content)
        assertEquals(state.value, saved)
    }
    @Test fun stopClosesActiveConnectionOnlyOnce() {
        val handle = CloudRequestCancellation()
        var closed = 0
        handle.register { closed++ }
        handle.cancel()
        handle.cancel()
        assertEquals(1, closed)
        assertThrows(CancellationException::class.java) { handle.ensureActive() }
    }

    @Test fun stopBeforeRegistrationPreventsANewRequest() {
        val handle = CloudRequestCancellation()
        handle.cancel()
        var closed = false
        assertThrows(CancellationException::class.java) { handle.register { closed = true } }
        assertTrue(closed)
    }

    @Test fun finishedRequestDoesNotRetainItsConnection() {
        val handle = CloudRequestCancellation()
        var closed = false
        handle.register { closed = true }
        handle.release()
        handle.cancel()
        assertFalse(closed)
    }

    @Test fun retryRemovesOnlyFailedTurnAndDoesNotDuplicateQuestion() {
        val earlier = AgentConversationMessage(MessageRole.ASSISTANT, "earlier")
        val question = AgentConversationMessage(MessageRole.USER, "question")
        val failed = listOf(earlier, question, AgentConversationMessage(MessageRole.ERROR, "network error"))
        assertEquals("question", AgentRetryPolicy.question(failed))
        val retried = AgentRetryPolicy.beforeRetry(failed) + question
        assertEquals(listOf(earlier, question), retried)
        assertNull(AgentRetryPolicy.question(retried + AgentConversationMessage(MessageRole.ASSISTANT, "done")))
        assertNull(AgentRetryPolicy.question(listOf(AgentConversationMessage(MessageRole.ERROR, "error"))))
    }
}
