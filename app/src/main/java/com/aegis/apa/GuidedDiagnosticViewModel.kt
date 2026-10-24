package com.aegis.apa

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aegis.apa.agent.*
import com.aegis.apa.localization.AppLanguage
import com.aegis.apa.model.*
import java.util.concurrent.Executor

class GuidedDiagnosticViewModel : ViewModel() {
    private val mutableState = mutableStateOf(GuidedUiState())
    val state: State<GuidedUiState> = mutableState
    private val mutableExplanation = mutableStateOf(GuidedExplanationState())
    val explanationState: State<GuidedExplanationState> = mutableExplanation
    private var explanationLanguage = AppLanguage.ZH_CN
    private var explanation: GuidedExplanationController? = null
    private val controller = GuidedDiagnosticController(onState = {
        mutableState.value = it
        explanation?.synchronizeInputs()
    })
    /** Called by the Activity with application-safe suppliers. Rebinding after rotation retains ownership. */
    fun configureExplanation(credentials: () -> StoredApiKey?, configuration: () -> String,
        executor: Executor, publishExecutor: Executor,
        provider: (GuidedPayloadResult.Ready, StoredApiKey, CloudRequestCancellation) -> GuidedExplanationResult =
            { payload, key, cancel -> GuidedExplanationProvider().explain(payload, key, cancel) }) {
        if (explanation != null) return
        explanation = GuidedExplanationController(controller, credentials, { explanationLanguage }, configuration,
            executor, publishExecutor, { mutableExplanation.value = it }, provider)
    }
    fun updateExplanationLanguage(language: AppLanguage) {
        explanationLanguage = language; refreshExplanationContext()
    }
    fun refreshExplanationContext() { explanation?.synchronizeInputs() }
    fun previewExplanation(key: GuidedSessionKey) {
        if (state.value.session?.key == key) explanation?.preview(explanationState.value.options)
    }
    fun setExplanationOptions(options: GuidedExplanationOptions) { explanation?.setOptions(options) }
    fun dismissExplanationPreview() { explanation?.dismissPreview() }
    fun sendExplanation(key: GuidedSessionKey) { explanation?.send(key) }
    fun cancelExplanation() { explanation?.cancel() }
    fun start(scenario: GuidedScenario) = controller.start(scenario)
    fun updateAnswers(answers: GuidedAnswers) = controller.updateAnswers(answers)
    fun updateAnswers(key: GuidedSessionKey, answers: GuidedAnswers) = controller.updateAnswers(key, answers)
    fun offerSample(sample: GuidedSample) = controller.offerSample(sample)
    fun previewSample(sampleId: String) = controller.previewSample(sampleId)
    fun confirmSample(sampleId: String, replaceSampleId: String? = null) = controller.confirmSample(sampleId, replaceSampleId)
    fun confirmSample(preview: GuidedSamplePreview, replaceSampleId: String? = null) = controller.confirmSample(preview, replaceSampleId)
    fun evaluateLocal(key: GuidedSessionKey) = controller.evaluateLocal(key)
    fun markActionStarted(actionId: String) = controller.markActionStarted(actionId)
    fun markActionStarted(key: GuidedSessionKey, actionId: String) = controller.markActionStarted(key, actionId)
    fun dispatchAction(event: GuidedUiEvent, dispatch: (GuidedAction) -> Unit) = dispatchGuidedAction(controller, event, dispatch)
    fun offerBasicSnapshot(token: GuidedWorkToken, snapshot: DeviceSnapshot, sampleId: String) =
        offerGuidedBasicSnapshot(controller, token, snapshot, sampleId)
    fun beginWork(key: GuidedSessionKey, cancel: () -> Unit) = controller.beginWork(key, cancel)
    fun completeWork(token: GuidedWorkToken, onCurrent: () -> Unit) = controller.completeWork(token, onCurrent)
    fun offerSample(token: GuidedWorkToken, sample: GuidedSample) = controller.offerSample(token, sample)
    fun failWork(token: GuidedWorkToken, errorId: String) = controller.failWork(token, errorId)
    fun cancelWork() = controller.cancelWork()
    fun end() = controller.end()
    fun end(key: GuidedSessionKey) = controller.end(key)
    fun restart(scenario: GuidedScenario) = controller.restart(scenario)
    fun restart(key: GuidedSessionKey, scenario: GuidedScenario) = controller.restart(key, scenario)
    fun dispose() { explanation?.dispose(); controller.dispose() }
    override fun onCleared() { dispose(); super.onCleared() }
}
