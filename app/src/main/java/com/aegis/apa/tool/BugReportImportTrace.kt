package com.aegis.apa.tool

/** Temporary, per-import debug information; never persisted or sent to a model. */
class BugReportImportTrace(private val enabled: Boolean) {
    private val events = mutableListOf<String>()

    fun record(event: String) {
        if (enabled && events.size < 4) {
            events += event.take(240).map { if (it.isISOControl()) '?' else it }.joinToString("")
        }
    }

    fun appendTo(message: String): String =
        if (!enabled || events.isEmpty()) message
        else message + "\n\nImport diagnostic / 导入诊断（调试版）：\n" + events.joinToString("\n")
}
