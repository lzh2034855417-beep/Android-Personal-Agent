package com.aegis.apa.agent

object AgentPlainTextFormatter {
    fun format(text: String): String = text
        .replace("**", "")
        .replace("__", "")
}
