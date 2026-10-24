package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.tool.PowerDiagnosticPipeline
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import javax.crypto.KeyGenerator

/** Synthetic raw reports run the real parser, adapter, both rules and payload builder.
 * Explicit-window metadata below is deliberately synthetic: imports do not supply these bounds.
 */
class GuidedDiagnosticPipelineTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val answers = GuidedAnswers(startedAt = now.minusSeconds(3600), endedAt = now,
        charging = VerificationConfirmation.NO, rebooted = VerificationConfirmation.NO,
        mostlyIdle = VerificationConfirmation.YES, stillHot = VerificationConfirmation.YES,
        activity = GuidedActivity.GAME, reviewed = true)

    // Catches a later unknown statistics section replacing actual zero or its duration.
    @Test fun actualZeroAndSelectedWindowSurviveBothRulesAndPayloads() {
        val report = parse(battery = battery("0") + """

            Statistics for vendor scope:
            Time on battery: 30m (100%) realtime
            Estimated power use (mAh):
              Capacity: 5000, Computed drain: 999, actual drain: 999
              UID u0a123: 999 fg: 1 (1m) bg: 998 (29m)
            Uid u0a123:
              Wake lock upload: 30m partial (1 times) realtime
        """.trimIndent())
        val sample = adapt(report)
        val overall = sample.evidence.single { it.kind == GuidedEvidenceKind.OVERALL_POWER }
        assertEquals(GuidedValue.Number(0.0, "mah"), overall.value)
        assertEquals(VerificationEstimate.ACTUAL, overall.estimate)
        assertEquals(3_600_000L, overall.window!!.batteryDurationMillis)
        assertEquals(VerificationMetricScope.SINCE_LAST_CHARGE, overall.window.scope)
        assertNull(overall.window.startedAt); assertNull(overall.window.endedAt)
        assertEquals(GuidedValue.Number(900_000.0, "millis"), sample.evidence.single {
            it.kind == GuidedEvidenceKind.WAKELOCK_DURATION }.value)
        for ((assessment, payload) in both(sample)) {
            assertEquals(overall, assessment.facts.single { it.id == overall.id })
            assertTrue(payload.userPrompt.contains("\"number\":0.0,\"unit\":\"mah\""))
            assertTrue(payload.userPrompt.contains("\"startedAt\":null,\"endedAt\":null"))
            assertFalse(payload.userPrompt.contains("999.0"))
        }
    }

    // Catches an ambiguous supported section being silently chosen and promoted into usable power.
    @Test fun duplicateSupportedSectionsRemainUnusableThroughBothRulesAndPayloads() {
        val sample = adapt(parse(battery = battery("0") + "\n" + battery("555")))
        assertTrue(sample.gaps.any { it.id == "diagnostic_evidence_unavailable" })
        assertTrue(sample.evidence.none { it.kind == GuidedEvidenceKind.OVERALL_POWER && it.value != null })
        for ((assessment, payload) in both(sample)) {
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, assessment.primaryAction.kind)
            assertFalse(payload.userPrompt.contains("555.0"))
            assertTrue(payload.userPrompt.contains("diagnostic_evidence_unavailable"))
        }
    }

    // Catches downstream repair of incomplete identity by an otherwise strong numeric source.
    @Test fun truncatedAndFailedPackageMappingsCannotAuthorizeSingleAppVerification() {
        for ((status, truncated, expected) in listOf(
            Triple(DiagnosticSourceStatus.AVAILABLE, true, EvidenceFieldStatus.TRUNCATED),
            Triple(DiagnosticSourceStatus.PARSE_FAILED, false, EvidenceFieldStatus.SOURCE_UNAVAILABLE))) {
            val report = parse(packageStatus = status, packagesTruncated = truncated, source = DiagnosticInputSource.ROOT)
            val sample = adapt(report)
            val appFacts = sample.evidence.filter { it.packages.isNotEmpty() }
            assertTrue(appFacts.isNotEmpty())
            assertTrue(appFacts.filter { it.source == "batterystats" }.all { it.fieldStatus == expected && it.value == null })
            assertTrue(appFacts.all { it.value == null })
            assertTrue(sample.verificationEligibleAppGroups.isEmpty())
            for ((assessment, payload) in both(sample)) {
                assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, assessment.primaryAction.kind)
                assertTrue(payload.userPrompt.contains(if (truncated) "truncated" else "source_unavailable"))
                assertNull(target(sample, assessment))
            }
        }
    }

    // Catches splitting shared UID power, duplicating it per package or granting a one-app action.
    @Test fun completeSharedIdentityRemainsAnUndividedGroupInBothPayloads() {
        val sample = adapt(parse(packages = "package:com.example.b uid:10123\npackage:com.example.a uid:10123",
            source = DiagnosticInputSource.ROOT))
        val appFacts = sample.evidence.filter { it.packages.isNotEmpty() }
        assertTrue(appFacts.all { it.packages == listOf("com.example.a", "com.example.b") })
        assertEquals(1, appFacts.count { it.kind == GuidedEvidenceKind.APP_TOTAL_POWER })
        assertEquals(GuidedValue.Number(300.0, "mah"), appFacts.single {
            it.kind == GuidedEvidenceKind.APP_TOTAL_POWER }.value)
        assertTrue(sample.verificationEligibleAppGroups.isEmpty())
        for ((assessment, payload) in both(sample)) {
            assertNull(target(sample, assessment))
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, assessment.primaryAction.kind)
            assertTrue(payload.userPrompt.contains("\"packages\":[\"com.example.a\",\"com.example.b\"]"))
        }
    }

    // Catches policy allowance bypassing unknown production wall-clock/charging windows.
    @Test fun localCollectionAllowanceDoesNotRepairUnknownProductionWindow() {
        val report = parse(source = DiagnosticInputSource.ROOT)
        val sample = adapt(report)
        assertEquals(listOf(listOf("com.example.a")), sample.verificationEligibleAppGroups)
        assertEquals(VerificationOwnership.LOCAL_COLLECTION, sample.diagnosticDevice!!.ownership)
        val idle = IdleDrainRuleSet.evaluate(answers, listOf(sample))
        assertEquals("idle.observe_condition", idle.primaryAction.id)
        assertTrue(idle.gaps.any { it.id == "idle_window_unknown" })
        assertNull(target(sample, idle))
        for ((_, payload) in both(sample)) {
            assertTrue(payload.userPrompt.contains("\"startedAt\":null,\"endedAt\":null"))
            assertNotEquals("idle.verify_background", payload.actionId)
        }
    }

    // Catches ownership/answer gates being lost after adaptation, or payloads accepting invented actions.
    @Test fun syntheticExplicitWindowAllowsOnlyCurrentOwnedUnchangedLocalVerification() {
        val root = explicit(parse(source = DiagnosticInputSource.ROOT))
        val sample = adapt(root)
        val idle = IdleDrainRuleSet.evaluate(answers, listOf(sample))
        assertEquals("idle.verify_background", idle.primaryAction.id)
        assertEquals(VerificationTarget.AppGroup(listOf("com.example.a")), target(sample, idle))
        val state = state(sample, GuidedScenario.IDLE_DRAIN, idle)
        assertSame(root, guidedVerificationSource(state, state.session!!.key, idle.primaryAction.id, root))
        assertNull(guidedVerificationSource(state, state.session.key, idle.primaryAction.id,
            root.copy(sampledAtInstant = now.plusSeconds(1))))
        val imported = adapt(explicit(parse()))
        val importedIdle = IdleDrainRuleSet.evaluate(answers, listOf(imported))
        assertTrue(importedIdle.gaps.any { it.id == "report_device_unknown" })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, importedIdle.primaryAction.kind)
        val changed = answers.copy(activity = GuidedActivity.VIDEO)
        val stale = IdleDrainRuleSet.evaluate(changed, listOf(sample))
        assertTrue(stale.gaps.any { it.id == "idle_answers_changed" })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, stale.primaryAction.kind)
        val session = state.session.copy(answers = changed, revision = 1, answerRevision = 1)
        assertEquals(GuidedPayloadResult.Rejected("guided.ai.stale_assessment"),
            GuidedExplanationPayload.build(session, idle, GuidedExplanationOptions(), AppLanguage.EN))
        for ((assessment, payload) in both(sample)) {
            assertEquals(assessment.primaryAction.id, payload.actionId)
            val forged = GuidedAssessment(assessment.facts, assessment.candidates, assessment.gaps,
                GuidedAction("model.override", GuidedActionKind.COLLECT, emptyList()))
            assertEquals(GuidedPayloadResult.Rejected("guided.ai.stale_assessment"), GuidedExplanationPayload.build(
                state(sample, if (assessment.primaryAction.id.startsWith("idle.")) GuidedScenario.IDLE_DRAIN else GuidedScenario.USAGE_HEAT,
                    assessment).session!!, forged, GuidedExplanationOptions(), AppLanguage.EN))
        }
    }

    // Catches reported severe-state priority being replaced by temperature magnitude or an unanswered form.
    @Test fun parsedThermalSeverityControlsOnlyTheHeatLocalAction() {
        val unreviewed = answers.copy(reviewed = false)
        val severe = adapt(parse(thermal = "Current Thermal Status: 3"), unreviewed, GuidedDiagnosticFixtures.device(0.0))
        val heat = UsageHeatRuleSet.evaluate(unreviewed, listOf(severe))
        assertEquals("heat.view_system_warning", heat.primaryAction.id)
        val idle = IdleDrainRuleSet.evaluate(unreviewed, listOf(severe))
        assertEquals("idle.answer", idle.primaryAction.id)
        for ((_, payload) in both(severe, unreviewed)) assertTrue(payload.actionId in setOf("idle.answer", "heat.view_system_warning"))
        val unknown = adapt(parse(thermal = "Current Thermal Status: 99"), device = GuidedDiagnosticFixtures.device(1000.0))
        val unknownHeat = UsageHeatRuleSet.evaluate(answers, listOf(unknown))
        assertNull(unknownHeat.facts.single { it.kind == GuidedEvidenceKind.THERMAL_STATUS }.value)
        assertNotEquals(GuidedActionKind.VIEW_SYSTEM_WARNING, unknownHeat.primaryAction.kind)
        both(unknown)
    }

    // Catches raw report/header/label/UID/reset leakage and default-on current or confirmed notes.
    @Test fun importedPrivateTextAndIdentifiersCannotEnterEitherDefaultPayload() {
        val noted = answers.copy(note = "PRIVATE_NOTE_中😀")
        val report = parse().let { it.copy(verificationMetadata = it.verificationMetadata!!.copy(
            device = VerificationDeviceContext("PRIVATE_MANUFACTURER", "PRIVATE_MODEL", 36, "PRIVATE_BUILD", VerificationOwnership.UNKNOWN),
            windowsBySource = it.verificationMetadata.windowsBySource.mapValues { (_, w) -> w.copy(resetId = "PRIVATE_RESET") })) }
        val sample = adapt(report, noted, GuidedDiagnosticFixtures.device())
        for (scenario in GuidedScenario.entries) {
            val session = state(sample, scenario, evaluate(scenario, noted, listOf(sample)), noted).session!!
            val assessment = evaluate(scenario, noted, listOf(sample))
            for (language in AppLanguage.entries) {
                val payload = ready(GuidedExplanationPayload.build(session, assessment, GuidedExplanationOptions(), language))
                for (secret in listOf("PRIVATE_NOTE", "PRIVATE_RAW", "PRIVATE_LABEL", "PRIVATE_MANUFACTURER",
                    "PRIVATE_MODEL", "PRIVATE_BUILD", "PRIVATE_RESET", "LOCAL_DEVICE", "10123", "history", "apiKey"))
                    assertFalse(secret, payload.userPrompt.contains(secret))
                assertTrue(payload.userPrompt.contains("com.example.a"))
                assertTrue(payload.userPrompt.contains(now.toString()))
                assertTrue(payload.utf8Bytes <= 16384)
                val optedIn = ready(GuidedExplanationPayload.build(session, assessment, GuidedExplanationOptions(true), language))
                assertTrue(optedIn.userPrompt.contains("PRIVATE_NOTE_中😀"))
                assertFalse(optedIn.systemPrompt.contains("PRIVATE_NOTE"))
            }
        }
    }

    // Catches temperature becoming a power metric, heat-effect delta, or a fabricated comparison success.
    @Test fun twoHeatReadingsStayDescriptiveAndNeverBecomePowerComparisonMetrics() {
        val report = parse()
        val first = adapt(report, device = GuidedDiagnosticFixtures.device(45.0))
        val secondReport = report.copy(sampledAtInstant = now.plusSeconds(120))
        val second = adapt(parse(battery = "", packages = "").copy(sampledAtInstant = now.plusSeconds(120)),
            device = GuidedDiagnosticFixtures.device(35.0).copy(sampledAtInstant = now.plusSeconds(120)), id = "second")
        val heat = UsageHeatRuleSet.evaluate(answers, listOf(first, second))
        assertEquals(listOf(45.0, 35.0), heat.facts.filter { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }
            .map { (it.value as GuidedValue.Number).value })
        assertTrue(heat.gaps.any { it.id == "heat_event_unlinked" })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, heat.primaryAction.kind)
        val payload = ready(GuidedExplanationPayload.build(state(first, GuidedScenario.USAGE_HEAT, heat).session!!.copy(
            samples = listOf(first, second)), heat, GuidedExplanationOptions(), AppLanguage.EN))
        assertEquals(heat.primaryAction.id, payload.actionId)
        val fullSecond = adapt(secondReport, device = GuidedDiagnosticFixtures.device(35.0), id = "full-second")
        val fullHeat = UsageHeatRuleSet.evaluate(answers, listOf(first, fullSecond))
        assertEquals(GuidedPayloadResult.Rejected("guided.ai.payload_too_large"), GuidedExplanationPayload.build(
            state(first, GuidedScenario.USAGE_HEAT, fullHeat).session!!.copy(samples = listOf(first, fullSecond)),
            fullHeat, GuidedExplanationOptions(), AppLanguage.EN))
        assertEquals(first.evidence.size + fullSecond.evidence.size, fullHeat.facts.size)
        val baseline = project(report)
        val retest = project(secondReport)
        assertTrue(baseline.metrics.none { it.unit.wireId == "celsius" })
        assertTrue(retest.metrics.none { it.unit.wireId == "celsius" })
        val compared = VerificationComparisonEngine.compare(VerificationFixtures.record().copy(baseline = baseline, retest = retest))
        assertTrue(compared.metrics.all { it.delta == null && it.relativeChange == null })
    }

    // Catches a retest callback or failed first save inventing a persisted record/success link.
    @Test fun missingVerificationRecordAndFailedBaselineCannotFabricateSavedSuccess() {
        val report = parse()
        both(adapt(report))
        val summary = project(report)
        val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        val store = VerificationStore(File(folder.root, "verification.enc"), { key }, { now })
        val controller = VerificationController(store, { now }, Executor { it.run() }, {})
        controller.recordRetest("missing-record", 0, summary)
        controller.select("missing-record")
        assertTrue(controller.state.records.isEmpty())
        assertNull(controller.state.selectedId); assertNull(controller.state.unsavedRecord)
        assertTrue(File(folder.root, "verification.enc.pending").mkdir())
        controller.createBaseline("synthetic import", VerificationTarget.Overall, summary)
        assertEquals(VerificationStorageFailure.WRITE_FAILED, controller.state.storageFailure)
        assertNotNull(controller.state.unsavedRecord)
        assertTrue(controller.state.records.none { it.id == controller.state.selectedId })
        assertTrue(store.load().records.isEmpty())
        val owner = VerificationExplanationOwner(Executor { it.run() })
        owner.updateContext(controller.state.records.firstOrNull { it.id == controller.state.selectedId }, null, AppLanguage.EN)
        owner.preview()
        assertNull(owner.state.payload); assertFalse(owner.state.preview)
    }

    private fun battery(actual: String = "350") = """
        Statistics since last charge:
        Time on battery: 1h (100%) realtime
        Estimated power use (mAh):
          Capacity: 5000, Computed drain: 300, actual drain: $actual
          UID u0a123: 300 fg: 60 (10m) bg: 240 (50m)
        Uid u0a123:
          Wake lock sync: 15m partial (1 times) realtime
    """.trimIndent()

    private fun parse(battery: String = battery(), packages: String = "package:com.example.a uid:10123",
        packageStatus: DiagnosticSourceStatus = DiagnosticSourceStatus.AVAILABLE, packagesTruncated: Boolean = false,
        source: DiagnosticInputSource = DiagnosticInputSource.BUGREPORT, thermal: String = "Current Thermal Status: 2"): PowerDiagnosticSnapshot {
        val raw = "DUMP OF SERVICE package:\n$packages\nDUMP OF SERVICE batterystats:\n$battery\n" +
            "PRIVATE_RAW\nDUMP OF SERVICE thermalservice:\n$thermal"
        val read = BugReportSectionExtractor.extract(raw.byteInputStream(), "synthetic.txt") as BugReportReadResult.Success
        val sections = read.sections.map { if (it.source == "packages") it.copy(status = packageStatus, truncated = packagesTruncated) else it }
        return PowerDiagnosticPipeline.analyze(sections, source, now, packageLabelResolver = { "PRIVATE_LABEL" })
    }

    private fun explicit(report: PowerDiagnosticSnapshot): PowerDiagnosticSnapshot = report.copy(
        verificationMetadata = report.verificationMetadata!!.copy(windowsBySource = mapOf("batterystats" to MetricWindow(
            "batterystats", VerificationMetricScope.EXPLICIT_WINDOW, answers.startedAt, answers.endedAt,
            3_600_000, null, VerificationConfirmation.NO))))

    private fun adapt(report: PowerDiagnosticSnapshot, declaration: GuidedAnswers = answers, device: DeviceSnapshot? = null,
        id: String = "sample"): GuidedSample {
        val result = GuidedEvidenceAdapter.adapt(GuidedInput(report, device, null), id, declaration)
        assertTrue("Real adapter rejected synthetic input: $result", result is GuidedSampleResult.Ready)
        return (result as GuidedSampleResult.Ready).sample
    }
    private fun evaluate(scenario: GuidedScenario, declaration: GuidedAnswers, samples: List<GuidedSample>) = when (scenario) {
        GuidedScenario.IDLE_DRAIN -> IdleDrainRuleSet.evaluate(declaration, samples)
        GuidedScenario.USAGE_HEAT -> UsageHeatRuleSet.evaluate(declaration, samples)
    }
    private fun state(sample: GuidedSample, scenario: GuidedScenario, assessment: GuidedAssessment, declaration: GuidedAnswers = answers) =
        GuidedUiState(GuidedDiagnosticSession("session", scenario, 0, 0, 0, declaration, listOf(sample), GuidedStage.READY), assessment)
    private fun target(sample: GuidedSample, assessment: GuidedAssessment): VerificationTarget.AppGroup? {
        val state = state(sample, GuidedScenario.IDLE_DRAIN, assessment)
        return guidedVerificationTarget(state, state.session!!.key, assessment.primaryAction.id)
    }
    private fun ready(result: GuidedPayloadResult): GuidedPayloadResult.Ready {
        assertTrue("Payload must preserve the real local result: $result", result is GuidedPayloadResult.Ready)
        return result as GuidedPayloadResult.Ready
    }
    private fun both(sample: GuidedSample, declaration: GuidedAnswers = answers): List<Pair<GuidedAssessment, GuidedPayloadResult.Ready>> =
        GuidedScenario.entries.map { scenario ->
            val assessment = evaluate(scenario, declaration, listOf(sample))
            val payload = ready(GuidedExplanationPayload.build(state(sample, scenario, assessment, declaration).session!!,
                assessment, GuidedExplanationOptions(), AppLanguage.EN))
            assertEquals(assessment.primaryAction.id, payload.actionId)
            assertEquals(assessment.facts.map { it.id }.toSet(), payload.factIds)
            assertTrue(assessment.candidates.size <= 3)
            val facts = assessment.facts.map { it.id }.toSet()
            val gaps = assessment.gaps.map { it.id }.toSet()
            val candidates = assessment.candidates.map { it.ruleId }.toSet()
            assertEquals(facts.size, assessment.facts.size)
            assertTrue(assessment.candidates.all { facts.containsAll(it.evidenceIds) && gaps.containsAll(it.gapIds) })
            assertTrue(assessment.gaps.all { facts.containsAll(it.evidenceIds) })
            assertTrue((facts + gaps + candidates).containsAll(assessment.primaryAction.reasonIds))
            assessment to payload
        }
    private fun project(report: PowerDiagnosticSnapshot) = requireNotNull(VerificationSummaryProjector.project(report,
        report.verificationMetadata!!, VerificationFixtures.context(), VerificationTarget.Overall))
}
