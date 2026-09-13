package com.aegis.apa

import com.aegis.apa.model.EvidenceField
import com.aegis.apa.tool.JobSchedulerEvidenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobSchedulerEvidenceParserTest {
    @Test
    fun countsScheduledJobsPerUidWithoutCountingSummaryText() {
        val result = JobSchedulerEvidenceParser.parse(
            """
                JOB #u0a123/1: com.example.chat/.SyncJob
                JOB #u0a123/2: com.example.chat/.UploadJob
                JOB #10456/7: com.example.reader/.RefreshJob
                3 jobs total
            """.trimIndent()
        )

        assertEquals(2L, result.apps.getValue(10123).jobCount)
        assertEquals(1L, result.apps.getValue(10456).jobCount)
        assertTrue(EvidenceField.JOBS in result.parsedFields)
    }
}
