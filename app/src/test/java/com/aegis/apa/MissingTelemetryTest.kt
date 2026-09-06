package com.aegis.apa

import com.aegis.apa.tool.BatteryPlugText
import com.aegis.apa.tool.UsageSummaryBuilder
import org.junit.Assert.assertNull
import org.junit.Test

class MissingTelemetryTest {
    @Test fun missingPlugFlagMustNotBecomeAllChargingSources() {
        assertNull(BatteryPlugText.fromFlags(-1))
    }

    @Test fun noUsageRecordsMustNotBecomeZeroMinutes() {
        assertNull(UsageSummaryBuilder.from(true, emptyMap(), emptyMap()).foregroundTimeMillis)
    }
}
