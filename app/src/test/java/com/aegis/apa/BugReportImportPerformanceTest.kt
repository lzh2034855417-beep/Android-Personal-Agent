package com.aegis.apa

import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportSectionExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Informational desktop benchmark, not a phone latency assertion. */
class BugReportImportPerformanceTest {
    @Test fun syntheticLargeReportKeepsRelevantSection() {
        val unrelated = "unrelated system log data 0123456789\n".repeat(450_000)
        val bytes = (unrelated + "------ DUMPSYS batterystats ------\nEstimated power use (mAh):\n  UID u0a123: 42.0\n").toByteArray()
        val samples = (0..6).map { iteration ->
            val started = System.nanoTime()
            val result = BugReportSectionExtractor.extract(bytes.inputStream(), "bugreport.txt") as BugReportReadResult.Success
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals("Estimated power use (mAh):\n  UID u0a123: 42.0", result.sections.single().output)
            if (iteration < 2) null else millis
        }.filterNotNull().sorted()
        println("IMPORT_BENCHMARK bytes=${bytes.size} median_ms=${samples[samples.size / 2]} samples_ms=$samples")
    }
}
