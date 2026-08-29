package ai.zetic.realtimetranslate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionAction {
    data class PermissionChanged(val granted: Boolean, val permanentlyDenied: Boolean = false) : SessionAction
    data class InputLanguageChanged(val language: SpeechLanguage) : SessionAction
    data class OutputLanguageChanged(val language: TranslationLanguage) : SessionAction
    data class Start(val context: Context) : SessionAction
    data object Stop : SessionAction
    data object Retry : SessionAction
    data object NewSession : SessionAction
}

class SessionViewModel(
    private val pipeline: RealtimeTranslationPipeline = RealtimeTranslationPipeline(ModelCompatibilityGate()),
    initialState: SessionUiState = SessionUiState(SessionPhase.PermissionRequired),
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    fun dispatch(action: SessionAction) {
        when (action) {
            is SessionAction.PermissionChanged -> mutableState.value =
                mutableState.value.copy(
                    phase = if (action.granted) SessionPhase.Ready else SessionPhase.PermissionRequired,
                    permissionPermanentlyDenied = !action.granted && action.permanentlyDenied,
                    errorMessage = null,
                )
            is SessionAction.InputLanguageChanged -> mutableState.value = mutableState.value.copy(inputLanguage = action.language)
            is SessionAction.OutputLanguageChanged -> mutableState.value = mutableState.value.copy(outputLanguage = action.language)
            is SessionAction.Start -> start(action.context)
            SessionAction.Stop -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Processing)
            SessionAction.Retry -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, errorMessage = null)
            SessionAction.NewSession -> mutableState.value =
                mutableState.value.copy(phase = SessionPhase.Ready, conversations = emptyList(), errorMessage = null)
        }
    }

    private fun start(context: Context) {
        val current = mutableState.value
        mutableState.value = current.copy(phase = SessionPhase.Processing, errorMessage = null)
        viewModelScope.launch {
            when (val result = pipeline.start(context.applicationContext, current.inputLanguage, current.outputLanguage)) {
                PipelineResult.Started -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Recording)
                is PipelineResult.Failed -> mutableState.value = mutableState.value.copy(phase = SessionPhase.Error, errorMessage = result.reason)
            }
        }
    }
}
