package com.aegis.apa

import com.aegis.apa.agent.AdviceCapabilityPolicy
import com.aegis.apa.agent.AgentCapabilityAccess
import com.aegis.apa.model.ShizukuAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdviceCapabilityPolicyTest {
    @Test
    fun pendingRootRecheckPreservesSelectionButDoesNotGrantUsableAccess() {
        val selected = AdviceCapabilityPolicy.selectionAfterAccessRefresh(
            "Level 2", false, false, rootAuthorizationPending = true
        )
        assertEquals("Level 2", selected)
        assertEquals("Level 0", AdviceCapabilityPolicy.effectiveLevel(selected, false, false))
        assertEquals("Level 2", AdviceCapabilityPolicy.selectionAfterAccessRefresh(
            selected, false, true, rootAuthorizationPending = false
        ))
        assertEquals("Level 0", AdviceCapabilityPolicy.selectionAfterAccessRefresh(
            selected, false, false, rootAuthorizationPending = false
        ))
    }

    @Test
    fun rootRecheckDoesNotKeepAnUnauthorizedShizukuSelectionOrAttachAnUnselectedReport() {
        assertEquals("Level 0", AdviceCapabilityPolicy.selectionAfterAccessRefresh(
            "Level 1", false, false, rootAuthorizationPending = true
        ))
        assertNull(AdviceCapabilityPolicy.selectionAfterAccessRefresh(
            null, false, false, rootAuthorizationPending = true
        ))
    }

    @Test
    fun agentAccessUsesTheCapabilityCentersVerifiedAuthorization() {
        val access = AgentCapabilityAccess.from(
            shizukuAccessState = ShizukuAccessState.AUTHORIZED,
            rootAuthorized = true
        )

        assertTrue(AdviceCapabilityPolicy.allowsLevel("Level 1", access.shizukuAuthorized, access.rootAuthorized))
        assertTrue(AdviceCapabilityPolicy.allowsLevel("Level 2", access.shizukuAuthorized, access.rootAuthorized))
    }

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
    fun noReportSelectionRemainsUnselected() {
        assertNull(AdviceCapabilityPolicy.effectiveLevel(null, true, true))
    }

    @Test
    fun noReportChatDoesNotRequestFreshDeviceSnapshot() {
        assertFalse(AdviceCapabilityPolicy.needsFreshSnapshot(null, false, false, false))
    }

    @Test
    fun snapshotIsRequestedOnlyForAttachmentsThatNeedCurrentDeviceData() {
        assertTrue(AdviceCapabilityPolicy.needsFreshSnapshot("Level 0", false, false, false))
        assertTrue(AdviceCapabilityPolicy.needsFreshSnapshot(null, true, false, false))
        assertTrue(AdviceCapabilityPolicy.needsFreshSnapshot(null, false, true, false))
    }

    @Test
    fun powerDiagnosticOnlyDoesNotRequestAnotherSnapshot() {
        assertFalse(AdviceCapabilityPolicy.needsFreshSnapshot(null, false, false, true))
    }

}
