package ai.zetic.realtimetranslate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeticai.mlange.core.background.BackgroundDownloadState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionAction {
    data class PermissionChanged(val granted: Boolean, val permanentlyDenied: Boolean = false) : SessionAction
    data class RefreshSpeechLanguages(val context: Context) : SessionAction
    data class RestoreLanguagePreferences(val context: Context) : SessionAction
    data class InputLanguageChanged(val speaker: Speaker, val language: SpeechLanguage) : SessionAction
    data class ReadingLanguageChanged(val speaker: Speaker, val language: TranslationLanguage) : SessionAction
    data class PrepareBackgroundDownload(val context: Context) : SessionAction
    data class ScheduleBackgroundDownload(val context: Context) : SessionAction
    data class RemoveDownloadedModel(val context: Context) : SessionAction
    data class StartConversation(val context: Context) : SessionAction
    data object EndSession : SessionAction
    data object CancelModelPreparation : SessionAction
    data object ClearConversation : SessionAction
    data class PttPress(val context: Context, val speaker: Speaker) : SessionAction
    data class PttRelease(val speaker: Speaker) : SessionAction
    data class TogglePtt(val context: Context, val speaker: Speaker) : SessionAction
    data class SubmitTyped(val text: String, val speaker: Speaker) : SessionAction
    data class RetryTranslation(val id: String) : SessionAction
    data object Retry : SessionAction
}

