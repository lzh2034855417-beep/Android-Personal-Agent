package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class GuidedExplanationControllerTest {
    private class Queue : Executor {
        val tasks = java.util.ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private class Fixture {
        val local = GuidedDiagnosticController(newId = { "session" })
        val worker = Queue(); val publications = Queue()
        var language = AppLanguage.EN
        var configuration = "provider-a:1"
        var reads = 0; var calls = 0
        var key: StoredApiKey? = StoredApiKey("provider-a", "secret", Long.MAX_VALUE)
        var sent: GuidedPayloadResult.Ready? = null
        var cancellation: CloudRequestCancellation? = null
        var result: GuidedExplanationResult = GuidedExplanationResult.Accepted(
            GuidedExplanationText(emptyList(), emptyList(), emptyList(), "Optional current explanation"))
        val controller = GuidedExplanationController(local, { reads++; key }, { language }, { configuration },
            worker, publications, provider = { payload, _, cancel ->
                calls++; sent = payload; cancellation = cancel; result
            })
        init { local.start(GuidedScenario.USAGE_HEAT); local.updateAnswers(GuidedAnswers(reviewed = true));
            local.evaluateLocal(local.state.session!!.key) }
        fun preview() = controller.preview(controller.state.options)
        fun send() = controller.send(local.state.session!!.key)
        fun finish() { worker.drain(); publications.drain() }
    }

    private class ActiveSessionFixture(readCredential: () -> StoredApiKey = GuidedExplanationSession::credentials) {
        val local = GuidedDiagnosticController(newId = { "active-session" })
        val worker = Queue(); val publications = Queue()
        var reads = 0
        val sentProviders = mutableListOf<String>()
        val controller = GuidedExplanationController(local,
            { reads++; readCredential() }, { AppLanguage.EN },
            GuidedExplanationSession::configuration, worker, publications,
            provider = { _, key, _ ->
                sentProviders += key.provider
                GuidedExplanationResult.Accepted(GuidedExplanationText(emptyList(), emptyList(), emptyList(), "active"))
            })
        init {
            local.start(GuidedScenario.USAGE_HEAT)
            local.updateAnswers(GuidedAnswers(reviewed = true))
            local.evaluateLocal(local.state.session!!.key)
        }
        fun preview() = controller.preview(controller.state.options)
        fun send() = controller.send(local.state.session!!.key)
        fun finish() { worker.drain(); publications.drain() }
    }

    @Test fun activeSessionKeylessProviderCannotReusePreviouslySavedProvider() {
        val savedA = StoredApiKey("provider-a", "saved-a", Long.MAX_VALUE)
        try {
            ApiSession.update(savedA)
            val f = ActiveSessionFixture()
            f.preview()
            // Settings activation of keyless B returns null while persisted A remains stored.
            ApiSession.update(null)
            f.send(); assertEquals(0, f.reads)
            f.preview(); assertTrue(f.controller.state.previewVisible); assertEquals(0, f.reads)
            val assessment = f.local.state.assessment
            f.send(); f.finish()
            assertEquals("guided.ai.missing_key", f.controller.state.errorId)
            assertTrue(f.sentProviders.isEmpty()); assertEquals(1, f.reads)
            assertSame(assessment, f.local.state.assessment)
            assertEquals("provider-a", savedA.provider)
        } finally { ApiSession.update(null) }
    }

    @Test fun activityRecreationDoesNotReloadStoredAOverIntentionalKeylessB() {
        val storedA = StoredApiKey("provider-a", "saved-a", Long.MAX_VALUE)
        try {
            ApiSession.update(storedA)
            val f = ActiveSessionFixture()
            f.preview()
            val beforeSelection = ApiSession.configurationRevision
            ApiSession.update(null) // Settings explicitly selected keyless B; persisted A still exists.
            val selectedRevision = ApiSession.configurationRevision
            assertTrue(selectedRevision > beforeSelection)
            var reloads = 0
            ApiSession.initialize { reloads++; storedA } // Same initializer called by Activity recreation.
            assertEquals(selectedRevision, ApiSession.configurationRevision)
            assertEquals(0, reloads)
            f.send(); assertEquals(0, f.reads) // Selection invalidates the old preview.
            f.preview(); f.send(); f.finish()
            assertEquals("guided.ai.missing_key", f.controller.state.errorId)
            assertTrue(f.sentProviders.isEmpty())
            assertEquals(1, f.reads)
        } finally { ApiSession.update(null) }
    }

    @Test fun activeSessionExpiryClearsCredentialAndReportsTypedErrorAfterConfirmation() {
        try {
            ApiSession.update(StoredApiKey("provider-a", "expired", 1L))
            val f = ActiveSessionFixture(); val revision = ApiSession.configurationRevision
            f.preview(); assertEquals(0, f.reads)
            assertEquals(revision, ApiSession.configurationRevision)
            f.send(); f.finish()
            assertEquals("guided.ai.expired_key", f.controller.state.errorId)
            assertEquals(revision + 1, ApiSession.configurationRevision)
            assertTrue(f.sentProviders.isEmpty()); assertFalse(f.controller.state.loading)
            f.controller.synchronizeInputs()
            assertEquals("guided.ai.expired_key", f.controller.state.errorId)
            f.send(); assertEquals(1, f.reads)
            f.preview(); f.send(); f.finish()
            assertEquals("guided.ai.missing_key", f.controller.state.errorId)
        } finally { ApiSession.update(null) }
    }

    @Test fun equalActiveSessionReloadKeepsPreviewAndConfirmedSendUsesActiveProvider() {
        try {
            val activeA = StoredApiKey("provider-a", "active-a", Long.MAX_VALUE)
            ApiSession.update(activeA)
            val f = ActiveSessionFixture(); val revision = ApiSession.configurationRevision
            f.send(); f.preview(); assertEquals(0, f.reads)
            ApiSession.update(activeA.copy())
            f.controller.synchronizeInputs()
            assertEquals(revision, ApiSession.configurationRevision)
            assertTrue(f.controller.state.previewVisible)
            f.send(); f.finish()
            assertEquals(listOf("provider-a"), f.sentProviders)
            assertEquals("active", f.controller.state.text!!.actionNote)
            assertEquals(1, f.reads)
        } finally { ApiSession.update(null) }
    }

    @Test fun credentialFailureCannotAttachErrorAfterUnrelatedSessionOrConfigurationChange() {
        for (changeSession in listOf(false, true)) {
            try {
                ApiSession.update(StoredApiKey("provider-a", "expired", 1L))
                lateinit var f: ActiveSessionFixture
                f = ActiveSessionFixture {
                    try { GuidedExplanationSession.credentials() } catch (failure: Exception) {
                        if (changeSession) f.local.restart(GuidedScenario.IDLE_DRAIN)
                        else ApiSession.update(StoredApiKey("provider-b", "active-b", Long.MAX_VALUE))
                        throw failure
                    }
                }
                f.preview(); f.send(); f.finish()
                assertNull("changeSession=$changeSession", f.controller.state.errorId)
                assertNull(f.controller.state.payload); assertNull(f.controller.state.text)
                assertFalse(f.controller.state.loading); assertTrue(f.sentProviders.isEmpty())
            } finally { ApiSession.update(null) }
        }
    }

    @Test fun credentialsAreUnreadUntilCurrentExplicitPreviewAndDoubleClickSendsOnce() {
        val f = Fixture(); f.send(); assertEquals(0, f.reads)
        f.preview(); assertTrue(f.controller.state.previewVisible); assertEquals(0, f.reads)
        val ready = f.controller.state.payload as GuidedPayloadResult.Ready
        assertFalse(ready.userPrompt.contains("secret"))
        f.send(); f.send(); assertTrue(f.controller.state.loading); assertEquals(1, f.reads)
        f.finish(); assertEquals(1, f.calls); assertSame(ready, f.sent)
        assertEquals("Optional current explanation", f.controller.state.text!!.actionNote)
    }

    @Test fun staleFullKeyCannotAuthorizeCredentialRead() {
        val f = Fixture(); f.preview(); assertTrue(f.controller.state.previewVisible); val old = f.local.state.session!!.key
        f.local.updateAnswers(GuidedAnswers(reviewed = false)); f.controller.send(old)
        assertEquals(0, f.reads); assertFalse(f.controller.state.previewVisible)
    }

    @Test fun optionAndLanguageChangesRequireFreshPreviewAndRejectQueuedWork() {
        val f = Fixture(); f.preview(); assertTrue(f.controller.state.previewVisible); f.controller.setOptions(GuidedExplanationOptions(true)); f.send()
        assertEquals(0, f.reads); assertNull(f.controller.state.payload)
        f.preview(); f.send(); f.language = AppLanguage.ZH_CN; f.finish()
        assertEquals(0, f.calls); assertNull(f.controller.state.text); assertFalse(f.controller.state.loading)
    }

    @Test fun providerConfigurationChangeRevokesPreviewWithoutReadingCredentials() {
        val f = Fixture(); f.preview(); assertTrue(f.controller.state.previewVisible); f.configuration = "provider-a:2"; f.send()
        assertEquals(0, f.reads); assertNull(f.controller.state.payload)
        f.preview(); f.send(); f.configuration = "provider-b:3"; f.finish()
        assertEquals(0, f.calls); assertNull(f.controller.state.text)
    }

    @Test fun inputReplacementRestartExitAndDisposeRejectLateDelivery() {
        for (change in 0..3) {
            val f = Fixture(); f.preview(); f.send(); assertTrue(f.controller.state.loading); f.worker.drain()
            when (change) {
                0 -> f.local.updateAnswers(GuidedAnswers(reviewed = false))
                1 -> f.local.restart(GuidedScenario.IDLE_DRAIN)
                2 -> f.local.end()
                3 -> f.controller.dispose()
            }
            f.controller.synchronizeInputs(); f.publications.drain()
            assertNull("change=$change", f.controller.state.text)
            assertFalse(f.controller.state.loading)
        }
    }

    @Test fun completedTextClearsOnKeyLanguageOptionsAndProviderChanges() {
        for (change in 0..3) {
            val f = Fixture(); f.preview(); f.send(); f.finish(); assertNotNull(f.controller.state.text)
            when (change) {
                0 -> f.local.updateAnswers(GuidedAnswers(reviewed = false))
                1 -> f.language = AppLanguage.ZH_CN
                2 -> f.controller.setOptions(GuidedExplanationOptions(true))
                3 -> f.configuration = "provider-b:2"
            }
            f.controller.synchronizeInputs(); assertNull("change=$change", f.controller.state.text)
        }
    }

    @Test fun stopCancelsRemoteAndAllowsOnlyManualRepreviewRetry() {
        val f = Fixture(); f.preview(); f.send(); f.worker.drain(); f.controller.cancel()
        assertEquals("guided.ai.cancelled", f.controller.state.errorId)
        assertThrows(java.util.concurrent.CancellationException::class.java) { f.cancellation!!.ensureActive() }
        f.publications.drain(); assertNull(f.controller.state.text)
        f.send(); assertEquals(1, f.reads)
        f.preview(); f.send(); f.finish(); assertEquals(2, f.calls); assertNotNull(f.controller.state.text)
    }

    @Test fun keylessAndRejectedRequestsPreserveExactLocalAssessmentAndNoPartialText() {
        val f = Fixture(); val assessment = f.local.state.assessment
        f.key = null; f.preview(); f.send(); f.finish()
        assertEquals("guided.ai.missing_key", f.controller.state.errorId)
        assertSame(assessment, f.local.state.assessment); assertEquals(0, f.calls)
        f.key = StoredApiKey("provider-a", "secret", Long.MAX_VALUE)
        f.result = GuidedExplanationResult.Rejected("guided.ai.invalid_reference")
        f.preview(); f.send(); f.finish()
        assertEquals("guided.ai.invalid_reference", f.controller.state.errorId)
        assertSame(assessment, f.local.state.assessment); assertNull(f.controller.state.text)
    }

    @Test fun otherOwnedWorkCancelsExplanationAndLateResultCannotReplaceIt() {
        val f = Fixture(); f.preview(); f.send(); assertTrue(f.controller.state.loading); f.worker.drain()
        var delivered = false
        val token = f.local.beginWork(f.local.state.session!!.key) {}!!
        f.publications.drain(); assertNull(f.controller.state.text); assertFalse(f.controller.state.loading)
        assertTrue(f.local.completeWork(token) { delivered = true }); assertTrue(delivered)
    }

    @Test fun oversizedSummaryBlocksSendWithoutCredentialsOrEvidenceRemoval() {
        val f = Fixture()
        val input = GuidedEvidenceAdapter.adapt(GuidedInput(null, GuidedDiagnosticFixtures.device(30.0), null), "sample", f.local.state.session!!.answers) as GuidedSampleResult.Ready
        val temperature = input.sample.evidence.first { it.kind == GuidedEvidenceKind.BATTERY_TEMPERATURE }
        val sample = input.sample.copy(evidence = List(100) { temperature.copy(id = "sample.temperature.$it") })
        f.local.offerSample(sample); assertTrue(f.local.confirmSample(f.local.previewSample("sample")!!))
        val assessment = f.local.state.assessment
        f.preview(); assertTrue(f.controller.state.payload is GuidedPayloadResult.Rejected)
        assertEquals("guided.ai.payload_too_large", (f.controller.state.payload as GuidedPayloadResult.Rejected).reasonId)
        f.send(); assertEquals(0, f.reads); assertSame(assessment, f.local.state.assessment)
        assertEquals(100, f.local.state.session!!.samples.single().evidence.size)
    }

    @Test fun apiSessionRevisionTracksChangesIncludingExpiryButNotSameValueReload() {
        ApiSession.update(null)
        val before = ApiSession.configurationRevision
        val key = StoredApiKey("fixture", "never-log-this", 100)
        ApiSession.update(key); assertEquals(before + 1, ApiSession.configurationRevision)
        ApiSession.update(key.copy()); assertEquals(before + 1, ApiSession.configurationRevision)
        ApiSession.update(key.copy(apiKey = "replaced-secret")); assertEquals(before + 2, ApiSession.configurationRevision)
        assertThrows(AgentFailureException::class.java) { ApiSession.requireValid(101) }
        assertEquals(before + 3, ApiSession.configurationRevision)
        ApiSession.update(null); assertEquals(before + 3, ApiSession.configurationRevision)
        assertFalse(key.toString().contains("never-log-this"))
    }

    @Test fun viewModelRetainsOwnerAcrossRebindingAndClearsCompletedTextOnLocalInputChange() {
        val owner = GuidedDiagnosticViewModel(); val worker = Queue(); val publication = Queue()
        var reads = 0
        owner.configureExplanation({ reads++; StoredApiKey("fixture", "secret", Long.MAX_VALUE) },
            { "fixture:1" }, worker, publication,
            { _, _, _ -> GuidedExplanationResult.Accepted(GuidedExplanationText(emptyList(), emptyList(), emptyList(), "retained")) })
        owner.start(GuidedScenario.USAGE_HEAT); owner.updateAnswers(GuidedAnswers(reviewed = true))
        val key = owner.state.value.session!!.key; owner.evaluateLocal(key)
        owner.previewExplanation(key); assertEquals(0, reads); owner.sendExplanation(key)
        owner.configureExplanation({ throw AssertionError("Activity rebind must not replace safe dependencies") },
            { "unused-rebind" }, Queue(), Queue())
        worker.drain(); publication.drain(); assertEquals("retained", owner.explanationState.value.text!!.actionNote)
        owner.updateAnswers(key, GuidedAnswers(reviewed = false)); assertNull(owner.explanationState.value.text)
        assertEquals(1, reads); owner.dispose()
    }

    @Test fun sampleReplacementClearsCompletedTextWhileRetainingNewLocalAssessment() {
        val f = Fixture()
        fun confirm(id: String, temperature: Double, replacement: String? = null) {
            val sample = (GuidedEvidenceAdapter.adapt(GuidedInput(null, GuidedDiagnosticFixtures.device(temperature), null), id,
                f.local.state.session!!.answers) as GuidedSampleResult.Ready).sample
            f.local.offerSample(sample); assertTrue(f.local.confirmSample(f.local.previewSample(id)!!, replacement))
        }
        confirm("first", 30.0); f.preview(); f.send(); f.finish(); assertNotNull(f.controller.state.text)
        confirm("second", 25.0, "first"); f.controller.synchronizeInputs()
        assertNull(f.controller.state.text); assertNotNull(f.local.state.assessment)
        assertEquals("second", f.local.state.session!!.samples.single().id)
    }

    @Test fun bilingualCopyUsesFixedReasonsAndLabelsWithNoSecretOrChineseLeakToEnglish() {
        val panelIds = listOf("title", "limits", "notes", "preview", "sending", "stop", "optional", "previewTitle",
            "scope", "sensitive", "notesOn", "notesOff", "bytes", "byteScope", "send", "cancel")
        val reasons = listOf("invalid_input", "stale_assessment", "payload_too_large", "invalid_unicode", "invalid_json",
            "invalid_schema", "invalid_reference", "invalid_action", "text_limit", "capability_violation", "response_too_large",
            "invalid_envelope", "refused", "cancelled", "empty_response", "missing_key", "expired_key", "unknown_provider", "http_error", "network_error")
        for (language in AppLanguage.entries) {
            val texts = panelIds.map { GuidedExplanationPanelCopy.text(it, language) } + reasons.map { GuidedExplanationCopy.reason("guided.ai.$it", language) }
            assertTrue(texts.all { it.isNotBlank() })
            if (language == AppLanguage.EN) assertTrue(texts.none { Regex("[\\p{IsHan}]").containsMatchIn(it) })
        }
        assertFalse(GuidedExplanationCopy.reason("secret-remote-body", AppLanguage.EN).contains("secret-remote-body"))
    }

    @Test fun restartedPageSessionRequiresFreshNotesOptIn() {
        val owner = GuidedDiagnosticViewModel()
        owner.configureExplanation({ null }, { "fixture:1" }, Queue(), Queue())
        owner.start(GuidedScenario.USAGE_HEAT)
        owner.setExplanationOptions(GuidedExplanationOptions(true))
        assertTrue(owner.explanationState.value.options.includeNotes)
        owner.restart(GuidedScenario.IDLE_DRAIN)
        assertFalse("A new session must not inherit notes consent", owner.explanationState.value.options.includeNotes)
    }
}
