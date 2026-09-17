package ai.zetic.realtimetranslate

import android.content.Context
import com.zeticai.mlange.core.background.BackgroundDownloadHandle
import com.zeticai.mlange.core.background.BackgroundDownloadState
import com.zeticai.mlange.core.background.BackgroundDownloadStatus
import com.zeticai.mlange.core.background.ModelRemovalResult
import com.zeticai.mlange.core.model.llm.LLMModelMode
import com.zeticai.mlange.core.model.llm.ZeticMLangeLLMModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
internal interface ModelDownloadSdk {
    fun download(context: Context, personalKey: String): BackgroundDownloadHandle
    fun status(context: Context, handle: BackgroundDownloadHandle): BackgroundDownloadStatus
    fun stop(context: Context, handle: BackgroundDownloadHandle)
    fun remove(context: Context): ModelRemovalResult
}

private object AndroidModelDownloadSdk : ModelDownloadSdk {
    override fun download(context: Context, personalKey: String) =
        ZeticMLangeLLMModel.downloadInBackground(
            context = context,
            personalKey = personalKey,
            name = MelangeHyMt2Translator.MODEL_NAME,
            version = MelangeHyMt2Translator.MODEL_VERSION,
            modelMode = LLMModelMode.RUN_AUTO,
        )

    override fun status(context: Context, handle: BackgroundDownloadHandle) =
        ZeticMLangeLLMModel.getBackgroundDownloadStatus(context, handle)

    override fun stop(context: Context, handle: BackgroundDownloadHandle) {
        ZeticMLangeLLMModel.stopBackgroundDownload(context, handle)
    }

    override fun remove(context: Context) = ZeticMLangeLLMModel.removeDownloadedModel(
        context = context,
        name = MelangeHyMt2Translator.MODEL_NAME,
    )
}

internal class HyMt2BackgroundDownload(
    context: Context,
    private val preferences: ModelDownloadPreferenceStore,
    private val personalKey: String,
    private val sdk: ModelDownloadSdk = AndroidModelDownloadSdk,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val applicationContext = context.applicationContext
    private val operationMutex = Mutex()

    val hasConsent: Boolean
        get() = preferences.modelDownloadConsent

    suspend fun scheduleIfConsented(): ModelDownloadUiState? = withContext(ioDispatcher) {
        if (!hasConsent || personalKey.isBlank()) return@withContext null
        val existing = refresh()
        if (existing != null && (existing.isStatusLookupFailure || existing.isActive || existing.isInstalled)) {
            return@withContext existing
        }
        schedule()
    }

    suspend fun grantConsentAndSchedule(): ModelDownloadUiState = withContext(ioDispatcher) {
        operationMutex.withLock {
            currentCoroutineContext().ensureActive()
            preferences.modelDownloadConsent = true
            scheduleLocked()
        }
    }

    suspend fun refresh(): ModelDownloadUiState? = withContext(ioDispatcher) {
        val handleId = preferences.backgroundDownloadHandleId ?: return@withContext null
        try {
            val status = sdk.status(applicationContext, BackgroundDownloadHandle(handleId))
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

    suspend fun removeDownloadedModel(): ModelRemovalResult = withContext(ioDispatcher) {
        operationMutex.withLock {
            currentCoroutineContext().ensureActive()
            val result = sdk.remove(applicationContext)
            currentCoroutineContext().ensureActive()
            if (!result.isInUse) clearConsentAndHandleLocked()
            result
        }
    }

    /** Stops the SDK-owned durable transfer. A later explicit start may schedule a fresh one. */
    suspend fun cancel() = withContext(ioDispatcher) {
        operationMutex.withLock {
            val handleId = preferences.backgroundDownloadHandleId ?: return@withLock
            sdk.stop(applicationContext, BackgroundDownloadHandle(handleId))
            if (preferences.backgroundDownloadHandleId == handleId) {
                preferences.backgroundDownloadHandleId = null
            }
        }
    }

    private fun clearConsentAndHandleLocked() {
        preferences.modelDownloadConsent = false
        preferences.backgroundDownloadHandleId = null
        preferences.hasEverLoadedModel = false
    }

    private suspend fun schedule(): ModelDownloadUiState = operationMutex.withLock { scheduleLocked() }

    private suspend fun scheduleLocked(): ModelDownloadUiState {
        var createdHandle: BackgroundDownloadHandle? = null
        return try {
            currentCoroutineContext().ensureActive()
            if (!hasConsent || personalKey.isBlank()) throw CancellationException()
            createdHandle = sdk.download(applicationContext, personalKey)
            // Persist ownership before observing cancellation. Once the SDK returns a handle, this
            // coordinator must either retain it for restoration or stop it before letting go.
            preferences.backgroundDownloadHandleId = createdHandle.id
            currentCoroutineContext().ensureActive()
            val status = sdk.status(applicationContext, createdHandle)
            currentCoroutineContext().ensureActive()
            status.toUiState()
        } catch (error: CancellationException) {
            createdHandle?.let { stopCreatedHandle(it) }
            throw error
        } catch (error: Throwable) {
            ModelDownloadUiState(
                state = BackgroundDownloadState.FAILED,
                errorMessage = error.message,
                isStatusLookupFailure = true,
            )
        }
    }

    private suspend fun stopCreatedHandle(handle: BackgroundDownloadHandle) = withContext(NonCancellable) {
        val stopped = runCatching { sdk.stop(applicationContext, handle) }.isSuccess
        if (stopped && preferences.backgroundDownloadHandleId == handle.id) {
            preferences.backgroundDownloadHandleId = null
        }
    }
}

private fun BackgroundDownloadStatus.toUiState(): ModelDownloadUiState = ModelDownloadUiState(
    state = state,
    progress = totalBytes
        ?.takeIf { it > 0L }
        ?.let { bytesDownloaded.toFloat().div(it).coerceIn(0f, 1f) },
    errorMessage = errorMessage,
)
