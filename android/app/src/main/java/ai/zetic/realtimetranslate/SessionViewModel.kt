package ai.zetic.realtimetranslate

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionAction {
    data class PermissionChanged(val granted: Boolean, val permanentlyDenied: Boolean = false) : SessionAction
    data class ProbeCapabilities(val context: Context) : SessionAction
    data class InputLanguageChanged(val speaker: Speaker, val language: SpeechLanguage) : SessionAction
    data class ReadingLanguageChanged(val speaker: Speaker, val language: TranslationLanguage) : SessionAction
    data object StartConversation : SessionAction
    data object EndSession : SessionAction
    data class PttPress(val context: Context, val speaker: Speaker) : SessionAction
    data class PttRelease(val speaker: Speaker) : SessionAction
    data class TogglePtt(val context: Context, val speaker: Speaker) : SessionAction
    data object Retry : SessionAction
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
            is SessionAction.PermissionChanged -> mutableState.value = mutableState.value.copy(
                phase = if (action.granted) SessionPhase.Ready else SessionPhase.PermissionRequired,
                permissionPermanentlyDenied = !action.granted && action.permanentlyDenied,
                errorMessage = null,
            )
            is SessionAction.ProbeCapabilities -> probeCapabilities(action.context)
            is SessionAction.InputLanguageChanged -> updateSettings(action.speaker) { it.copy(inputLanguage = action.language) }
            is SessionAction.ReadingLanguageChanged -> updateSettings(action.speaker) { it.copy(readingLanguage = action.language) }
            SessionAction.StartConversation -> mutableState.value = mutableState.value.copy(conversationStarted = true, errorMessage = null)
            SessionAction.EndSession -> endSession()
            is SessionAction.PttPress -> start(action.context, action.speaker)
            is SessionAction.PttRelease -> stop(action.speaker)
            is SessionAction.TogglePtt -> toggle(action.context, action.speaker)
            SessionAction.Retry -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, errorMessage = null)
        }
    }

    private fun updateSettings(speaker: Speaker, transform: (SpeakerSettings) -> SpeakerSettings) {
        val current = mutableState.value
        mutableState.value = current.copy(settings = current.settings + (speaker to transform(current.settingsFor(speaker))))
    }

    private fun start(context: Context, speaker: Speaker) {
        val current = mutableState.value
        if (!current.conversationStarted || current.phase != SessionPhase.Ready) return
        val settings = current.settingsFor(speaker)
        if (settings.inputLanguage !in current.availableInputLanguages && settings.inputLanguage !in current.downloadableInputLanguages) {
            mutableState.value = current.copy(phase = SessionPhase.Error, errorMessage = current.sourceCapabilityMessage ?: "The selected language for speaker ${speaker.label} needs on-device STT support verification.")
            return
        }
        transcriber?.destroy()
        val newTranscriber = transcriberFactory(context.applicationContext)
        transcriber = newTranscriber
        mutableState.value = current.copy(phase = finalizingPhase(speaker), errorMessage = null)
        when (val result = newTranscriber.start(settings.inputLanguage, transcriptListener(speaker, newTranscriber))) {
            SpeechStartResult.Started -> listening(speaker)
            is SpeechStartResult.Downloading -> mutableState.value = mutableState.value.copy(modelGateMessage = result.message)
            is SpeechStartResult.Failed -> fail(result.message)
        }
    }

    private fun toggle(context: Context, speaker: Speaker) = when (mutableState.value.phase) {
        listeningPhase(speaker) -> stop(speaker)
        SessionPhase.Ready -> start(context, speaker)
        else -> Unit
    }

    private fun probeCapabilities(context: Context) {
        val probe = transcriberFactory(context.applicationContext)
        mutableState.value = mutableState.value.copy(sourceCapabilityMessage = "Checking on-device STT support for each spoken language.")
        probe.probe(SpeechLanguage.entries.toList()) { result ->
            probe.destroy()
            mutableState.value = mutableState.value.copy(
                availableInputLanguages = result.available,
                downloadableInputLanguages = result.downloadable,
                sourceCapabilityMessage = result.message ?: if (result.available.isEmpty()) "No on-device spoken languages are available on this device." else null,
            )
        }
    }

    private fun listening(speaker: Speaker) {
        if (transcriber != null) mutableState.value = mutableState.value.copy(phase = listeningPhase(speaker), modelGateMessage = null)
    }

    private fun transcriptListener(speaker: Speaker, owner: SpeechTranscriber) = object : SpeechTranscriptListener {
        override fun onReady() = listening(speaker)
        override fun onModelDownload(message: String, restartRequired: Boolean) {
            if (restartRequired) {
                owner.destroy()
                if (transcriber === owner) transcriber = null
                mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, modelGateMessage = message)
            } else mutableState.value = mutableState.value.copy(phase = finalizingPhase(speaker), modelGateMessage = message)
        }
        override fun onPartial(transcript: String) = updateTranscript(speaker, transcript, false)
        override fun onFinal(transcript: String) = updateTranscript(speaker, transcript, true)
        override fun onStopped() {
            owner.destroy()
            if (transcriber === owner) transcriber = null
            finalize(speaker)
        }
        override fun onDeviceUnsupported(message: String) = fail(message, owner)
        override fun onError(message: String) = fail(message, owner)
    }

    private fun updateTranscript(speaker: Speaker, transcript: String, isFinal: Boolean) {
        val current = mutableState.value
        if (current.activeSpeaker() != speaker) return
        val pending = current.conversations.lastOrNull()?.takeIf { it.speaker == speaker && !it.isFinal }
        val item = ConversationItem(
            id = pending?.id ?: "transcript-${current.conversations.size}", speaker = speaker,
            sourceLanguage = current.settingsFor(speaker).inputLanguage,
            targetLanguage = current.settingsFor(speaker.other()).readingLanguage,
            transcript = transcript, isFinal = isFinal,
        )
        mutableState.value = current.copy(conversations = if (pending == null) current.conversations + item else current.conversations.dropLast(1) + item)
    }

    private fun stop(speaker: Speaker) {
        if (mutableState.value.phase != listeningPhase(speaker)) return
        mutableState.value = mutableState.value.copy(phase = finalizingPhase(speaker))
        transcriber?.stop()
    }

    private fun finalize(speaker: Speaker) {
        val current = mutableState.value
        val item = current.conversations.lastOrNull()?.takeIf { it.speaker == speaker && it.isFinal }
        if (item == null) {
            mutableState.value = current.copy(phase = SessionPhase.Ready)
            return
        }
        mutableState.value = current.copy(phase = translatingPhase(speaker))
        when (val gate = modelGate.check(item.sourceLanguage, item.targetLanguage)) {
            GateResult.Ready -> failTranslation(item.id, "The Hy-MT2 translation runtime is not connected yet.")
            is GateResult.Blocked -> failTranslation(item.id, gate.reason)
        }
    }

    private fun failTranslation(id: String, message: String) {
        val current = mutableState.value
        mutableState.value = current.copy(phase = SessionPhase.Ready, conversations = current.conversations.map { if (it.id == id) it.copy(translationError = message) else it })
    }

    private fun fail(message: String, owner: SpeechTranscriber? = transcriber) {
        owner?.destroy()
        if (owner === transcriber) transcriber = null
        mutableState.value = mutableState.value.copy(phase = SessionPhase.Error, errorMessage = message)
    }

    private fun endSession() {
        transcriber?.destroy()
        transcriber = null
        mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, conversationStarted = false, errorMessage = null, modelGateMessage = null)
    }

    override fun onCleared() { transcriber?.destroy(); transcriber = null }
}

private fun listeningPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.ListeningA else SessionPhase.ListeningB
private fun finalizingPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.FinalizingA else SessionPhase.FinalizingB
private fun translatingPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.TranslatingA else SessionPhase.TranslatingB
