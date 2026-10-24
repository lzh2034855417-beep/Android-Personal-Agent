package com.aegis.apa

import java.time.Instant

/** The raw timestamp draft remains in the form; only a parsed bound enters the answers. */
internal fun editGuidedTimeAnswer(draft: String, onEdit: (Instant?) -> Unit) {
    val parsed = runCatching { Instant.parse(draft.trim()) }.getOrNull()
    onEdit(parsed)
}
