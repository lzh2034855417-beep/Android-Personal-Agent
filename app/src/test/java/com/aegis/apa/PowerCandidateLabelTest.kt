package com.aegis.apa

import com.aegis.apa.model.AdviceLevel
import com.aegis.apa.model.DiagnosticConfidence
import com.aegis.apa.model.RankedPowerCandidate
import com.aegis.apa.model.packageCopyText
import com.aegis.apa.model.packageSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerCandidateLabelTest {
    @Test
    fun sharedUidPackageSummaryKeepsEveryPackageVisibleAndCopyable() {
        val candidate = RankedPowerCandidate(
            uid = 1000,
            packageNames = listOf(
                "android",
                "com.miui.powerkeeper",
                "com.miui.securitycenter"
            ),
            displayNames = listOf("Android 系统", "电量和性能", "手机管家"),
            facts = listOf("事实"),
            confidence = DiagnosticConfidence.MEDIUM,
            maxAdviceLevel = AdviceLevel.OBSERVE,
            reason = "解释"
        )

        assertEquals(
            "包名：android, com.miui.powerkeeper, com.miui.securitycenter",
            candidate.packageSummary()
        )
        assertEquals("android\ncom.miui.powerkeeper\ncom.miui.securitycenter", candidate.packageCopyText())
    }
}
