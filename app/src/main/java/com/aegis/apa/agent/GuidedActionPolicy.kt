package com.aegis.apa.agent

import com.aegis.apa.model.*

object GuidedActionPolicy {
    fun select(actions: List<GuidedAction>): GuidedAction = actions.minWithOrNull(
        compareBy<GuidedAction> { priority(it) }.thenBy { it.id }
    ) ?: GuidedAction("guided.end", GuidedActionKind.END, emptyList())

    private fun priority(action: GuidedAction): Int = when (action.kind) {
        GuidedActionKind.VIEW_SYSTEM_WARNING -> 0
        GuidedActionKind.ANSWER -> if (action.id.endsWith(".answer_conflict")) 1 else 2
        GuidedActionKind.IMPORT_REPORT, GuidedActionKind.COLLECT -> 2
        GuidedActionKind.OPEN_OBSERVATION, GuidedActionKind.OBSERVE_CONDITION,
        GuidedActionKind.OPEN_VERIFICATION -> 3
        GuidedActionKind.END -> 4
    }
}