class SessionViewModel(
    private val transcriberFactory: (Context) -> SpeechTranscriber = { AndroidOnDeviceSpeechTranscriber(it) },
    private val translator: HyMt2Translator = MelangeHyMt2Translator(BuildConfig.MELANGE_PERSONAL_KEY),
    private val speechLanguageCatalog: SpeechLanguageCatalog = AndroidSpeechLanguageCatalog(),
    languagePreferences: LanguagePreferenceStore? = null,
    private val audioInterruptionFactory: (Context) -> RecordingAudioInterruption = { AndroidRecordingAudioInterruption(it) },
    initialState: SessionUiState = SessionUiState(SessionPhase.PermissionRequired),
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()
    private var transcriber: SpeechTranscriber? = null
    private var recordingAudio: RecordingAudioInterruption? = null
    private var applicationContext: Context? = null
    private var backgroundDownloader: HyMt2BackgroundDownload? = null
    private var backgroundDownloadRequest: Job? = null
    private var backgroundDownloadPoll: Job? = null
    private var modelLoadJob: Job? = null
    private var translationJob: Job? = null
    private var partialTranslationJob: Job? = null
    private val backgroundDownloadGeneration = AtomicLong()
    private val backgroundDownloadStateLock = Any()
    private val modelLoadGeneration = AtomicLong()
    private val modelLoadStateLock = Any()
    private val translationGeneration = AtomicLong()
    private val modelRemovalInProgress = AtomicBoolean()
    private var languagePreferences: LanguagePreferenceStore? = languagePreferences
    private var storedSpokenTags: Map<Speaker, String?> = emptyMap()
    private var activeItemId: String? = null
    private var nextItemId = initialState.conversations.size.toLong()
    private var partialRevision = 0L
    private var appliedPartialRevision = 0L

    fun dispatch(action: SessionAction) = when (action) {
        is SessionAction.PermissionChanged -> mutableState.value = mutableState.value.copy(
            phase = if (action.granted) SessionPhase.Ready else SessionPhase.PermissionRequired,
            permissionPermanentlyDenied = !action.granted && action.permanentlyDenied,
            errorMessage = null,
        )
        is SessionAction.RefreshSpeechLanguages -> refreshSpeechLanguages(action.context)
        is SessionAction.RestoreLanguagePreferences -> restoreLanguagePreferences(action.context)
        is SessionAction.InputLanguageChanged -> updateSettings(action.speaker) { it.copy(inputLanguage = action.language) }
        is SessionAction.ReadingLanguageChanged -> updateSettings(action.speaker) {
            alignInputLanguage(it.copy(readingLanguage = action.language), mutableState.value.speechLanguages)
        }
        is SessionAction.PrepareBackgroundDownload -> prepareBackgroundDownload(action.context)
        is SessionAction.ScheduleBackgroundDownload -> scheduleBackgroundDownload(action.context)
        is SessionAction.RemoveDownloadedModel -> removeDownloadedModel(action.context)
        is SessionAction.StartConversation -> loadModel(action.context)
        SessionAction.EndSession -> endSession()
        SessionAction.CancelModelPreparation -> cancelModelPreparation()
        SessionAction.ClearConversation -> clearConversation()
        is SessionAction.PttPress -> start(action.context, action.speaker)
        is SessionAction.PttRelease -> stop(action.speaker)
        is SessionAction.TogglePtt -> toggle(action.context, action.speaker)
        is SessionAction.SubmitTyped -> submitTyped(action.text, action.speaker)
        is SessionAction.RetryTranslation -> retryTranslation(action.id)
        SessionAction.Retry -> retry()
    }

    private fun updateSettings(speaker: Speaker, transform: (SpeakerSettings) -> SpeakerSettings) {
        val current = mutableState.value
        val updated = transform(current.settingsFor(speaker))
        mutableState.value = current.copy(settings = current.settings + (speaker to updated))
        languagePreferences?.setReadingCode(speaker, updated.readingLanguage.code)
        languagePreferences?.setSpokenTag(speaker, updated.inputLanguage.preferenceTag)
    }

    private fun restoreLanguagePreferences(context: Context) {
        val store = languagePreferences ?: AndroidLanguagePreferences(context).also { languagePreferences = it }
        storedSpokenTags = Speaker.entries.associateWith(store::spokenTag)
        val current = mutableState.value
        val restored = current.settings.mapValues { (speaker, settings) ->
            val reading = store.readingCode(speaker)
                ?.let { code -> HyMt2Languages.all.firstOrNull { it.code == code } }
                ?: settings.readingLanguage
            settings.copy(readingLanguage = reading)
        }
        mutableState.value = current.copy(settings = restored)
    }

    /**
     * A speaker's chip language drives what the recognizer listens for, so a speaker shown as
     * Korean is listened to in Korean. A reading language with no installed recognizer leaves the
     * spoken language exactly as it was.
     */
    private fun alignInputLanguage(settings: SpeakerSettings, languages: List<SpeechLanguage>): SpeakerSettings {
        val match = SpokenLanguageMatching.match(settings.readingLanguage, languages) ?: return settings
        return settings.copy(inputLanguage = match)
    }

    private fun refreshSpeechLanguages(context: Context) {
        if (mutableState.value.conversationStarted || mutableState.value.speechLanguageCatalogLoading) return
        mutableState.value = mutableState.value.copy(speechLanguageCatalogLoading = true, speechLanguageCatalogMessage = null)
        speechLanguageCatalog.load(context.applicationContext) { result ->
            val validLanguages = result.languages.ifEmpty { listOf(SpeechLanguage.Automatic) }
            val current = mutableState.value
            // The catalog arriving is the first moment a spoken language can be derived at all, so
            // it stands in for init here. A speaker who has explicitly picked a spoken language is
            // left alone; that override survives until their reading language changes again.
            val aligned = current.settings.mapValues { (speaker, settings) ->
                val stored = when (val tag = storedSpokenTags[speaker]) {
                    SpeechLanguage.Automatic.preferenceTag -> SpeechLanguage.Automatic
                    null -> null
                    else -> validLanguages.filterIsInstance<SpeechLanguage.Installed>()
                        .firstOrNull { it.languageTag == tag }
                }
                when {
                    stored != null -> settings.copy(inputLanguage = stored)
                    settings.inputLanguage == SpeechLanguage.Automatic -> alignInputLanguage(settings, validLanguages)
                    else -> settings
                }
            }
            mutableState.value = current.copy(settings = aligned, speechLanguages = validLanguages, speechLanguageCatalogLoading = false, speechLanguageCatalogMessage = result.message)
            aligned.forEach { (speaker, settings) ->
                languagePreferences?.setReadingCode(speaker, settings.readingLanguage.code)
                languagePreferences?.setSpokenTag(speaker, settings.inputLanguage.preferenceTag)
            }
        }
    }

    private fun downloader(context: Context): HyMt2BackgroundDownload {
        applicationContext = context.applicationContext
        return backgroundDownloader ?: HyMt2BackgroundDownload(
            context = context.applicationContext,
            preferences = FirstRunPreferences(context.applicationContext),
            personalKey = BuildConfig.MELANGE_PERSONAL_KEY,
        ).also { backgroundDownloader = it }
    }

    /** Returning users only resume a transfer they have already explicitly approved. */
    private fun prepareBackgroundDownload(context: Context) {
        if (modelRemovalInProgress.get()) return
        val downloader = downloader(context)
        if (!downloader.hasConsent) {
            invalidateBackgroundDownloadWork()
            return
        }
        val generation = invalidateBackgroundDownloadWork()
        backgroundDownloadRequest = viewModelScope.launch {
            val download = downloader.scheduleIfConsented() ?: return@launch
            if (!isCurrentBackgroundDownload(generation)) return@launch
            publishDownloadStatus(download, generation)
            observeBackgroundDownload(downloader, download, generation)
            if (download.isInstalled) loadInstalledModel(generation)
        }
    }

    /** The consent surface is the only caller that can create a new background-download request. */
    private fun scheduleBackgroundDownload(context: Context) {
        if (modelRemovalInProgress.get()) return
        val downloader = downloader(context)
        val generation = invalidateBackgroundDownloadWork()
        runForCurrentBackgroundDownload(generation) {
            mutableState.value = mutableState.value.copy(
                backgroundDownload = ModelDownloadUiState(BackgroundDownloadState.QUEUED),
                modelRemovalMessage = null,
                errorMessage = null,
            )
        }
        backgroundDownloadRequest = viewModelScope.launch {
            val download = downloader.grantConsentAndSchedule()
            if (!isCurrentBackgroundDownload(generation)) return@launch
            publishDownloadStatus(download, generation)
            observeBackgroundDownload(downloader, download, generation)
            if (download.isInstalled) loadInstalledModel(generation)
        }
    }

    private fun observeBackgroundDownload(
        downloader: HyMt2BackgroundDownload,
        initial: ModelDownloadUiState,
        generation: Long,
    ) {
        if (!isCurrentBackgroundDownload(generation)) return
        backgroundDownloadPoll?.cancel()
        if (!initial.isActive) return
        backgroundDownloadPoll = viewModelScope.launch {
            var download = initial
            while (download.isActive) {
                delay(BACKGROUND_DOWNLOAD_POLL_INTERVAL_MILLIS)
                val refreshed = downloader.refresh() ?: break
                if (!isCurrentBackgroundDownload(generation)) return@launch
                download = refreshed
                publishDownloadStatus(download, generation)
            }
            if (download.isInstalled && isCurrentBackgroundDownload(generation)) loadInstalledModel(generation)
        }
    }

    private fun publishDownloadStatus(download: ModelDownloadUiState, generation: Long) {
        runForCurrentBackgroundDownload(generation) {
            mutableState.value = mutableState.value.copy(
                backgroundDownload = download,
                modelRemovalMessage = null,
            )
        }
    }

    private fun loadModel(context: Context) {
        if (modelRemovalInProgress.get()) return
        val current = mutableState.value
        if (current.phase !in setOf(SessionPhase.Ready, SessionPhase.ModelLoadFailed)) return
        applicationContext = context.applicationContext
        // MainActivity creates the download coordinator at launch. Keeping this fallback preserves
        // the existing pure session path for callers that intentionally use the view model alone.
        val downloader = backgroundDownloader ?: run {
            loadInstalledModel()
            return
        }
        if (downloader.hasConsent) {
            val generation = invalidateBackgroundDownloadWork()
            backgroundDownloadRequest = viewModelScope.launch {
                val download = downloader.scheduleIfConsented()
                if (!isCurrentBackgroundDownload(generation)) return@launch
                if (download != null) {
                    publishDownloadStatus(download, generation)
                    observeBackgroundDownload(downloader, download, generation)
                    if (download.isInstalled) loadInstalledModel(generation)
                } else {
                    loadInstalledModel(generation)
                }
            }
            return
        }
        // A production coordinator exists only after MainActivity has restored the persisted
        // consent decision. Do not let an alternate caller bypass that decision.
        return
    }

    /** The SDK has finished its durable transfer, so the existing runtime initialization can run. */
    private fun loadInstalledModel(downloadGeneration: Long? = null) {
        val context = applicationContext ?: return
        if (mutableState.value.phase !in setOf(SessionPhase.Ready, SessionPhase.ModelLoadFailed)) return
        if (downloadGeneration != null && !isCurrentBackgroundDownload(downloadGeneration)) return
        val loadGeneration = invalidateModelLoadWork()
        var modelLoadStarted = false
        runForCurrentModelLoad(loadGeneration, downloadGeneration) {
            val current = mutableState.value
            if (current.phase in setOf(SessionPhase.Ready, SessionPhase.ModelLoadFailed)) {
                mutableState.value = current.copy(phase = SessionPhase.LoadingModel, errorMessage = null, modelLoadProgress = 0f)
                modelLoadStarted = true
            }
        }
        if (!modelLoadStarted) return
        modelLoadJob = viewModelScope.launch {
            runCatching {
                translator.load(context, loadProgress@{ progress ->
                    runForCurrentModelLoad(loadGeneration, downloadGeneration) {
                        mutableState.value = mutableState.value.copy(modelLoadProgress = progress.coerceIn(0f, 1f))
                    }
                })
            }.onSuccess {
                runForCurrentModelLoad(loadGeneration, downloadGeneration) {
                    mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, conversationStarted = true, modelLoadProgress = 1f)
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                runForCurrentModelLoad(loadGeneration, downloadGeneration) {
                    mutableState.value = mutableState.value.copy(phase = SessionPhase.ModelLoadFailed, errorMessage = error.asUiText(R.string.error_model_load_failed))
                }
            }
        }
    }

    private fun cancelModelPreparation() {
        val current = mutableState.value
        if (current.phase != SessionPhase.LoadingModel && current.backgroundDownload?.isActive != true) return
        invalidateModelLoadWork()
        val downloader = backgroundDownloader
        invalidateBackgroundDownloadWork()
        mutableState.value = current.copy(
            phase = SessionPhase.Ready,
            conversationStarted = false,
            backgroundDownload = null,
            modelLoadProgress = 0f,
            errorMessage = null,
        )
        if (downloader != null) viewModelScope.launch { runCatching { downloader.cancel() } }
    }

    private fun removeDownloadedModel(context: Context) {
        val current = mutableState.value
        if (!current.canRemoveDownloadedModel || !modelRemovalInProgress.compareAndSet(false, true)) {
            mutableState.value = current.copy(modelRemovalMessage = UiText.res(R.string.model_removal_in_use))
            return
        }
        val downloader = downloader(context)
        val generation = invalidateBackgroundDownloadWork()
        viewModelScope.launch {
            runCatching {
                // No generating or speech phase reaches this branch, so releasing the idle runtime
                // cannot interrupt a turn. The SDK then removes only its managed model artifacts.
                translator.unload()
                downloader.removeDownloadedModel()
            }.onSuccess { result ->
                runForCurrentBackgroundDownload(generation) {
                    modelRemovalInProgress.set(false)
                    if (result.isInUse) {
                        mutableState.value = mutableState.value.copy(
                            modelRemovalMessage = UiText.res(R.string.model_removal_in_use),
                        )
                    } else {
                        mutableState.value = mutableState.value.copy(
                            phase = SessionPhase.Ready,
                            conversationStarted = false,
                            modelLoadProgress = 0f,
                            backgroundDownload = null,
                            modelRemovalMessage = UiText.res(R.string.model_removal_complete),
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                runForCurrentBackgroundDownload(generation) {
                    modelRemovalInProgress.set(false)
                    mutableState.value = mutableState.value.copy(
                        modelRemovalMessage = UiText.res(R.string.model_removal_failed),
                    )
                }
            }
        }
    }

    private fun invalidateBackgroundDownloadWork(): Long = synchronized(backgroundDownloadStateLock) {
        val generation = backgroundDownloadGeneration.incrementAndGet()
        backgroundDownloadRequest?.cancel()
        backgroundDownloadPoll?.cancel()
        backgroundDownloadRequest = null
        backgroundDownloadPoll = null
        generation
    }

    private fun isCurrentBackgroundDownload(generation: Long) =
        backgroundDownloadGeneration.get() == generation

    /** Pairs a generation check with its UI write so a cancelled worker cannot publish afterward. */
    private fun runForCurrentBackgroundDownload(generation: Long?, update: () -> Unit) {
        if (generation == null) {
            update()
            return
        }
        synchronized(backgroundDownloadStateLock) {
            if (backgroundDownloadGeneration.get() == generation) update()
        }
    }

    private fun invalidateModelLoadWork(): Long = synchronized(modelLoadStateLock) {
        val generation = modelLoadGeneration.incrementAndGet()
        modelLoadJob?.cancel()
        modelLoadJob = null
        generation
    }

    private fun runForCurrentModelLoad(
        loadGeneration: Long,
        downloadGeneration: Long?,
        update: () -> Unit,
    ) = synchronized(modelLoadStateLock) {
        if (modelLoadGeneration.get() == loadGeneration) {
            runForCurrentBackgroundDownload(downloadGeneration, update)
        }
    }

    private fun start(context: Context, speaker: Speaker) {
        val current = mutableState.value
        if (!current.conversationStarted || current.phase != SessionPhase.Ready) return
        val audio = audioInterruptionFactory(context.applicationContext)
        if (!audio.start(::handleAudioInterruption)) {
            mutableState.value = current.copy(notice = UiText.res(R.string.audio_interrupted_notice))
            return
        }
        recordingAudio?.stop()
        recordingAudio = audio
        val newTranscriber = transcriberFactory(context.applicationContext)
        transcriber?.destroy()
        transcriber = newTranscriber
        val item = ConversationItem(
            id = "transcript-${nextItemId++}",
            speaker = speaker,
            sourceLanguage = current.settingsFor(speaker).inputLanguage,
            targetLanguage = current.settingsFor(speaker.other()).readingLanguage,
            transcript = "",
            isFinal = false,
        )
        activeItemId = item.id
        mutableState.value = current.copy(
            phase = finalizingPhase(speaker),
            conversations = current.conversations + item,
            errorMessage = null,
            notice = null,
        )
        when (val result = newTranscriber.start(current.settingsFor(speaker).inputLanguage, transcriptListener(speaker, newTranscriber))) {
            SpeechStartResult.Started -> listening(speaker, newTranscriber)
            is SpeechStartResult.Failed -> fail(result.message)
        }
    }

    private fun toggle(context: Context, speaker: Speaker) = when (mutableState.value.phase) {
        listeningPhase(speaker) -> stop(speaker)
        SessionPhase.Ready -> start(context, speaker)
        else -> Unit
    }

    private fun listening(speaker: Speaker, owner: SpeechTranscriber) {
        val current = mutableState.value
        if (transcriber !== owner || current.phase !in setOf(finalizingPhase(speaker), listeningPhase(speaker))) return
        mutableState.value = current.copy(phase = listeningPhase(speaker))
    }

    private fun transcriptListener(speaker: Speaker, owner: SpeechTranscriber) = object : SpeechTranscriptListener {
        override fun onReady() = listening(speaker, owner)
        override fun onPartial(transcript: String) = updateTranscript(speaker, transcript, false, owner)
        override fun onFinal(transcript: String) = updateTranscript(speaker, transcript, true, owner)
        override fun onStopped() {
            if (transcriber !== owner) return
            owner.destroy()
            recordingAudio?.stop()
            recordingAudio = null
            finalize(speaker, owner)
        }
        override fun onError(message: UiText) = fail(message, owner)
    }

    private fun updateTranscript(speaker: Speaker, transcript: String, isFinal: Boolean, owner: SpeechTranscriber) {
        val current = mutableState.value
        if (transcriber !== owner || current.activeSpeaker() != speaker) return
        val id = activeItemId ?: return
        val existing = current.conversations.firstOrNull { it.id == id } ?: return
        val item = existing.copy(
            transcript = transcript,
            isFinal = isFinal,
            // Keep the last valid translation visible while the debounce/model pass catches up.
            // The pass still carries its source text and revision, so a stale result cannot land.
            provisionalTranslation = existing.provisionalTranslation,
        )
        mutableState.value = current.copy(conversations = current.conversations.map { if (it.id == id) item else it })
        if (!isFinal && transcript.isNotBlank()) schedulePartialTranslation(item)
    }

    private fun schedulePartialTranslation(item: ConversationItem) {
        val revision = ++partialRevision
        val pass = PartialTranslationPass(item.id, revision, item.transcript)
        partialTranslationJob?.cancel()
        partialTranslationJob = viewModelScope.launch {
            delay(PARTIAL_TRANSLATION_DEBOUNCE_MILLIS)
            val prompt = HyMt2TranslationRequestBuilder.build(pass.sourceText, item.targetLanguage)
            val translated = runCatching { translator.translate(prompt) }.getOrNull()?.trim().orEmpty()
            if (translated.isEmpty()) return@launch
            val current = mutableState.value
            val currentItem = current.conversations.firstOrNull { it.id == pass.itemId }
            if (!LivePartialTranslationGuard.shouldApply(pass, activeItemId, currentItem, appliedPartialRevision)) return@launch
            appliedPartialRevision = pass.revision
            mutableState.value = current.copy(
                conversations = current.conversations.map {
                    if (it.id == pass.itemId) it.copy(provisionalTranslation = translated) else it
                },
            )
        }
    }

    private fun stop(speaker: Speaker) {
        if (mutableState.value.phase != listeningPhase(speaker)) return
        mutableState.value = mutableState.value.copy(phase = finalizingPhase(speaker))
        transcriber?.stop()
    }

    private fun finalize(speaker: Speaker, owner: SpeechTranscriber) {
        val current = mutableState.value
        if (transcriber !== owner || current.phase != finalizingPhase(speaker)) return
        val itemId = activeItemId
        val item = current.conversations.firstOrNull { it.id == itemId && it.speaker == speaker && it.isFinal }
        if (item == null || item.transcript.isBlank()) {
            transcriber = null
            endLiveTranslation()
            mutableState.value = current.copy(
                phase = SessionPhase.Ready,
                conversations = current.conversations.filterNot { it.id == itemId },
                notice = if (item?.transcript?.isBlank() == true) UiText.res(R.string.no_speech_notice) else current.notice,
            )
            return
        }
        transcriber = null
        mutableState.value = current.copy(phase = translatingPhase(speaker))
        startFinalTranslation(item)
    }

    private fun submitTyped(text: String, speaker: Speaker) {
        val transcript = text.trim()
        val current = mutableState.value
        if (transcript.isEmpty() || !current.conversationStarted || current.phase != SessionPhase.Ready) return
        val item = ConversationItem(
            id = "transcript-${nextItemId++}",
            speaker = speaker,
            sourceLanguage = current.settingsFor(speaker).inputLanguage,
            targetLanguage = current.settingsFor(speaker.other()).readingLanguage,
            transcript = transcript,
            isFinal = true,
        )
        activeItemId = item.id
        mutableState.value = current.copy(
            phase = translatingPhase(speaker),
            conversations = current.conversations + item,
            notice = null,
        )
        startFinalTranslation(item)
    }

    private fun retryTranslation(id: String) {
        val current = mutableState.value
        if (!current.conversationStarted || current.phase != SessionPhase.Ready) return
        val item = current.conversations.firstOrNull { it.id == id && it.translationError != null } ?: return
        activeItemId = item.id
        mutableState.value = current.copy(
            phase = translatingPhase(item.speaker),
            conversations = current.conversations.map {
                if (it.id == id) it.copy(translation = null, provisionalTranslation = null, translationError = null) else it
            },
            notice = null,
        )
        startFinalTranslation(item.copy(translation = null, provisionalTranslation = null, translationError = null))
    }

    private fun startFinalTranslation(item: ConversationItem) {
        val prompt = HyMt2TranslationRequestBuilder.build(item.transcript, item.targetLanguage)
        val requestGeneration = translationGeneration.incrementAndGet()
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            runCatching { translator.translate(prompt) }
                .onSuccess { completeTranslation(item.id, requestGeneration, it) }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        failTranslation(item.id, requestGeneration, error.asUiText(R.string.error_translation_failed))
                    }
                }
        }
    }

    private fun completeTranslation(id: String, requestGeneration: Long, translation: String) {
        val current = mutableState.value
        if (!current.conversationStarted || activeItemId != id || translationGeneration.get() != requestGeneration) return
        endLiveTranslation()
        mutableState.value = current.copy(phase = SessionPhase.Ready, conversations = current.conversations.map { if (it.id == id) it.copy(translation = translation, provisionalTranslation = null, translationError = null) else it })
    }
    private fun failTranslation(id: String, requestGeneration: Long, message: UiText) {
        val current = mutableState.value
        if (!current.conversationStarted || activeItemId != id || translationGeneration.get() != requestGeneration) return
        endLiveTranslation()
        mutableState.value = current.copy(phase = SessionPhase.Ready, conversations = current.conversations.map { if (it.id == id) it.copy(provisionalTranslation = null, translationError = message) else it })
    }
    private fun fail(message: UiText, owner: SpeechTranscriber? = transcriber) {
        if (owner !== transcriber || !mutableState.value.conversationStarted) return
        owner?.destroy()
        transcriber = null
        recordingAudio?.stop()
        recordingAudio = null
        val id = activeItemId
        endLiveTranslation()
        mutableState.value = mutableState.value.copy(
            phase = SessionPhase.Error,
            conversations = mutableState.value.conversations.filterNot { it.id == id },
            errorMessage = message,
        )
    }
    /**
     * Ending a session stops recognition and clears the conversation but keeps the model resident,
     * so the next Start conversation reaches Ready without loading again. The model is released in
     * [onCleared], when the view model itself goes away.
     */
    private fun endSession() {
        if (mutableState.value.phase == SessionPhase.LoadingModel || mutableState.value.backgroundDownload?.isActive == true) {
            cancelModelPreparation()
            return
        }
        val activeTranscriber = transcriber
        transcriber = null
        activeTranscriber?.destroy()
        recordingAudio?.stop()
        recordingAudio = null
        translationGeneration.incrementAndGet()
        translationJob?.cancel()
        translationJob = null
        endLiveTranslation()
        mutableState.value = mutableState.value.copy(phase = SessionPhase.Ready, conversationStarted = false, conversations = emptyList(), errorMessage = null, notice = null, modelLoadProgress = 0f)
    }
    /**
     * Empties the transcript without ending the session: the model stays resident, both language
     * chips stay as they are, the session phase is untouched, and the next turn starts straight
     * away. Guarded by the same rule the drawer row is disabled under, so a late tap on a row that
     * has just become unavailable cannot strand an in-flight bubble.
     */
    private fun clearConversation() {
        val current = mutableState.value
        if (!current.canClearConversation) return
        mutableState.value = current.copy(conversations = emptyList())
    }

    private fun handleAudioInterruption() {
        val current = mutableState.value
        if (AudioInterruptionPolicy.response(current) != AudioInterruptionResponse.AbandonUtterance) return
        val id = activeItemId
        val activeTranscriber = transcriber
        transcriber = null
        activeTranscriber?.destroy()
        recordingAudio?.stop()
        recordingAudio = null
        endLiveTranslation()
        mutableState.value = current.copy(
            phase = SessionPhase.Ready,
            conversations = current.conversations.filterNot { it.id == id },
            errorMessage = null,
            notice = UiText.res(R.string.audio_interrupted_notice),
        )
    }

    private fun endLiveTranslation() {
        partialTranslationJob?.cancel()
        partialTranslationJob = null
        activeItemId = null
    }

    private fun retry() {
        val current = mutableState.value
        when {
            current.backgroundDownload?.state in setOf(BackgroundDownloadState.FAILED, BackgroundDownloadState.STOPPED) ->
                applicationContext?.let(::prepareBackgroundDownload)
            current.phase == SessionPhase.ModelLoadFailed -> applicationContext?.let(::loadModel)
            current.phase == SessionPhase.Error && current.conversationStarted -> {
                mutableState.value = current.copy(phase = SessionPhase.Ready, errorMessage = null)
            }
        }
    }
    override fun onCleared() {
        invalidateBackgroundDownloadWork()
        invalidateModelLoadWork()
        translationGeneration.incrementAndGet()
        translationJob?.cancel()
        partialTranslationJob?.cancel()
        transcriber?.destroy()
        transcriber = null
        recordingAudio?.stop()
        recordingAudio = null
        translator.close()
    }

    private companion object {
        const val BACKGROUND_DOWNLOAD_POLL_INTERVAL_MILLIS = 1_000L
        const val PARTIAL_TRANSLATION_DEBOUNCE_MILLIS = 350L
    }
}

/**
 * The sentence a failure shows. A [TranslationFailure] already carries its own, and anything else
 * is a runtime or SDK error this app did not write and cannot translate, so its message is passed
 * through as-is and the generic line stands in when there is none.
 */
private fun Throwable.asUiText(fallback: Int): UiText =
    (this as? TranslationFailure)?.text ?: message?.takeIf { it.isNotBlank() }?.let(UiText::raw) ?: UiText.res(fallback)

private fun listeningPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.ListeningA else SessionPhase.ListeningB
private fun finalizingPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.FinalizingA else SessionPhase.FinalizingB
private fun translatingPhase(speaker: Speaker) = if (speaker == Speaker.A) SessionPhase.TranslatingA else SessionPhase.TranslatingB
