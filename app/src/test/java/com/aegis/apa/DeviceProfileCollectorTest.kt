package com.aegis.apa

import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileCollector
import com.aegis.apa.tool.ProfileCommandResult
import com.aegis.apa.tool.ProfileCommandRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileCollectorTest {
    @Test
    fun successfulRootReadUsesRootProfile() {
        val runner = RecordingRunner(
            ProfileCommandResult(
                exitCode = 0,
                output = "MODEL=2509FPN0BC\nSOC=SM8850\nCPU_PRESENT=0-7"
            )
        )

        val profile = DeviceProfileCollector.read(preferRoot = true, runner = runner)

        assertEquals(DeviceProfileAccess.ROOT, profile.access)
        assertEquals("SM8850", profile.soc)
        assertNull(profile.error)
        assertEquals(1, runner.commands.size)
        assertEquals("su", runner.commands.single().first())
    }

    @Test
    fun deniedRootReadFallsBackToStandardProfile() {
        val runner = RecordingRunner(
            ProfileCommandResult(exitCode = 1, output = "Permission denied"),
            ProfileCommandResult(
                exitCode = 0,
                output = "MODEL=2509FPN0BC\nSOC=SM8850\nCPU_PRESENT=0-7"
            )
        )

        val profile = DeviceProfileCollector.read(preferRoot = true, runner = runner)

        assertEquals(DeviceProfileAccess.STANDARD, profile.access)
        assertEquals("SM8850", profile.soc)
        assertNotNull(profile.error)
        assertTrue(profile.error.orEmpty().contains("Root"))
        assertEquals(2, runner.commands.size)
        assertEquals("su", runner.commands[0].first())
        assertEquals("sh", runner.commands[1].first())
    }

    @Test
    fun failedStandardReadReturnsDisplayableError() {
        val runner = RecordingRunner(
            ProfileCommandResult(exitCode = 2, output = "command failed")
        )

        val profile = DeviceProfileCollector.read(preferRoot = false, runner = runner)

        assertEquals(DeviceProfileAccess.STANDARD, profile.access)
        assertNull(profile.model)
        assertTrue(profile.cpuPolicies.isEmpty())
        assertNotNull(profile.error)
        assertTrue(profile.toReportText().contains("采集提示"))
    }

    @Test
    fun rootTimeoutAlsoFallsBackToStandardProfile() {
        val runner = RecordingRunner(
            ProfileCommandResult(exitCode = null, output = "", timedOut = true),
            ProfileCommandResult(exitCode = 0, output = "MODEL=Fallback phone")
        )

        val profile = DeviceProfileCollector.read(preferRoot = true, runner = runner)

        assertEquals(DeviceProfileAccess.STANDARD, profile.access)
        assertEquals("Fallback phone", profile.model)
        assertTrue(profile.error.orEmpty().contains("超时"))
    }

    private class RecordingRunner(
        vararg results: ProfileCommandResult
    ) : ProfileCommandRunner {
        private val remaining = ArrayDeque(results.toList())
        val commands = mutableListOf<List<String>>()

        override fun run(command: List<String>): ProfileCommandResult {
            commands += command
            return remaining.removeFirst()
        }
    }
}
