package com.aegis.apa

import com.aegis.apa.model.CapabilityAccessFeedback
import com.aegis.apa.model.ShizukuAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityAccessFeedbackTest {
    @Test
    fun grantedUsageAccessDisablesTheAuthorizationEntry() {
        val feedback = CapabilityAccessFeedback(
            usageAccessGranted = true,
            shizukuAccessState = ShizukuAccessState.SERVICE_UNAVAILABLE,
            rootAuthorized = false
        )

        assertEquals("已获得使用情况访问", feedback.usageActionLabel)
        assertFalse(feedback.usageActionEnabled)
    }

    @Test
    fun runningShizukuRequestsPermissionUntilApaIsAuthorized() {
        val pending = CapabilityAccessFeedback(
            usageAccessGranted = false,
            shizukuAccessState = ShizukuAccessState.PERMISSION_REQUIRED,
            rootAuthorized = false
        )
        val granted = pending.copy(shizukuAccessState = ShizukuAccessState.AUTHORIZED)

        assertEquals("授权 APA 使用 Shizuku", pending.shizukuActionLabel)
        assertTrue(pending.shizukuActionEnabled)
        assertEquals("Shizuku 已授权", granted.shizukuActionLabel)
        assertFalse(granted.shizukuActionEnabled)
    }

    @Test
    fun installedButStoppedShizukuIsNotReportedAsAuthorized() {
        val feedback = CapabilityAccessFeedback(
            usageAccessGranted = false,
            shizukuAccessState = ShizukuAccessState.SERVICE_UNAVAILABLE,
            rootAuthorized = false
        )

        assertEquals("打开 Shizuku", feedback.shizukuActionLabel)
        assertTrue(feedback.shizukuActionEnabled)
    }

    @Test
    fun successfulRootEvidenceDisablesTheManagerEntry() {
        val feedback = CapabilityAccessFeedback(
            usageAccessGranted = false,
            shizukuAccessState = ShizukuAccessState.NOT_INSTALLED,
            rootAuthorized = true
        )

        assertEquals("Root 已授权", feedback.rootActionLabel)
        assertFalse(feedback.rootActionEnabled)
    }

    @Test
    fun rootRequiresSuccessfulEvidenceRatherThanOnlyANullError() {
        assertFalse(
            CapabilityAccessFeedback.hasRootEvidence(
                rootBatteryAttempted = false,
                rootBatteryError = null,
                profileUsedRoot = false,
                diagnosticHasSuccessfulCommand = false
            )
        )
        assertTrue(CapabilityAccessFeedback.hasRootEvidence(true, null, false, false))
        assertTrue(CapabilityAccessFeedback.hasRootEvidence(false, "Root 授权被拒绝", true, false))
        assertTrue(CapabilityAccessFeedback.hasRootEvidence(false, null, false, true))
        assertFalse(CapabilityAccessFeedback.hasRootEvidence(false, null, false, false))
        assertFalse(CapabilityAccessFeedback.hasRootEvidence(true, "Root 授权被拒绝", false, false))
    }

    @Test
    fun shizukuStateRequiresAConnectedBinderAndGrantedPermission() {
        assertEquals(
            ShizukuAccessState.NOT_INSTALLED,
            ShizukuAccessState.resolve(isInstalled = false, binderAlive = true, permissionGranted = true)
        )
        assertEquals(
            ShizukuAccessState.SERVICE_UNAVAILABLE,
            ShizukuAccessState.resolve(isInstalled = true, binderAlive = false, permissionGranted = true)
        )
        assertEquals(
            ShizukuAccessState.PERMISSION_REQUIRED,
            ShizukuAccessState.resolve(isInstalled = true, binderAlive = true, permissionGranted = false)
        )
        assertEquals(
            ShizukuAccessState.AUTHORIZED,
            ShizukuAccessState.resolve(isInstalled = true, binderAlive = true, permissionGranted = true)
        )
    }
}
