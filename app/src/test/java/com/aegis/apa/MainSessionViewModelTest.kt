package com.aegis.apa

import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.MessageRole
import org.junit.Assert.*
import org.junit.Test

class MainSessionViewModelTest {
    @Test fun onlineInterruptionExplainsThatRetryIsANewRequest() {
        val session = MainSessionViewModel()
        session.beginAnalysis(online = true)
        session.interruptAnalysis()
        val notice = session.messages.value.single()
        assertTrue(notice.content.contains("在线请求可能仍在处理"))
        assertTrue(notice.content.contains("重试会再次发送"))
        assertNull(notice.cloudProvider)
    }

    @Test fun interruptedAnalysisKeepsConversationAndAllowsRetry() {
        val session = MainSessionViewModel()
        session.messages.value = listOf(AgentConversationMessage(MessageRole.USER, "question"))
        session.beginAnalysis()
        session.interruptAnalysis()
        assertFalse(session.analyzing.value)
        assertEquals("question", session.messages.value.first().content)
        assertEquals(MessageRole.ERROR, session.messages.value.last().role)
        session.interruptAnalysis()
        assertEquals(2, session.messages.value.size)
        session.beginAnalysis()
        assertTrue(session.analyzing.value)
    }

    @Test fun oldRequestCannotChangeRetriedConversationOrBusyState() {
        val session = MainSessionViewModel()
        val old = session.beginAnalysis()
        session.interruptAnalysis()
        val fresh = session.beginAnalysis()
        val before = session.messages.value
        session.interruptAnalysis(old)
        session.appendAnalysisMessage(old, AgentConversationMessage(MessageRole.ASSISTANT, "stale"))
        session.finishAnalysis(old)
        assertEquals(before, session.messages.value)
        assertTrue(session.analyzing.value)
        session.appendAnalysisMessage(fresh, AgentConversationMessage(MessageRole.ASSISTANT, "fresh"))
        session.finishAnalysis(fresh)
        assertEquals("fresh", session.messages.value.last().content)
        assertFalse(session.analyzing.value)
    }
}
