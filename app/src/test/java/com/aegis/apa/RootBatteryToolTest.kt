package com.aegis.apa

import com.aegis.apa.tool.RootBatteryTool
import com.aegis.apa.tool.RootCommandRunner
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class RootBatteryToolTest {
    @Test
    fun cancelledBatteryReadDoesNotAttemptRootAuthorization() {
        val failure = runCatching { RootBatteryTool.read { true } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun cancelledFixedRootCommandDoesNotAttemptRootAuthorization() {
        val failure = runCatching { RootCommandRunner.runBatteryHealth { true } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }
}
