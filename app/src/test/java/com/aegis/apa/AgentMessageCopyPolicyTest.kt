package com.aegis.apa

import com.aegis.apa.agent.AgentConversationMessage
import com.aegis.apa.agent.AgentMessageCopyPolicy
import com.aegis.apa.agent.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentMessageCopyPolicyTest {
    @Test fun assistantReplyCopiesItsCompleteText() {
        val message = AgentConversationMessage(
            role = MessageRole.ASSISTANT,
            content = "第一嫌疑：抖音\n依据：唤醒 180 次\n建议：限制后台活动"
        )

        assertEquals(
            "第一嫌疑：抖音\n依据：唤醒 180 次\n建议：限制后台活动",
            AgentMessageCopyPolicy.copyText(message)
        )
    }

    @Test fun userAndErrorMessagesDoNotOfferAiReplyCopying() {
        assertNull(
            AgentMessageCopyPolicy.copyText(
                AgentConversationMessage(MessageRole.USER, "为什么耗电")
            )
        )
        assertNull(
            AgentMessageCopyPolicy.copyText(
                AgentConversationMessage(MessageRole.ERROR, "请求失败")
            )
        )
    }
}
