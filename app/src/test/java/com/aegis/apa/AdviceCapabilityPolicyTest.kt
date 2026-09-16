package com.aegis.apa

import com.aegis.apa.agent.AdviceCapabilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdviceCapabilityPolicyTest {
    @Test
    fun levelZeroIsAlwaysAvailable() {
        assertTrue(AdviceCapabilityPolicy.allowsLevel("Level 0", false, false))
        assertEquals("Level 0", AdviceCapabilityPolicy.effectiveLevel("Level 0", false, false))
    }

    @Test
    fun levelOneRequiresVerifiedShizukuAuthorization() {
        assertFalse(AdviceCapabilityPolicy.allowsLevel("Level 1", false, true))
        assertEquals("Level 0", AdviceCapabilityPolicy.effectiveLevel("Level 1", false, true))
        assertTrue(AdviceCapabilityPolicy.allowsLevel("Level 1", true, false))
    }

    @Test
    fun levelTwoRequiresSuccessfulRootEvidence() {
        assertFalse(AdviceCapabilityPolicy.allowsLevel("Level 2", true, false))
        assertEquals("Level 0", AdviceCapabilityPolicy.effectiveLevel("Level 2", true, false))
        assertTrue(AdviceCapabilityPolicy.allowsLevel("Level 2", false, true))
        assertEquals("Level 2", AdviceCapabilityPolicy.effectiveLevel("Level 2", false, true))
    }

    @Test
    fun unknownLevelFallsBackToLevelZero() {
        assertEquals("Level 0", AdviceCapabilityPolicy.effectiveLevel("Level 9", true, true))
    }

    @Test
    fun missingRootReadIsNotMistakenForAReadWithoutAnError() {
        assertFalse(AdviceCapabilityPolicy.hasSuccessfulRootEvidence(false, null))
        assertFalse(AdviceCapabilityPolicy.hasSuccessfulRootEvidence(true, "Root 授权被拒绝"))
        assertTrue(AdviceCapabilityPolicy.hasSuccessfulRootEvidence(true, null))
    }
}
