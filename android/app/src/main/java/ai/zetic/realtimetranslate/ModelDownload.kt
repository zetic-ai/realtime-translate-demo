package ai.zetic.realtimetranslate

import android.content.Context
import com.zeticai.mlange.core.background.BackgroundDownloadHandle
import com.zeticai.mlange.core.background.BackgroundDownloadState
import com.zeticai.mlange.core.background.BackgroundDownloadStatus
import com.zeticai.mlange.core.background.ModelRemovalResult
import com.zeticai.mlange.core.model.llm.LLMModelMode
import com.zeticai.mlange.core.model.llm.ZeticMLangeLLMModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The subset of a durable SDK download status that this app renders. */
data class ModelDownloadUiState(
    val state: BackgroundDownloadState,
    val progress: Float? = null,
    val errorMessage: String? = null,
    val isStatusLookupFailure: Boolean = false,
) {
    val isActive: Boolean
        get() = state in activeStates

    val isInstalled: Boolean
        get() = state == BackgroundDownloadState.INSTALLED

    companion object {
        private val activeStates = setOf(
            BackgroundDownloadState.QUEUED,
            BackgroundDownloadState.RESOLVING,
            BackgroundDownloadState.DOWNLOADING,
            BackgroundDownloadState.VERIFYING,
        )
    }
}

/**
 * Keeps the SDK's durable handle beside the explicit consent decision. The SDK owns the transfer
 * itself; this class only schedules, restores, observes, and removes that one model's artifacts.
 */
class HyMt2BackgroundDownload(
    context: Context,
    private val preferences: FirstRunPreferences,
    private val personalKey: String,
) {
    private val applicationContext = context.applicationContext
    private val operationMutex = Mutex()

    val hasConsent: Boolean
        get() = preferences.modelDownloadConsent

    suspend fun scheduleIfConsented(): ModelDownloadUiState? = withContext(Dispatchers.IO) {
        if (!hasConsent || personalKey.isBlank()) return@withContext null
        val existing = refresh()
        if (existing != null && (existing.isStatusLookupFailure || existing.isActive || existing.isInstalled)) {
            return@withContext existing
        }
        schedule()
    }

    suspend fun grantConsentAndSchedule(): ModelDownloadUiState = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            currentCoroutineContext().ensureActive()
            preferences.modelDownloadConsent = true
            scheduleLocked()
        }
    }

    suspend fun refresh(): ModelDownloadUiState? = withContext(Dispatchers.IO) {
        val handleId = preferences.backgroundDownloadHandleId ?: return@withContext null
        try {
            val status = ZeticMLangeLLMModel.getBackgroundDownloadStatus(
                applicationContext,
                BackgroundDownloadHandle(handleId),
            )
            currentCoroutineContext().ensureActive()
            status.toUiState()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ModelDownloadUiState(
                state = BackgroundDownloadState.FAILED,
                errorMessage = error.message,
                isStatusLookupFailure = true,
            )
        }
    }

    suspend fun removeDownloadedModel(): ModelRemovalResult = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            currentCoroutineContext().ensureActive()
            val result = ZeticMLangeLLMModel.removeDownloadedModel(
                context = applicationContext,
                name = MelangeHyMt2Translator.MODEL_NAME,
            )
            currentCoroutineContext().ensureActive()
            if (!result.isInUse) clearConsentAndHandleLocked()
            result
        }
    }

    private fun clearConsentAndHandleLocked() {
        preferences.modelDownloadConsent = false
        preferences.backgroundDownloadHandleId = null
        preferences.hasEverLoadedModel = false
    }

    private suspend fun schedule(): ModelDownloadUiState = operationMutex.withLock { scheduleLocked() }

    private suspend fun scheduleLocked(): ModelDownloadUiState = try {
        currentCoroutineContext().ensureActive()
        if (!hasConsent || personalKey.isBlank()) throw CancellationException()
        val handle = ZeticMLangeLLMModel.downloadInBackground(
            context = applicationContext,
            personalKey = personalKey,
            name = MelangeHyMt2Translator.MODEL_NAME,
            version = null,
            modelMode = LLMModelMode.RUN_AUTO,
        )
        currentCoroutineContext().ensureActive()
        preferences.backgroundDownloadHandleId = handle.id
        val status = ZeticMLangeLLMModel.getBackgroundDownloadStatus(applicationContext, handle)
        currentCoroutineContext().ensureActive()
        status.toUiState()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        ModelDownloadUiState(
            state = BackgroundDownloadState.FAILED,
            errorMessage = error.message,
            isStatusLookupFailure = true,
        )
    }
}

private fun BackgroundDownloadStatus.toUiState(): ModelDownloadUiState = ModelDownloadUiState(
    state = state,
    progress = totalBytes
        ?.takeIf { it > 0L }
        ?.let { bytesDownloaded.toFloat().div(it).coerceIn(0f, 1f) },
    errorMessage = errorMessage,
)
