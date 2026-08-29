package ai.zetic.realtimetranslate

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionAction {
    data class PermissionChanged(val granted: Boolean, val permanentlyDenied: Boolean = false) : SessionAction
    data class ProbeCapabilities(val context: Context) : SessionAction
    data class InputLanguageChanged(val language: SpeechLanguage) : SessionAction
    data class OutputLanguageChanged(val language: TranslationLanguage) : SessionAction
    data class Start(val context: Context) : SessionAction
    data object Stop : SessionAction
    data object Retry : SessionAction
    data object NewSession : SessionAction
}

class SessionViewModel(
    private val transcriberFactory: (Context) -> SpeechTranscriber = { AndroidOnDeviceSpeechTranscriber(it) },
    private val modelGate: ModelCompatibilityGate = ModelCompatibilityGate(),
    initialState: SessionUiState = SessionUiState(SessionPhase.PermissionRequired),
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()
    private var transcriber: SpeechTranscriber? = null

    fun dispatch(action: SessionAction) {
        when (action) {
            is SessionAction.PermissionChanged -> mutableState.value =
                mutableState.value.copy(
                    phase = if (action.granted) SessionPhase.Ready else SessionPhase.PermissionRequired,
                    permissionPermanentlyDenied = !action.granted && action.permanentlyDenied,
                    errorMessage = null,
                )
            is SessionAction.ProbeCapabilities -> probeCapabilities(action.context)
            is SessionAction.InputLanguageChanged -> mutableState.value = mutableState.value.copy(inputLanguage = action.language)
            is SessionAction.OutputLanguageChanged -> mutableState.value = mutableState.value.copy(outputLanguage = action.language)
            is SessionAction.Start -> start(action.context)
            SessionAction.Stop -> stop()
            SessionAction.Retry -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, errorMessage = null)
            SessionAction.NewSession -> mutableState.value =
                mutableState.value.copy(phase = SessionPhase.Ready, conversations = emptyList(), errorMessage = null)
        }
    }

    private fun start(context: Context) {
        val current = mutableState.value
        if (current.inputLanguage !in current.availableInputLanguages && current.inputLanguage !in current.downloadableInputLanguages) {
            mutableState.value = current.copy(
                phase = SessionPhase.Error,
                errorMessage = current.sourceCapabilityMessage ?: "선택한 발화 언어의 온디바이스 STT 지원을 먼저 확인해야 합니다.",
            )
            return
        }
        mutableState.value = current.copy(phase = SessionPhase.Processing, errorMessage = null)
        transcriber?.destroy()
        val newTranscriber = transcriberFactory(context.applicationContext)
        transcriber = newTranscriber
        when (val result = newTranscriber.start(current.inputLanguage, transcriptListener())) {
            SpeechStartResult.Started -> if (transcriber === newTranscriber) recording(current)
            is SpeechStartResult.Downloading -> if (transcriber === newTranscriber) {
                mutableState.value = mutableState.value.copy(
                    phase = SessionPhase.Processing,
                    errorMessage = null,
                    modelGateMessage = result.message,
                )
            }
            is SpeechStartResult.Failed -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Error, errorMessage = result.message)
        }
    }

    private fun probeCapabilities(context: Context) {
        val probe = transcriberFactory(context.applicationContext)
        mutableState.value = mutableState.value.copy(sourceCapabilityMessage = "발화 언어별 온디바이스 STT 지원을 확인 중입니다.")
        probe.probe(SpeechLanguage.entries.toList()) { result ->
            probe.destroy()
            mutableState.value = mutableState.value.copy(
                availableInputLanguages = result.available,
                downloadableInputLanguages = result.downloadable,
                sourceCapabilityMessage = result.message ?: if (result.available.isEmpty()) "이 기기에서 사용할 수 있는 온디바이스 발화 언어가 없습니다." else null,
            )
        }
    }

    private fun recording(current: SessionUiState) {
        val gateMessage = (modelGate.check(current.inputLanguage, current.outputLanguage) as? GateResult.Blocked)?.reason
        mutableState.value = mutableState.value.copy(phase = SessionPhase.Recording, modelGateMessage = gateMessage)
    }

    private fun transcriptListener() = object : SpeechTranscriptListener {
        override fun onReady() {
            recording(mutableState.value)
        }

        override fun onModelDownload(message: String, restartRequired: Boolean) {
            transcriber?.takeIf { restartRequired }?.destroy()
            if (restartRequired) transcriber = null
            mutableState.value = mutableState.value.copy(
                phase = if (restartRequired) SessionPhase.Ready else SessionPhase.Processing,
                modelGateMessage = message,
            )
        }

        override fun onPartial(transcript: String) {
            updateTranscript(transcript, isFinal = false)
        }

        override fun onFinal(transcript: String) {
            updateTranscript(transcript, isFinal = true)
        }

        override fun onStopped() {
            transcriber?.destroy()
            transcriber = null
            mutableState.value = mutableState.value.copy(phase = SessionPhase.Finished)
        }

        override fun onDeviceUnsupported(message: String) {
            transcriber?.destroy()
            transcriber = null
            mutableState.value = mutableState.value.copy(phase = SessionPhase.Error, errorMessage = message)
        }

        override fun onError(message: String) {
            transcriber?.destroy()
            transcriber = null
            mutableState.value = mutableState.value.copy(phase = SessionPhase.Error, errorMessage = message)
        }
    }

    private fun updateTranscript(transcript: String, isFinal: Boolean) {
        val current = mutableState.value
        val lastPending = current.conversations.lastOrNull()?.takeUnless { it.isFinal }
        val item = ConversationItem(lastPending?.id ?: "transcript-${current.conversations.size}", transcript = transcript, isFinal = isFinal)
        val conversations = if (lastPending == null) current.conversations + item else current.conversations.dropLast(1) + item
        mutableState.value = current.copy(conversations = conversations)
    }

    private fun stop() {
        transcriber?.stop()
        mutableState.value = mutableState.value.copy(phase = SessionPhase.Processing)
    }

    override fun onCleared() {
        transcriber?.destroy()
        transcriber = null
    }
}
