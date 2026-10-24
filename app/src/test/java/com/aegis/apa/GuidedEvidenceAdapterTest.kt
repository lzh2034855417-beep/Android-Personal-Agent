package com.aegis.apa

import com.aegis.apa.agent.GuidedEvidenceAdapter
import com.aegis.apa.agent.GuidedSummaryCodec
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedEvidenceAdapterTest {
    private fun ready(input: GuidedInput, answers: GuidedAnswers = GuidedAnswers(), id: String = "sample") =
        (GuidedEvidenceAdapter.adapt(input, id, answers) as GuidedSampleResult.Ready).sample

    // Catches filling report event time/identity from the current phone, dropping zero, or copying raw details.
    @Test fun adapterPreservesProvenanceAndBounds() {
        val report = GuidedDiagnosticFixtures.diagnostic().copy(
            apps = listOf(VerificationFixtures.snapshot().apps.single().copy(estimatedPowerMah = 0.0,
                foregroundPowerMah = Double.NaN, backgroundPowerMah = Double.POSITIVE_INFINITY)),
            system = SystemPowerEvidence(thermalStatus = 0, batteryDrainWindow = BatteryDrainWindowEvidence(3600000, 5000.0, 0.0, true)),
            sources = GuidedDiagnosticFixtures.diagnostic().sources +
                ("thermalservice" to DiagnosticSourceResult("thermalservice", DiagnosticSourceStatus.AVAILABLE, "RAW_PRIVATE")))
        val sample = ready(GuidedInput(report, GuidedDiagnosticFixtures.device(), null))
        val power = sample.evidence.single { it.kind == GuidedEvidenceKind.APP_TOTAL_POWER }
        assertEquals(GuidedValue.Number(0.0, "mah"), power.value)
        assertNull(power.reportedAt)
        assertEquals(report.sampledAtInstant, power.collectedAt)
        assertEquals(VerificationFixtures.window(), power.window)
        assertEquals(GuidedValue.Number(0.0, "celsius"), sample.evidence.single { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }.value)
        assertEquals(GuidedDiagnosticFixtures.now, sample.evidence.single { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }.collectedAt)
        assertEquals(GuidedValue.Code("0"), sample.evidence.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
        listOf(GuidedEvidenceKind.APP_FOREGROUND_POWER, GuidedEvidenceKind.APP_BACKGROUND_POWER).forEach { kind ->
            val item = sample.evidence.single { it.kind == kind }
            assertNull(item.value)
            assertEquals(EvidenceFieldStatus.NOT_PARSED, item.fieldStatus)
        }
        assertEquals(VerificationOwnership.UNKNOWN, sample.diagnosticDevice?.ownership)
        assertNull(sample.diagnosticDevice?.modelCode)
        assertTrue(sample.gaps.any { it.id == "report_device_unknown" })
        val encoded = GuidedSummaryCodec.encode(sample).toString(Charsets.UTF_8)
        listOf("RAW_PRIVATE", "LOCAL_DEVICE", "LOCAL_MODEL", "10123", "raw error must not persist").forEach {
            assertFalse(encoded.contains(it))
        }
    }

    // Catches selecting by input order/power or exposing an omitted group's metrics to downstream rules.
    @Test fun capsDistinctAppGroupsInFixedPackageOrderAndReportsOmissions() {
        val report = GuidedDiagnosticFixtures.diagnostic().copy(apps = (21 downTo 1).map {
            VerificationFixtures.snapshot().apps.single().copy(packageNames = listOf("app.%02d".format(it)), estimatedPowerMah = it.toDouble())
        })
        val sample = ready(GuidedInput(report, null, null))
        assertEquals(21, sample.sourceAppGroupCount)
        assertEquals(1, sample.omittedAppGroupCount)
        assertEquals((1..20).map { listOf("app.%02d".format(it)) }, sample.evidence.map { it.packages }.filter { it.isNotEmpty() }.distinct().sortedBy { it.single() })
        assertFalse(GuidedSummaryCodec.encode(sample).toString(Charsets.UTF_8).contains("app.21"))
        assertTrue(sample.gaps.any { it.id == "app_groups_omitted" })
    }

    // Catches bypassing the accepted identity gate and merging ambiguous duplicate source rows.
    @Test fun incompleteIdentityAndDuplicateGroupsDoNotBecomeAppFacts() {
        val base = GuidedDiagnosticFixtures.diagnostic()
        val partial = ready(GuidedInput(base.copy(evidenceCoverage = EvidenceCoverage(
            EvidenceField.entries.associateWith { if (it == EvidenceField.UID_PACKAGES) EvidenceFieldStatus.TRUNCATED else EvidenceFieldStatus.PARSED })), null, null))
        assertTrue(partial.evidence.filter { it.packages.isNotEmpty() }.all { it.value == null && it.fieldStatus == EvidenceFieldStatus.TRUNCATED })
        val duplicate = ready(GuidedInput(base.copy(apps = base.apps + base.apps), null, null))
        assertEquals(1, duplicate.sourceAppGroupCount)
        assertTrue(duplicate.evidence.filter { it.packages.isNotEmpty() }.all { it.value == null && it.fieldStatus == EvidenceFieldStatus.NOT_PARSED })
    }

    // Catches treating invalid observations as rates or swapping reversed user timestamps.
    @Test fun invalidObservationAndReversedAnswersRemainExplicitGaps() {
        val answers = GuidedAnswers(startedAt = GuidedDiagnosticFixtures.now, endedAt = GuidedDiagnosticFixtures.now.minusSeconds(1))
        val sample = ready(GuidedInput(null, null, GuidedDiagnosticFixtures.observation(BatteryObservationValidity.CONTINUITY_LOST)), answers)
        assertTrue(sample.evidence.isEmpty())
        assertTrue(sample.gaps.any { it.id == "observation_continuity_lost" })
        assertTrue(sample.gaps.any { it.id == "answer_time_range_reversed" })
        assertEquals(answers.startedAt, sample.answersAtConfirmation.startedAt)
        assertEquals(answers.endedAt, sample.answersAtConfirmation.endedAt)
        val valid = ready(GuidedInput(null, null, GuidedDiagnosticFixtures.observation()))
        assertEquals(GuidedValue.Number(2.0, "percent_per_hour"), valid.evidence.single().value)
        assertEquals(GuidedDiagnosticFixtures.now.minusSeconds(3600), valid.evidence.single().window?.startedAt)
        val nonfinite = ready(GuidedInput(null, null, GuidedDiagnosticFixtures.observation(rate = Double.NaN)))
        assertFalse(nonfinite.evidence.any { it.value != null })
    }

    // Catches codepoint truncation bugs, UTF-8 byte undercounting and an off-by-one gate.
    @Test fun rejectsOneByteOverButAllowsExactly48KiBUsingSameEncoder() {
        val report = GuidedDiagnosticFixtures.diagnostic().let { it.copy(verificationMetadata = it.verificationMetadata!!.copy(
            device = VerificationDeviceContext(null, null, null, "", VerificationOwnership.UNKNOWN))) }
        val input = GuidedInput(report, GuidedDiagnosticFixtures.device(), null)
        val answers = GuidedAnswers(note = "中😀".repeat(300) + "\uD800")
        val original = ready(input, answers)
        assertEquals(500, original.answersAtConfirmation.note.codePointCount(0, original.answersAtConfirmation.note.length))
        assertTrue(original.answersAtConfirmation.note.endsWith("😀"))
        val baseSize = GuidedSummaryCodec.encode(original).size
        val padding = "x".repeat(48 * 1024 - baseSize)
        fun paddedInput(pad: String) = input.copy(diagnostic = report.copy(verificationMetadata = report.verificationMetadata!!.copy(
            device = report.verificationMetadata.device!!.copy(buildVersion = pad))))
        val exact = ready(paddedInput(padding), answers)
        assertEquals(49152, GuidedSummaryCodec.encode(exact).size)
        assertEquals(GuidedSampleResult.Rejected("summary_too_large"), GuidedEvidenceAdapter.adapt(paddedInput(padding + "x"), "sample", answers))
        assertEquals("a�b", GuidedAnswers(note = "a\uD800b").note)
    }

    // Catches duplicate evidence IDs, reordering-dependent bytes and snapshots backed by caller collections.
    @Test fun confirmedSamplesAreDefensiveAndCodecRejectsDuplicateIds() {
        val packages = mutableListOf("com.example.app")
        val sources = GuidedDiagnosticFixtures.diagnostic().sources.toMutableMap()
        val apps = mutableListOf(VerificationFixtures.snapshot().apps.single().copy(packageNames = packages))
        val report = GuidedDiagnosticFixtures.diagnostic().copy(apps = apps, sources = sources)
        val sample = ready(GuidedInput(report, null, null))
        val before = GuidedSummaryCodec.encode(sample)
        packages += "private.omitted"
        apps.clear()
        sources.clear()
        assertArrayEquals(before, GuidedSummaryCodec.encode(sample))
        assertArrayEquals(before, GuidedSummaryCodec.encode(sample.copy(evidence = sample.evidence.reversed(), sources = sample.sources.toSortedMap(compareByDescending { it }))))
        try {
            GuidedSummaryCodec.encode(sample.copy(evidence = sample.evidence + sample.evidence.first()))
            fail("Duplicate IDs must be rejected")
        } catch (failure: GuidedSummaryException) { assertEquals("duplicate_evidence_id", failure.reasonId) }
        val mutableEvidence = sample.evidence.toMutableList()
        val mutableGaps = sample.gaps.toMutableList()
        val copied = sample.copy(evidence = mutableEvidence, gaps = mutableGaps)
        mutableEvidence.clear(); mutableGaps.clear()
        assertEquals(sample.evidence, copied.evidence)
        assertEquals(sample.gaps, copied.gaps)
        try { (copied.evidence as MutableList<GuidedEvidence>).clear(); fail("Read-only confirmed evidence") }
        catch (_: UnsupportedOperationException) { }
    }

    // Catches inventing default facts when no evidence exists and guessing meanings of unknown thermal codes.
    @Test fun emptyInputAndUnknownThermalStateStayUnknown() {
        assertTrue(ready(GuidedInput(null, null, null)).evidence.isEmpty())
        val report = GuidedDiagnosticFixtures.diagnostic().copy(system = SystemPowerEvidence(thermalStatus = 99),
            sources = mapOf("thermalservice" to DiagnosticSourceResult("thermalservice", DiagnosticSourceStatus.AVAILABLE)))
        val sample = ready(GuidedInput(report, GuidedDiagnosticFixtures.device(Double.NEGATIVE_INFINITY), null))
        assertNull(sample.evidence.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
        assertEquals(EvidenceFieldStatus.NOT_PARSED, sample.evidence.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.fieldStatus)
        assertNull(sample.evidence.single { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }.value)
    }

    // Catches retaining a numeric value whose parser coverage explicitly says it was not parsed.
    @Test fun unparsedFieldsRemainUnknownWithoutRejectingOtherAvailableEvidence() {
        val report = GuidedDiagnosticFixtures.diagnostic().copy(evidenceCoverage = EvidenceCoverage(
            EvidenceField.entries.associateWith {
                if (it == EvidenceField.WAKELOCK_TIME) EvidenceFieldStatus.NOT_PARSED else EvidenceFieldStatus.PARSED
            }))
        val result = GuidedEvidenceAdapter.adapt(GuidedInput(report, null, null), "sample", GuidedAnswers())
        assertTrue("Other parsed fields must remain usable: $result", result is GuidedSampleResult.Ready)
        val sample = (result as GuidedSampleResult.Ready).sample
        val wakelock = sample.evidence.single { it.kind == GuidedEvidenceKind.WAKELOCK_DURATION }
        assertNull(wakelock.value)
        assertEquals(EvidenceFieldStatus.NOT_PARSED, wakelock.fieldStatus)
        assertEquals(VerificationEstimate.UNKNOWN, wakelock.estimate)
        assertEquals(GuidedValue.Number(100.0, "mah"), sample.evidence.single {
            it.kind == GuidedEvidenceKind.APP_TOTAL_POWER
        }.value)
    }

    // Catches erasing source failure/ownership metadata when the shared projector returns null.
    @Test fun unavailableProjectionRetainsWhitelistedSourcesAndIndependentThermalTimes() {
        val report = GuidedDiagnosticFixtures.diagnostic().copy(
            sources = mapOf(
                "batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.PERMISSION_DENIED),
                "thermalservice" to DiagnosticSourceResult("thermalservice", DiagnosticSourceStatus.AVAILABLE),
                "private_source" to DiagnosticSourceResult("private_source", DiagnosticSourceStatus.AVAILABLE, "SECRET")),
            system = SystemPowerEvidence(thermalStatus = 2))
        val sample = ready(GuidedInput(report, GuidedDiagnosticFixtures.device(), null))
        assertTrue(sample.gaps.any { it.id == "diagnostic_evidence_unavailable" })
        assertEquals(DiagnosticSourceStatus.PERMISSION_DENIED, sample.sources["batterystats"])
        assertFalse(sample.sources.containsKey("private_source"))
        val thermal = sample.evidence.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }
        assertEquals(report.sampledAtInstant, thermal.collectedAt)
        assertNull(thermal.reportedAt)
        assertEquals(GuidedDiagnosticFixtures.now, sample.evidence.single {
            it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE
        }.collectedAt)
        assertEquals(VerificationOwnership.UNKNOWN, sample.diagnosticDevice?.ownership)
        assertNull(sample.diagnosticDevice?.modelCode)
        val empty = ready(GuidedInput(report.copy(system = SystemPowerEvidence(), sources = mapOf(
            "batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.TIMED_OUT))), null, null))
        assertTrue(empty.evidence.isEmpty())
        assertTrue(empty.gaps.any { it.id == "diagnostic_evidence_unavailable" })
        assertEquals(DiagnosticSourceStatus.TIMED_OUT, empty.sources["batterystats"])
    }

    // Catches allowing arbitrary sources, units, raw status strings, nonfinite values or forged group counts.
    @Test fun codecRejectsEvidenceOutsideTypedWhitelist() {
        val sample = ready(GuidedInput(null, GuidedDiagnosticFixtures.device(), null))
        val item = sample.evidence.first()
        val cases = listOf(
            item.copy(source = "raw_report"), item.copy(value = GuidedValue.Number(1.0, "cpu_celsius")),
            item.copy(value = GuidedValue.Number(Double.NaN, "celsius")),
            item.copy(kind = GuidedEvidenceKind.THERMAL_STATUS, source = "thermalservice", value = GuidedValue.Code("99")),
            item.copy(kind = GuidedEvidenceKind.CHARGING, value = GuidedValue.Code("raw private text")),
            item.copy(window = VerificationFixtures.window("other_source")))
        cases.forEach { bad ->
            try { GuidedSummaryCodec.encode(sample.copy(evidence = listOf(bad))); fail("Invalid evidence must be rejected") }
            catch (failure: GuidedSummaryException) { assertEquals("invalid_summary", failure.reasonId) }
        }
        try { GuidedSummaryCodec.encode(sample.copy(omittedAppGroupCount = 1)); fail("Omission count must be honest") }
        catch (failure: GuidedSummaryException) { assertEquals("invalid_summary", failure.reasonId) }
    }

    // Catches emitting unescaped quotes/backslashes and accidental surrogate repair outside the bounded note.
    @Test fun codecEscapesUnicodeWithoutChangingEvidenceOrLeakingArbitrarySources() {
        val sample = ready(GuidedInput(null, null, null), GuidedAnswers(note = "中😀\"\\\n"))
        val json = GuidedSummaryCodec.encode(sample).toString(Charsets.UTF_8)
        assertTrue(json.contains("中😀\\\"\\\\ "))
        assertFalse(json.contains("\\ud83d"))
        try { GuidedSummaryCodec.encode(sample.copy(id = "\uD800")); fail("Malformed IDs cannot silently collide") }
        catch (failure: GuidedSummaryException) { assertEquals("invalid_summary", failure.reasonId) }
    }
}
