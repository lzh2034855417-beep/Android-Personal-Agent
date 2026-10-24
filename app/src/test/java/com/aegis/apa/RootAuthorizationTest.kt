package com.aegis.apa

import com.aegis.apa.model.RootAccessState
import com.aegis.apa.tool.ProcessOutput
import com.aegis.apa.tool.classifyRootAuthorization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAuthorizationTest {
    @Test fun onlySuccessfulCompleteRootIdentityConfirmsAuthorization() {
        assertEquals(RootAccessState.AUTHORIZED, classifyRootAuthorization(output("0\n")))
        assertEquals(RootAccessState.DENIED, classifyRootAuthorization(output("10440\n")))
        assertEquals(RootAccessState.FAILED, classifyRootAuthorization(output("")))
        assertEquals(RootAccessState.FAILED, classifyRootAuthorization(output("0\n10440")))
        assertEquals(RootAccessState.FAILED, classifyRootAuthorization(output("0", truncated = true)))
        assertEquals(RootAccessState.FAILED, classifyRootAuthorization(output("0", exitCode = 1)))
    }

    @Test fun deniedTimeoutAndMissingExecutableAreDifferentStates() {
        assertEquals(RootAccessState.DENIED, classifyRootAuthorization(output("su: permission denied", exitCode = 1)))
        assertEquals(RootAccessState.TIMED_OUT, classifyRootAuthorization(output("0", timedOut = true)))
        assertEquals(RootAccessState.UNAVAILABLE, classifyRootAuthorization(null))
    }

    @Test fun identityCheckDoesNotRequireAnyBatteryOrDiagnosticData() {
        val session = MainSessionViewModel()
        val token = session.beginRootAuthorizationCheck(allowRetry = false)!!
        assertTrue(session.completeRootAuthorizationCheck(token, RootAccessState.AUTHORIZED))
        assertEquals(RootAccessState.AUTHORIZED, session.rootAccessState.value)
        assertNull(session.rootBatteryInfo.value)
        assertNull(session.deviceProfile.value)
        assertNull(session.powerDiagnostic.value)
    }

    @Test fun concurrentRefreshDoesNotStartAnotherAuthorizationRequest() {
        val session = MainSessionViewModel()
        assertNotNull(session.beginRootAuthorizationCheck(allowRetry = false))
        assertNull(session.beginRootAuthorizationCheck(allowRetry = true))
        assertEquals(RootAccessState.CHECKING, session.rootAccessState.value)
    }

    @Test fun revokedAuthorizationReplacesSuccessAndIsNotAutomaticallyRetried() {
        val session = MainSessionViewModel()
        val first = session.beginRootAuthorizationCheck(false)!!
        session.completeRootAuthorizationCheck(first, RootAccessState.AUTHORIZED)
        val next = session.beginRootAuthorizationCheck(false)!!
        assertEquals(RootAccessState.CHECKING, session.rootAccessState.value)
        session.completeRootAuthorizationCheck(next, RootAccessState.DENIED)
        assertEquals(RootAccessState.DENIED, session.rootAccessState.value)
        assertNull(session.beginRootAuthorizationCheck(false))
        assertNotNull(session.beginRootAuthorizationCheck(true))
    }

    @Test fun timedOutCheckWaitsForExplicitRetryOrManagerReturn() {
        val session = MainSessionViewModel()
        val token = session.beginRootAuthorizationCheck(false)!!
        session.completeRootAuthorizationCheck(token, RootAccessState.TIMED_OUT)
        assertNull(session.beginRootAuthorizationCheck(false))
        session.requestRootAuthorizationRefresh()
        assertNotNull(session.beginRootAuthorizationCheck(false))
    }

    @Test fun oldResultsCannotRestoreAuthorizationAfterInterfaceDisappears() {
        val session = MainSessionViewModel()
        val stale = session.beginRootAuthorizationCheck(false)!!
        session.markRootUnavailable()
        assertFalse(session.completeRootAuthorizationCheck(stale, RootAccessState.AUTHORIZED))
        assertEquals(RootAccessState.UNAVAILABLE, session.rootAccessState.value)
        val fresh = session.beginRootAuthorizationCheck(false)!!
        assertFalse(session.completeRootAuthorizationCheck(stale, RootAccessState.AUTHORIZED))
        assertTrue(session.completeRootAuthorizationCheck(fresh, RootAccessState.AUTHORIZED))
    }

    @Test fun cancellationLeavesARecheckableUnknownState() {
        val session = MainSessionViewModel()
        val cancelled = session.beginRootAuthorizationCheck(false)!!
        session.cancelRootAuthorizationCheck(cancelled)
        assertEquals(RootAccessState.UNKNOWN, session.rootAccessState.value)
        assertFalse(session.completeRootAuthorizationCheck(cancelled, RootAccessState.AUTHORIZED))
        assertNotNull(session.beginRootAuthorizationCheck(false))
    }

    private fun output(
        text: String,
        exitCode: Int = 0,
        truncated: Boolean = false,
        timedOut: Boolean = false
    ) = ProcessOutput(exitCode, text, truncated, timedOut)
}
