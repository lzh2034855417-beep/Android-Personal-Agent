package com.aegis.apa

import com.aegis.apa.agent.AgentPlainTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentPlainTextFormatterTest {
    @Test
    fun removesUnsupportedBoldMarkersWithoutRemovingTheirText() {
        val formatted = AgentPlainTextFormatter.format("本次也**未附带应用报告**。")

        assertEquals("本次也未附带应用报告。", formatted)
        assertFalse(formatted.contains("**"))
    }
}
