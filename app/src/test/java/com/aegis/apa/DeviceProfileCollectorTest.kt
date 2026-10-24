package com.aegis.apa

import com.aegis.apa.tool.DeviceProfileAccess
import com.aegis.apa.tool.DeviceProfileCollector
import com.aegis.apa.tool.ProfileCommandResult
import com.aegis.apa.tool.ProfileCommandRunner
import com.aegis.apa.localization.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class DeviceProfileCollectorTest {
    @Test
    fun hardwareOnlyReadDoesNotCollectCpuSchedulingOrBatteryDiagnostics() {
        val runner = RecordingRunner(ProfileCommandResult(0,
            "DDR_VENDOR_HEX=000001ce\nDDR_TYPE_HEX=00000008\nSTORAGE=SKhynix|HN8T271EJKX152"))
        val hardware = DeviceProfileCollector.readHardware(false, runner)
        assertEquals("三星 (Samsung)", hardware?.ramVendor)
        val script = runner.commands.single().last()
        assertTrue(script.contains("ddr_manufacturer_id"))
        assertTrue(!script.contains("cpufreq") && !script.contains("thermal_zone"))
        assertTrue(!script.contains("dumpsys") && !script.contains("batterystats"))
    }

    @Test
    fun schedulingReadDoesNotReadSupplierNodes() {
        val runner = RecordingRunner(ProfileCommandResult(0, "MODEL=Phone\nSOC=SM8850\nCPU_PRESENT=0-7"))
        val profile = DeviceProfileCollector.readScheduling(false, runner)
        assertEquals("SM8850", profile.soc)
        val script = runner.commands.single().last()
        assertTrue(script.contains("cpufreq"))
        assertTrue(!script.contains("ddr_manufacturer_id") && !script.contains("/sys/block/"))
    }

    @Test
    fun cancelledRootFailureNeverStartsStandardFallback() {
        var cancelled = false
        var calls = 0
        val runner = ProfileCommandRunner {
            calls++
            cancelled = true
            ProfileCommandResult(1, "Permission denied")
        }
        val failure = runCatching {
            DeviceProfileCollector.read(true, runner) { cancelled }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, calls)
    }

    @Test
    fun truncatedRootPrefixRemainsUsableAndWarningAppearsInBothLanguages() {
        val runner = RecordingRunner(ProfileCommandResult(0, "MODEL=Phone\n", truncated = true))
        val profile = DeviceProfileCollector.read(true, runner)
        assertEquals("Phone", profile.model)
        assertEquals(DeviceProfileAccess.ROOT, profile.access)
        assertTrue(profile.toReportText().contains("截断"))
        assertTrue(profile.toReportText(AppLanguage.EN).contains("truncated"))
        assertEquals(1, runner.commands.size)
    }

    @Test
    fun truncatedStandardPrefixDoesNotExposeUnexpectedProperties() {
        val runner = RecordingRunner(ProfileCommandResult(
            0, "MODEL=Phone\nSERIAL=private-id\n", truncated = true
        ))
        val profile = DeviceProfileCollector.read(false, runner)
        assertEquals("Phone", profile.model)
        assertTrue(profile.error.orEmpty().contains("截断"))
        assertTrue(!profile.toReportText().contains("private-id"))
    }

    @Test
    fun interruptedCollectionDoesNotStartAnyCommand() {
        var calls = 0
        val runner = ProfileCommandRunner {
            calls++
            ProfileCommandResult(0, "MODEL=Should not read")
        }
        Thread.currentThread().interrupt()
        val failure = try {
            runCatching { DeviceProfileCollector.read(true, runner) }.exceptionOrNull()
        } finally {
            Thread.interrupted()
        }
        assertTrue(failure is CancellationException)
        assertEquals(0, calls)
    }

    @Test
    fun interruptionAfterRootFailureDoesNotFallBack() {
        var calls = 0
        val runner = ProfileCommandRunner {
            calls++
            Thread.currentThread().interrupt()
            ProfileCommandResult(1, "Permission denied")
        }
        val failure = try {
            runCatching { DeviceProfileCollector.read(true, runner) }.exceptionOrNull()
        } finally {
            Thread.interrupted()
        }
        assertTrue(failure is CancellationException)
        assertEquals(1, calls)
    }

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
