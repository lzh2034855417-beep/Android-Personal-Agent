package com.aegis.apa.tool

import com.aegis.apa.model.DiagnosticSourceStatus
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class AllowedRootCommand(
    val source: String,
    val shell: String,
    val timeoutMillis: Long,
    val maxBytes: Int
) {
    BATTERYSTATS("batterystats", "dumpsys batterystats --charged", 8_000, 384 * 1024),
    POWER("power", "dumpsys power", 5_000, 96 * 1024),
    ALARM("alarm", "dumpsys alarm", 6_000, 192 * 1024),
    JOBS("jobscheduler", "dumpsys jobscheduler", 6_000, 192 * 1024),
    DEVICE_IDLE("deviceidle", "dumpsys deviceidle", 5_000, 96 * 1024),
    THERMAL("thermalservice", "dumpsys thermalservice", 5_000, 96 * 1024),
    WAKEUP("wakeup_sources", "cat /sys/kernel/debug/wakeup_sources 2>/dev/null || cat /d/wakeup_sources 2>/dev/null", 4_000, 128 * 1024),
    PACKAGES("packages", "pm list packages -U", 8_000, 256 * 1024)
}

data class RootCommandResult(
    val command: AllowedRootCommand,
    val status: DiagnosticSourceStatus,
    val output: String,
    val truncated: Boolean = false,
    val detail: String? = null
)

fun interface DiagnosticCommandRunner {
    fun run(command: AllowedRootCommand): RootCommandResult
}

object RootCommandRunner : DiagnosticCommandRunner {
    override fun run(command: AllowedRootCommand): RootCommandResult {
        val execution = execute(command.shell, command.timeoutMillis, command.maxBytes)
            ?: return RootCommandResult(
                command,
                DiagnosticSourceStatus.PERMISSION_DENIED,
                "",
                detail = "无法启动 Root 命令"
            )
        if (execution.timedOut) {
            return RootCommandResult(command, DiagnosticSourceStatus.TIMED_OUT, "", detail = "采集超时")
        }
        return classify(command, execution.exitCode, execution.output, execution.truncated)
    }

    internal fun runBatteryHealth(): FixedRootResult {
        val execution = execute(BATTERY_HEALTH_COMMAND, 8_000, 32 * 1024)
            ?: return FixedRootResult(DiagnosticSourceStatus.PERMISSION_DENIED, "", "无法启动 Root 命令")
        if (execution.timedOut) {
            return FixedRootResult(DiagnosticSourceStatus.TIMED_OUT, "", "Root 授权超时")
        }
        val classified = classifyStatus(execution.exitCode, execution.output, execution.truncated)
        return FixedRootResult(
            status = classified.first,
            output = if (classified.first == DiagnosticSourceStatus.AVAILABLE || classified.first == DiagnosticSourceStatus.TRUNCATED) execution.output else "",
            detail = classified.second
        )
    }

    internal fun classifyForTest(
        command: AllowedRootCommand,
        exitCode: Int,
        output: String,
        truncated: Boolean
    ): RootCommandResult = classify(command, exitCode, output, truncated)

    private fun classify(
        command: AllowedRootCommand,
        exitCode: Int,
        output: String,
        truncated: Boolean
    ): RootCommandResult {
        val (status, detail) = classifyStatus(exitCode, output, truncated, command)
        val safeOutput = if (status == DiagnosticSourceStatus.AVAILABLE || status == DiagnosticSourceStatus.TRUNCATED) output else ""
        return RootCommandResult(command, status, safeOutput, truncated, detail)
    }

    private fun classifyStatus(
        exitCode: Int,
        output: String,
        truncated: Boolean,
        command: AllowedRootCommand? = null
    ): Pair<DiagnosticSourceStatus, String?> {
        if (exitCode == 0) {
            return if (truncated) {
                DiagnosticSourceStatus.TRUNCATED to "输出过长，已截断"
            } else {
                DiagnosticSourceStatus.AVAILABLE to null
            }
        }
        val normalized = output.lowercase(Locale.ROOT)
        return when {
            command == AllowedRootCommand.WAKEUP && output.isBlank() ->
                DiagnosticSourceStatus.UNSUPPORTED to "设备不支持或不允许读取 wakeup_sources"
            "permission denied" in normalized || "not allowed" in normalized || "access denied" in normalized ->
                DiagnosticSourceStatus.PERMISSION_DENIED to "Root 授权被拒绝或权限不足"
            "not found" in normalized || "can't find service" in normalized || "no such file" in normalized ->
                DiagnosticSourceStatus.UNSUPPORTED to "设备不支持该数据源"
            else -> DiagnosticSourceStatus.PARSE_FAILED to "Root 命令执行失败"
        }
    }

    private fun execute(shell: String, timeoutMillis: Long, maxBytes: Int): Execution? {
        val process = runCatching {
            ProcessBuilder("su", "-c", shell)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        val capture = ByteArrayOutputStream(maxBytes.coerceAtMost(32 * 1024))
        val truncated = AtomicBoolean(false)
        val reader = thread(name = "root-output-reader", isDaemon = true) {
            runCatching {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val remaining = maxBytes - capture.size()
                        if (remaining > 0) capture.write(buffer, 0, count.coerceAtMost(remaining))
                        if (count > remaining) truncated.set(true)
                    }
                }
            }
        }

        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            reader.join(500)
            return Execution(-1, "", truncated.get(), timedOut = true)
        }
        reader.join(1_000)
        return Execution(
            exitCode = process.exitValue(),
            output = capture.toString(Charsets.UTF_8.name()),
            truncated = truncated.get(),
            timedOut = false
        )
    }

    private data class Execution(
        val exitCode: Int,
        val output: String,
        val truncated: Boolean,
        val timedOut: Boolean
    )

    internal data class FixedRootResult(
        val status: DiagnosticSourceStatus,
        val output: String,
        val detail: String?
    )

    private val BATTERY_HEALTH_COMMAND = """
        for key in charge_full_design charge_full cycle_count current_now voltage_now temp; do
          for path in /sys/class/power_supply/battery/${'$'}key /sys/class/power_supply/Battery/${'$'}key; do
            if [ -r "${'$'}path" ]; then
              echo "${'$'}key=${'$'}(cat "${'$'}path")"
              break
            fi
          done
        done
    """.trimIndent()
}
