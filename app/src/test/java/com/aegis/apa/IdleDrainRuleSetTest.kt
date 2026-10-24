package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import com.aegis.apa.tool.*
import org.junit.Assert.*
import org.junit.Test

class IdleDrainRuleSetTest {
    private val answers = GuidedAnswers(VerificationFixtures.start, VerificationFixtures.end,
        VerificationConfirmation.NO, VerificationConfirmation.YES, VerificationConfirmation.NO, reviewed = true)

    private fun adapt(report: PowerDiagnosticSnapshot? = null, observation: BatteryObservationResult? = null,
        device: DeviceSnapshot? = null, id: String = "one") =
        (GuidedEvidenceAdapter.adapt(GuidedInput(report, device, observation), id, answers) as GuidedSampleResult.Ready).sample

    private fun report() = VerificationFixtures.snapshot().copy(inputSource = DiagnosticInputSource.ROOT,
        // The eligible fixture must not have app power exceeding its overall window.
        system = SystemPowerEvidence(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, 500.0, true)),
        verificationMetadata = VerificationFixtures.metadata(), apps = listOf(
            VerificationFixtures.snapshot().apps.single().copy(estimatedPowerMah = 200.0,
                backgroundPowerMah = 150.0, wakeLockDurationMillis = 600_000)))

    private fun checkReferences(result: GuidedAssessment) {
        val evidence = result.facts.map { it.id }.toSet()
        val gaps = result.gaps.map { it.id }.toSet()
        val rules = result.candidates.map { it.ruleId }.toSet()
        assertTrue(result.candidates.size <= 3)
        assertEquals(result.facts.size, evidence.size)
        result.gaps.forEach { assertTrue(evidence.containsAll(it.evidenceIds)) }
        result.candidates.forEach {
            assertTrue(evidence.containsAll(it.evidenceIds)); assertTrue(gaps.containsAll(it.gapIds))
        }
        assertTrue((evidence + gaps + rules).containsAll(result.primaryAction.reasonIds))
    }

    @Test fun missingAnswersThenCurrentBatteryThenValidObservationChooseUsefulNextSteps() {
        val cases = listOf(
            Triple(GuidedAnswers(), emptyList(), GuidedActionKind.ANSWER),
            Triple(answers, listOf(adapt(device = GuidedDiagnosticFixtures.device())), GuidedActionKind.OPEN_OBSERVATION),
            Triple(answers, listOf(adapt(observation = GuidedDiagnosticFixtures.observation())), GuidedActionKind.IMPORT_REPORT))
        cases.forEach { (context, samples, expected) ->
            val result = IdleDrainRuleSet.evaluate(context, samples)
            assertEquals(expected, result.primaryAction.kind); checkReferences(result)
            if (expected == GuidedActionKind.OPEN_OBSERVATION) assertTrue(result.candidates.isEmpty())
        }
    }

    @Test fun restoredAndStableObservationsStayExplicitlyRoughThroughActualAdapter() {
        listOf(BatteryObservationContinuity.PROCESS_OBSERVED, BatteryObservationContinuity.USER_CONFIRMED_AFTER_RESTORE).forEach {
            val observation = GuidedDiagnosticFixtures.observation().copy(durationMillis = 14_400_000, dropPercent = 12,
                start = BatteryObservationPoint(GuidedDiagnosticFixtures.now.minusSeconds(14_400), 90, false),
                drainPercentPerHour = 3.0, continuity = it)
            val result = IdleDrainRuleSet.evaluate(answers, listOf(adapt(observation = observation)))
            assertEquals(1, result.facts.size)
            assertEquals(3.0, (result.facts.single().value as GuidedValue.Number).value, 0.0)
            assertTrue(result.gaps.any { gap -> gap.id == "idle_observation_rough" })
            assertEquals("idle.rough_observation_rate", result.candidates.single().ruleId)
            assertTrue(result.candidates.single().gapIds.contains("idle_observation_rough")); checkReferences(result)
        }
    }

    @Test fun missingWindowOldWindowUnknownOwnershipAndPartialIdentityNeverOpenVerification() {
        val base = report()
        val cases = listOf(
            base.copy(verificationMetadata = VerificationFixtures.productionMetadata()),
            base.copy(verificationMetadata = VerificationFixtures.metadata().copy(windowsBySource =
                VerificationFixtures.metadata().windowsBySource.mapValues { (_, window) -> window.copy(
                    startedAt = window.startedAt!!.minusSeconds(86400), endedAt = window.endedAt!!.minusSeconds(86400)) })),
            base.copy(inputSource = DiagnosticInputSource.BUGREPORT),
            base.copy(sources = base.sources + ("packages" to DiagnosticSourceResult("packages", DiagnosticSourceStatus.TRUNCATED, ""))),
            base.copy(sources = base.sources + ("batterystats" to DiagnosticSourceResult("batterystats", DiagnosticSourceStatus.TRUNCATED, ""))))
        cases.forEach {
            val sample = adapt(it)
            val first = IdleDrainRuleSet.evaluate(answers, listOf(sample))
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, first.primaryAction.kind)
            assertNotEquals(GuidedActionKind.COLLECT, first.primaryAction.kind)
            assertEquals(first, IdleDrainRuleSet.evaluate(answers, listOf(sample))); checkReferences(first)
        }
        val unknown = IdleDrainRuleSet.evaluate(answers, listOf(adapt(cases.first())))
        assertTrue(unknown.gaps.any { it.id == "idle_window_unknown" })
    }

    @Test fun safeCompleteEvidenceOpensExistingVerificationButProtectedAndSharedGroupsDoNot() {
        val base = report()
        val allowed = IdleDrainRuleSet.evaluate(answers, listOf(adapt(base)))
        assertEquals(GuidedActionKind.OPEN_VERIFICATION, allowed.primaryAction.kind); checkReferences(allowed)
        listOf(
            base.apps.single().copy(uid = 1000, packageNames = listOf("com.vendor.service")),
            base.apps.single().copy(uid = null),
            base.apps.single().copy(packageNames = listOf("com.example.payment")),
            base.apps.single().copy(packageNames = listOf("com.android.phone")),
            base.apps.single().copy(sharedUid = true),
            base.apps.single().copy(packageNames = listOf("com.example.a", "com.example.b"), sharedUid = true)
        ).forEach { app ->
            val result = IdleDrainRuleSet.evaluate(answers, listOf(adapt(base.copy(apps = listOf(app)))))
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind)
            result.candidates.filter { it.ruleId.startsWith("idle.background_activity_correlation") }.forEach { candidate ->
                assertTrue(candidate.evidenceIds.all { id -> result.facts.single { it.id == id }.packages == app.packageNames.sorted() })
            }
            checkReferences(result)
        }
    }

    @Test fun unknownChargingRebootOrIdleDeclarationsLimitApplicationVerification() {
        listOf(answers.copy(charging = VerificationConfirmation.UNKNOWN),
            answers.copy(rebooted = VerificationConfirmation.UNKNOWN),
            answers.copy(mostlyIdle = VerificationConfirmation.UNKNOWN),
            answers.copy(charging = VerificationConfirmation.YES),
            answers.copy(rebooted = VerificationConfirmation.YES)).forEach {
            val result = IdleDrainRuleSet.evaluate(it, listOf(adapt(report())))
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind)
            assertTrue(result.gaps.any { gap -> gap.id == "idle_conditions_unconfirmed" }); checkReferences(result)
        }
    }

    @Test fun confirmedUnknownAnswersProceedWithoutRepeatedQuestionsAndAreEncoded() {
        val unknown = GuidedAnswers(reviewed = true)
        val result = IdleDrainRuleSet.evaluate(unknown, emptyList())
        assertEquals(GuidedActionKind.OPEN_OBSERVATION, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "idle_conditions_unconfirmed" })
        val sample = (GuidedEvidenceAdapter.adapt(GuidedInput(null, null, null), "one", unknown) as GuidedSampleResult.Ready).sample
        assertTrue(GuidedSummaryCodec.encode(sample).toString(Charsets.UTF_8).contains("\"reviewed\":true"))
        checkReferences(result)
    }

    @Test fun invalidCoverageCannotAuthorizeVerificationFromRawNumbers() {
        listOf(EvidenceField.POWER_MAH, EvidenceField.WAKELOCK_TIME, EvidenceField.UID_PACKAGES).forEach { field ->
            val base = report()
            val sample = adapt(base.copy(evidenceCoverage = EvidenceCoverage(base.evidenceCoverage.fields +
                (field to EvidenceFieldStatus.NOT_PARSED))))
            val result = IdleDrainRuleSet.evaluate(answers, listOf(sample))
            assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind); checkReferences(result)
        }
    }

    @Test fun unsupportedSourcesAndUnrepairableUnknownsEndWithoutRepeatedCollection() {
        val sample = GuidedSample("unsupported", emptyList(), answers, diagnosticInputSource = DiagnosticInputSource.ROOT,
            sources = mapOf("batterystats" to DiagnosticSourceStatus.UNSUPPORTED, "battery" to DiagnosticSourceStatus.UNSUPPORTED))
        val first = IdleDrainRuleSet.evaluate(answers, listOf(sample))
        assertEquals(GuidedActionKind.END, first.primaryAction.kind)
        assertEquals(first, IdleDrainRuleSet.evaluate(answers, listOf(sample))); checkReferences(first)
    }

    @Test fun zeroUnknownAndOmittedGroupsDoNotBecomePositiveOrWholeDeviceCandidates() {
        val base = report()
        val zero = base.copy(system = base.system.copy(batteryDrainWindow = BatteryDrainWindowEvidence(3_600_000, 5000.0, 0.0, true)),
            apps = listOf(base.apps.single().copy(estimatedPowerMah = 0.0, backgroundPowerMah = 0.0,
                wakeLockDurationMillis = 0, wakeupCount = 0, alarmCount = 0, jobCount = 0, backgroundDurationMillis = 0)))
        val result = IdleDrainRuleSet.evaluate(answers, listOf(adapt(zero)))
        assertTrue(result.facts.any { (it.value as? GuidedValue.Number)?.value == 0.0 })
        assertTrue(result.candidates.isEmpty()); checkReferences(result)
        val omitted = adapt(base.copy(apps = (1..23).map { base.apps.single().copy(uid = 10000 + it,
            packageNames = listOf("com.example.app%02d".format(it))) }))
        val capped = IdleDrainRuleSet.evaluate(answers, listOf(omitted))
        assertEquals(3, capped.candidates.size)
        assertTrue(capped.gaps.any { it.id == "app_groups_omitted" })
        assertTrue(capped.candidates.all { it.ruleId.startsWith("idle.background_activity_correlation") })
        checkReferences(capped)
    }

    @Test fun realTaskOneReportPreservesActualZeroAndMissingProductionWindow() {
        val section = RawDiagnosticSection("batterystats", DiagnosticSourceStatus.AVAILABLE,
            "Statistics since last charge:\nTime on battery: 1h (100%) realtime\n" +
                "Estimated power use (mAh):\n  Capacity: 5000, Computed drain: 10, actual drain: 0\n  UID u0a123: 10\n" +
                "Statistics for vendor scope:\nTime on battery: 30m (100%) realtime\n" +
                "Estimated power use (mAh):\n  Capacity: 5000, Computed drain: 15, actual drain: 12")
        val parsed = PowerDiagnosticPipeline.analyze(listOf(section), DiagnosticInputSource.BUGREPORT, GuidedDiagnosticFixtures.now)
        val result = IdleDrainRuleSet.evaluate(answers, listOf(adapt(parsed)))
        assertTrue(result.facts.any { it.kind == GuidedEvidenceKind.OVERALL_POWER })
        assertEquals(0.0, (result.facts.single { it.kind == GuidedEvidenceKind.OVERALL_POWER }.value as GuidedValue.Number).value, 0.0)
        assertTrue(result.gaps.any { it.id == "idle_window_unknown" })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind); checkReferences(result)
    }

    @Test fun actionPolicyUsesWarningConflictGapThenObservationAndStableIdOrder() {
        val actions = listOf(GuidedAction("idle.end", GuidedActionKind.END, emptyList()),
            GuidedAction("idle.verify_background", GuidedActionKind.OPEN_VERIFICATION, emptyList()),
            GuidedAction("idle.import_report", GuidedActionKind.IMPORT_REPORT, emptyList()),
            GuidedAction("idle.answer_conflict", GuidedActionKind.ANSWER, emptyList()),
            GuidedAction("system.warning", GuidedActionKind.VIEW_SYSTEM_WARNING, emptyList()))
        assertEquals("system.warning", GuidedActionPolicy.select(actions).id)
        assertEquals("idle.answer_conflict", GuidedActionPolicy.select(actions.dropLast(1)).id)
        assertEquals("idle.import_report", GuidedActionPolicy.select(actions.take(3)).id)
        assertEquals("a", GuidedActionPolicy.select(listOf(GuidedAction("z", GuidedActionKind.OPEN_OBSERVATION, emptyList()),
            GuidedAction("a", GuidedActionKind.OPEN_OBSERVATION, emptyList()))).id)
        assertEquals(GuidedActionKind.END, GuidedActionPolicy.select(emptyList()).kind)
    }

    @Test fun codecRejectsExplicitSourceStatusContradictionButAllowsMissingMapEntry() {
        val sample = adapt(observation = GuidedDiagnosticFixtures.observation())
        GuidedSummaryCodec.encode(sample.copy(sources = emptyMap()))
        try {
            GuidedSummaryCodec.encode(sample.copy(sources = mapOf("observation" to DiagnosticSourceStatus.UNSUPPORTED)))
            fail("Contradictory source status must be rejected")
        } catch (failure: GuidedSummaryException) { assertEquals("invalid_summary", failure.reasonId) }
    }

    @Test fun verificationActionTargetsExactlyOneUniqueCanonicalGroupCandidate() {
        val base = report()
        val sample = adapt(base.copy(apps = listOf(base.apps.single(), base.apps.single().copy(uid = 10124,
            packageNames = listOf("com.example.second")))))
        val result = IdleDrainRuleSet.evaluate(answers, listOf(sample))
        assertEquals("idle.verify_background", result.primaryAction.id)
        assertEquals(1, result.primaryAction.reasonIds.size)
        val selected = result.candidates.single { it.ruleId == result.primaryAction.reasonIds.single() }
        assertEquals("idle.background_activity_correlation:" + selected.evidenceIds.sorted().first(), selected.ruleId)
        val supporting = selected.evidenceIds.map { id -> result.facts.single { it.id == id } }
        assertEquals(1, supporting.map { it.packages }.distinct().size)
        assertEquals(listOf("com.example.app"), supporting.first().packages)
        assertEquals(result, IdleDrainRuleSet.evaluate(answers, listOf(sample.copy(evidence = sample.evidence.reversed()))))
        checkReferences(result)
    }

    @Test fun conflictPrecedesEvidenceAndIncompleteSystemEvidenceDoesNotBecomeAnAdjustment() {
        val reversed = answers.copy(startedAt = VerificationFixtures.end, endedAt = VerificationFixtures.start)
        val result = IdleDrainRuleSet.evaluate(reversed, listOf(adapt(report())))
        assertEquals("idle.answer_conflict", result.primaryAction.id)
        assertEquals(GuidedActionKind.ANSWER, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "answer_time_range_reversed" }); checkReferences(result)
        val chargedWindow = report().copy(verificationMetadata = VerificationFixtures.metadata().copy(windowsBySource =
            VerificationFixtures.metadata().windowsBySource.mapValues { (_, window) -> window.copy(charging = VerificationConfirmation.YES) }))
        val limited = IdleDrainRuleSet.evaluate(answers, listOf(adapt(chargedWindow)))
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, limited.primaryAction.kind)
        checkReferences(limited)
    }

    @Test fun repeatedEvidenceIdsFailClosedWithoutUnresolvableReferences() {
        val sample = adapt(report())
        val result = IdleDrainRuleSet.evaluate(answers, listOf(sample, sample))
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, result.primaryAction.kind)
        assertTrue(result.gaps.any { it.id == "idle_evidence_conflict" }); checkReferences(result)
    }

    @Test fun eligibilityUsesExistingPolicyAndTransientUidAndOnlyRetainedParsedGroups() {
        val base = report()
        val allowed = adapt(base)
        assertEquals(listOf(listOf("com.example.app")), allowed.verificationEligibleAppGroups)
        val encoded = GuidedSummaryCodec.encode(allowed).toString(Charsets.UTF_8)
        assertTrue(encoded.contains("\"verification_eligible_app_groups\":"))
        assertFalse(encoded.contains("10123"))
        val inputs = listOf(
            base.copy(apps = listOf(base.apps.single().copy(uid = null))),
            base.copy(apps = listOf(base.apps.single().copy(uid = 1000))),
            base.copy(apps = listOf(base.apps.single().copy(sharedUid = true))),
            base.copy(apps = listOf(base.apps.single().copy(packageNames = listOf("com.example.payment")))),
            base.copy(apps = listOf(base.apps.single(), base.apps.single())),
            base.copy(evidenceCoverage = EvidenceCoverage(base.evidenceCoverage.fields +
                (EvidenceField.WAKELOCK_TIME to EvidenceFieldStatus.NOT_PARSED))),
            base.copy(evidenceCoverage = EvidenceCoverage(base.evidenceCoverage.fields +
                (EvidenceField.POWER_MAH to EvidenceFieldStatus.SOURCE_UNAVAILABLE))),
            base.copy(sources = base.sources + ("packages" to DiagnosticSourceResult("packages", DiagnosticSourceStatus.TRUNCATED, ""))))
        inputs.forEach { assertTrue(adapt(it).verificationEligibleAppGroups.isEmpty()) }
        val capped = adapt(base.copy(apps = (1..23).map { base.apps.single().copy(uid = 10000 + it,
            packageNames = listOf("com.example.app%02d".format(java.util.Locale.ROOT, it))) }))
        assertEquals(20, capped.verificationEligibleAppGroups.size)
        assertFalse(capped.verificationEligibleAppGroups.contains(listOf("com.example.app21")))
        val mutable = mutableListOf(mutableListOf("com.example.app"))
        val copied = allowed.copy(verificationEligibleAppGroups = mutable)
        mutable.single().clear(); mutable.clear()
        assertEquals(listOf(listOf("com.example.app")), copied.verificationEligibleAppGroups)
        try { (copied.verificationEligibleAppGroups.single() as MutableList).clear(); fail("read-only group") }
        catch (_: UnsupportedOperationException) { }
        try { GuidedSummaryCodec.encode(allowed.copy(verificationEligibleAppGroups = listOf(listOf("com.example.absent")))); fail("unretained group") }
        catch (failure: GuidedSummaryException) { assertEquals("invalid_summary", failure.reasonId) }
    }

    @Test fun optionalMissingMetricsDoNotBlockCompletePolicySignalsButMixedWindowsDo() {
        val base = report()
        val sparse = base.copy(apps = listOf(base.apps.single().copy(foregroundPowerMah = null, foregroundDurationMillis = null,
            backgroundDurationMillis = null, wakeupCount = null, alarmCount = null, jobCount = null)))
        val sample = adapt(sparse)
        assertEquals(GuidedActionKind.OPEN_VERIFICATION, IdleDrainRuleSet.evaluate(answers, listOf(sample)).primaryAction.kind)
        val mixed = sample.copy(evidence = sample.evidence.map {
            if (it.kind == GuidedEvidenceKind.WAKELOCK_DURATION) it.copy(window = it.window!!.copy(
                endedAt = VerificationFixtures.end.plusSeconds(60))) else it
        })
        assertNotEquals(GuidedActionKind.OPEN_VERIFICATION, IdleDrainRuleSet.evaluate(answers, listOf(mixed)).primaryAction.kind)
    }

    @Test fun explicitSameIntervalChargingConflictRequiresAnswersButCurrentChargingAfterIntervalDoesNot() {
        val base = report()
        val chargedWindow = base.copy(verificationMetadata = VerificationFixtures.metadata().copy(windowsBySource =
            VerificationFixtures.metadata().windowsBySource.mapValues { (_, window) -> window.copy(charging = VerificationConfirmation.YES) }))
        val conflicting = IdleDrainRuleSet.evaluate(answers, listOf(adapt(chargedWindow)))
        assertEquals("idle.answer_conflict", conflicting.primaryAction.id)
        assertTrue(conflicting.gaps.any { it.id == "idle_conditions_conflict" }); checkReferences(conflicting)
        val oldReport = chargedWindow.copy(verificationMetadata = chargedWindow.verificationMetadata!!.copy(windowsBySource =
            chargedWindow.verificationMetadata!!.windowsBySource.mapValues { (_, window) -> window.copy(
                startedAt = window.startedAt!!.minusSeconds(86400), endedAt = window.endedAt!!.minusSeconds(86400)) }))
        assertNotEquals(GuidedActionKind.ANSWER, IdleDrainRuleSet.evaluate(answers, listOf(adapt(oldReport))).primaryAction.kind)
        val chargingNow = GuidedDiagnosticFixtures.device().copy(batteryInfo = GuidedDiagnosticFixtures.device().batteryInfo.copy(
            status = "正在充电", plugged = "AC"))
        assertEquals(GuidedActionKind.OPEN_VERIFICATION, IdleDrainRuleSet.evaluate(answers, listOf(adapt(base, device = chargingNow))).primaryAction.kind)
    }
}
