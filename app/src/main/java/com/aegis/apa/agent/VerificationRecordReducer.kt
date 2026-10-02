package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.time.Instant

object VerificationRecordReducer {
    fun recordChange(record: VerificationRecord, change: VerificationChange, now: Instant): VerificationRecord {
        requireOpen(record, "record_change")
        return record.copy(change = change, retest = null, state = VerificationState.CHANGE_RECORDED,
            closeReason = null, updatedAt = now, revision = record.revision + 1)
    }

    fun recordRetest(record: VerificationRecord, summary: DiagnosticVerificationSummary, now: Instant): VerificationRecord {
        if (record.state !in setOf(VerificationState.CHANGE_RECORDED, VerificationState.RETEST_SAVED) || record.change == null) {
            throw VerificationTransitionException(record.state, "record_retest")
        }
        return record.copy(retest = summary, state = VerificationState.RETEST_SAVED, closeReason = null,
            updatedAt = now, revision = record.revision + 1)
    }

    fun close(record: VerificationRecord, reason: VerificationCloseReason, now: Instant): VerificationRecord {
        requireOpen(record, "close")
        return record.copy(state = VerificationState.CLOSED, closeReason = reason, updatedAt = now, revision = record.revision + 1)
    }

    private fun requireOpen(record: VerificationRecord, operation: String) {
        if (record.state == VerificationState.CLOSED) throw VerificationTransitionException(record.state, operation)
    }
}
