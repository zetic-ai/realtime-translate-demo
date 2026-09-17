package ai.zetic.realtimetranslate

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.zeticai.mlange.core.model.llm.LLMNextTokenResult
import com.zeticai.mlange.core.model.llm.LLMRunResult
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `default speaker settings let a first session start without language taps`() {
        val state = SessionUiState(SessionPhase.Ready)

        assertEquals(SpeechLanguage.Automatic, state.settingsFor(Speaker.A).inputLanguage)
        assertEquals(SpeechLanguage.Automatic, state.settingsFor(Speaker.B).inputLanguage)
        assertEquals("en", state.settingsFor(Speaker.A).readingLanguage.code)
        assertEquals("ko", state.settingsFor(Speaker.B).readingLanguage.code)
    }

    @Test fun `the matcher picks the recognizer for the reading language`() {
        val installed = listOf(SpeechLanguage.Automatic, installed("en-US"), installed("ko-KR"), installed("ja-JP"))

        assertEquals("ko-KR", match("ko", installed)?.languageTag)
        assertEquals("ja-JP", match("ja", installed)?.languageTag)
    }

    @Test fun `the matcher prefers the variant a bare reading code implies`() {
        val installed = listOf(installed("fr-BE"), installed("fr-FR"), installed("zh-CN"), installed("zh-TW"))

        assertEquals("fr-FR", match("fr", installed)?.languageTag)
        assertEquals("zh-CN", match("zh", installed)?.languageTag)
        assertEquals("zh-TW", match("zh-Hant", installed)?.languageTag)
    }

    @Test fun `the matcher takes the only variant even when it is not the implied one`() {
        assertEquals("fr-CA", match("fr", listOf(installed("fr-CA")))?.languageTag)
    }

    @Test fun `a reading language with no installed recognizer has no match`() {
        assertEquals(null, match("th", listOf(SpeechLanguage.Automatic, installed("en-US"))))
    }

    @Test fun `the installed catalog aligns each speaker's spoken language to their chip`() = runTest {
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, installed("en-US"), installed("ko-KR")))
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )

        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        assertEquals("en-US", (viewModel.state.value.settingsFor(Speaker.A).inputLanguage as SpeechLanguage.Installed).languageTag)
        assertEquals("ko-KR", (viewModel.state.value.settingsFor(Speaker.B).inputLanguage as SpeechLanguage.Installed).languageTag)
        assertTrue(catalog.downloadRequests.isEmpty())
    }

    @Test fun `requesting one downloadable language starts only that exact pack and marks it pending`() = runTest {
        val french = SpeechLanguage.Installed("fr-CA", "French (Canada)", SpeechLanguage.OnDeviceStatus.DownloadRequired)
        val spanish = SpeechLanguage.Installed("es-MX", "Spanish (Mexico)", SpeechLanguage.OnDeviceStatus.DownloadRequired)
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, french, spanish))
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        viewModel.dispatch(SessionAction.RequestSpeechModelDownload(TestContext(), french))
        viewModel.dispatch(SessionAction.RequestSpeechModelDownload(TestContext(), french))

        assertEquals(listOf("fr-CA"), catalog.downloadRequests)
        assertEquals(
            SpeechLanguage.OnDeviceStatus.DownloadPending,
            viewModel.state.value.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .first { it.languageTag == "fr-CA" }.onDeviceStatus,
        )
        assertEquals(
            SpeechLanguage.OnDeviceStatus.DownloadRequired,
            viewModel.state.value.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .first { it.languageTag == "es-MX" }.onDeviceStatus,
        )
        assertEquals(SpeechLanguage.Automatic, viewModel.state.value.settingsFor(Speaker.A).inputLanguage)
    }

    @Test fun `completed speech pack becomes ready without restarting the app`() = runTest {
        val downloadable = SpeechLanguage.Installed("fr-CA", "French (Canada)", SpeechLanguage.OnDeviceStatus.DownloadRequired)
        val ready = downloadable.copy(onDeviceStatus = SpeechLanguage.OnDeviceStatus.Ready)
        val catalog = FakeCatalog(
            listOf(SpeechLanguage.Automatic, downloadable),
            requestResult = SpeechModelDownloadResult.Completed,
        )
        catalog.onRequest = { catalog.languages = listOf(SpeechLanguage.Automatic, ready) }
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(
                phase = SessionPhase.Ready,
                conversationStarted = true,
                speechLanguages = listOf(SpeechLanguage.Automatic, downloadable),
            ),
        )

        viewModel.dispatch(SessionAction.RequestSpeechModelDownload(TestContext(), downloadable))

        assertEquals(1, catalog.loadRequests)
        assertEquals(
            SpeechLanguage.OnDeviceStatus.Ready,
            viewModel.state.value.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .first { it.languageTag == "fr-CA" }.onDeviceStatus,
        )
    }

    @Test fun `scheduled speech pack becomes ready on a later foreground catalog recheck`() = runTest {
        val downloadable = SpeechLanguage.Installed("ja-JP", "Japanese (Japan)", SpeechLanguage.OnDeviceStatus.DownloadRequired)
        val ready = downloadable.copy(onDeviceStatus = SpeechLanguage.OnDeviceStatus.Ready)
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, downloadable))
        catalog.onRequest = { catalog.languages = listOf(SpeechLanguage.Automatic, ready) }
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        viewModel.dispatch(SessionAction.RequestSpeechModelDownload(TestContext(), downloadable))

        assertEquals(1, catalog.loadRequests)
        assertEquals(
            SpeechLanguage.OnDeviceStatus.DownloadPending,
            viewModel.state.value.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .first { it.languageTag == "ja-JP" }.onDeviceStatus,
        )

        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        assertEquals(2, catalog.loadRequests)
        assertEquals(
            SpeechLanguage.OnDeviceStatus.Ready,
            viewModel.state.value.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .first { it.languageTag == "ja-JP" }.onDeviceStatus,
        )
    }

    @Test fun `failed speech pack request restores downloadable state and offers settings fallback`() = runTest {
        val french = SpeechLanguage.Installed("fr-FR", "French (France)", SpeechLanguage.OnDeviceStatus.DownloadRequired)
        val catalog = FakeCatalog(
            listOf(SpeechLanguage.Automatic, french),
            requestResult = SpeechModelDownloadResult.Failed,
        )
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        viewModel.dispatch(SessionAction.RequestSpeechModelDownload(TestContext(), french))

        assertEquals(SpeechLanguage.OnDeviceStatus.DownloadRequired, (viewModel.state.value.speechLanguages[1] as SpeechLanguage.Installed).onDeviceStatus)
        assertEquals(UiText.res(R.string.speech_model_download_failed), viewModel.state.value.speechModelDownloadError)
    }

    @Test fun `catalog rechecks preserve a speech pack failure fallback`() = runTest {
        val pending = SpeechLanguage.Installed("ja-JP", "Japanese (Japan)", SpeechLanguage.OnDeviceStatus.DownloadPending)
        val error = UiText.res(R.string.speech_model_download_failed)
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = FakeCatalog(listOf(SpeechLanguage.Automatic, pending)),
            initialState = SessionUiState(
                phase = SessionPhase.Ready,
                speechLanguages = listOf(SpeechLanguage.Automatic, pending),
                speechModelDownloadError = error,
            ),
        )

        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        assertEquals(error, viewModel.state.value.speechModelDownloadError)
    }

    @Test fun `an explicit spoken language survives the catalog arriving`() = runTest {
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, installed("en-US"), installed("ko-KR")))
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )

        viewModel.dispatch(SessionAction.InputLanguageChanged(Speaker.B, installed("en-US")))
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        assertEquals("en-US", (viewModel.state.value.settingsFor(Speaker.B).inputLanguage as SpeechLanguage.Installed).languageTag)
    }

    @Test fun `choosing a reading language re-aligns that speaker's spoken language only`() = runTest {
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, installed("en-US"), installed("ko-KR"), installed("ja-JP")))
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))
        // An explicit override, which the next reading change is allowed to replace.
        viewModel.dispatch(SessionAction.InputLanguageChanged(Speaker.A, installed("ko-KR")))

        viewModel.dispatch(SessionAction.ReadingLanguageChanged(Speaker.A, HyMt2Languages.all.first { it.code == "ja" }))

        assertEquals("ja-JP", (viewModel.state.value.settingsFor(Speaker.A).inputLanguage as SpeechLanguage.Installed).languageTag)
        assertEquals("ko-KR", (viewModel.state.value.settingsFor(Speaker.B).inputLanguage as SpeechLanguage.Installed).languageTag)
    }

    @Test fun `a reading language with no recognizer leaves the spoken language alone`() = runTest {
        val catalog = FakeCatalog(listOf(SpeechLanguage.Automatic, installed("en-US"), installed("ko-KR")))
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            speechLanguageCatalog = catalog,
            initialState = SessionUiState(SessionPhase.Ready),
        )
        viewModel.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        viewModel.dispatch(SessionAction.ReadingLanguageChanged(Speaker.A, HyMt2Languages.all.first { it.code == "th" }))

        assertEquals("en-US", (viewModel.state.value.settingsFor(Speaker.A).inputLanguage as SpeechLanguage.Installed).languageTag)
        assertEquals("th", viewModel.state.value.settingsFor(Speaker.A).readingLanguage.code)
    }

    @Test fun `starts only after the model finishes loading`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        assertEquals(SessionPhase.LoadingModel, viewModel.state.value.phase)
        withTimeout(5_000) { viewModel.state.first { it.phase == SessionPhase.Ready } }

        assertTrue(viewModel.state.value.conversationStarted)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertEquals(1f, viewModel.state.value.modelLoadProgress)
    }

    @Test fun `model loading stays indeterminate until the SDK reports download progress`() = runTest {
        val translator = NonCooperativeLoadTranslator()
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        runCurrent()

        assertEquals(SessionPhase.LoadingModel, viewModel.state.value.phase)
        assertEquals(null, viewModel.state.value.modelLoadProgress)

        translator.progress(0.42f)

        assertEquals(0.42f, viewModel.state.value.modelLoadProgress)
        viewModel.dispatch(SessionAction.CancelModelPreparation)
    }

    @Test fun `model load failure offers retry state without enabling conversation`() = runTest {
        val translator = FakeTranslator(loadError = IllegalStateException("offline"))
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        advanceUntilIdle()

        assertEquals(SessionPhase.ModelLoadFailed, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.conversationStarted)
        assertEquals(null, viewModel.state.value.modelLoadProgress)
        assertEquals(UiText.raw("offline"), viewModel.state.value.errorMessage)
    }

    @Test fun `retry reloads after model load failure`() = runTest {
        val translator = FakeTranslator(loadError = IllegalStateException("offline"))
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        advanceUntilIdle()
        viewModel.dispatch(SessionAction.Retry)
        advanceUntilIdle()

        assertEquals(2, translator.loads)
        assertEquals(SessionPhase.ModelLoadFailed, viewModel.state.value.phase)
    }

    @Test fun `language changes during a live session apply without reloading the model`() = runTest {
        val translator = FakeTranslator()
        val transcriber = DelayedTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { transcriber },
            translator = translator,
            audioInterruptionFactory = { FakeRecordingAudio() },
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )
        val korean = HyMt2Languages.all.first { it.code == "ko" }
        val french = SpeechLanguage.Installed("fr-FR", "French (France)")

        viewModel.dispatch(SessionAction.ReadingLanguageChanged(Speaker.B, korean))
        viewModel.dispatch(SessionAction.InputLanguageChanged(Speaker.A, french))

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.conversationStarted)
        assertEquals(0, translator.loads)

        viewModel.dispatch(SessionAction.PttPress(TestContext(), Speaker.A))
        transcriber.listener.onFinal("hello")
        viewModel.dispatch(SessionAction.PttRelease(Speaker.A))
        transcriber.listener.onStopped()
        advanceUntilIdle()

        assertEquals(french, transcriber.startedLanguage)
        assertEquals("ko", viewModel.state.value.conversations.single().targetLanguage.code)
        assertEquals("translated", viewModel.state.value.conversations.single().translation)
        assertEquals(0, translator.loads)
    }

    @Test fun `ending a session returns to the idle main screen without a separate setup step`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true, conversations = listOf(ConversationItem("partial", Speaker.A, SpeechLanguage.Automatic, HyMt2Languages.all.first(), "partial", false, translationError = UiText.raw("error")))),
        )

        viewModel.dispatch(SessionAction.EndSession)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.conversationStarted)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.conversations.isEmpty())
    }

    @Test fun `ending a session keeps the model resident and the next start loads nothing`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        advanceUntilIdle()
        viewModel.dispatch(SessionAction.EndSession)
        advanceUntilIdle()

        assertFalse(translator.closed)
        assertEquals(0, translator.unloads)

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.conversationStarted)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
    }

    @Test fun `clearing the view model is the only thing that releases the model`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(translator = translator, initialState = SessionUiState(SessionPhase.Ready))

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        advanceUntilIdle()
        viewModel.dispatch(SessionAction.EndSession)
        advanceUntilIdle()
        assertFalse(translator.closed)

        // The one thing that releases the model: the view model itself going away.
        ViewModelStore().apply { put("session", viewModel) }.clear()

        assertTrue(translator.closed)
    }

    @Test fun `a loaded model is reused rather than loaded a second time`() = runTest {
        var built = 0
        val session = object : HyMt2ModelSession {
            override fun run(prompt: String) = LLMRunResult(0)
            override fun waitForNextToken() = LLMNextTokenResult(0, "bonjour", 1, isFinal = true)
            override fun cleanUp() = Unit
            override fun close() = Unit
        }
        val translator = MelangeHyMt2Translator("test") { _, _ -> built += 1; session }

        translator.load(TestContext()) { }
        translator.load(TestContext()) { }

        assertEquals(1, built)
    }

    @Test fun `late speech callbacks after ending cannot restart translation`() = runTest {
        val translator = FakeTranslator()
        val transcriber = DelayedTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { transcriber },
            translator = translator,
            audioInterruptionFactory = { FakeRecordingAudio() },
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )

        viewModel.dispatch(SessionAction.PttPress(TestContext(), Speaker.A))
        assertEquals(SessionPhase.ListeningA, viewModel.state.value.phase)
        viewModel.dispatch(SessionAction.EndSession)
        transcriber.listener.onFinal("late transcript")
        transcriber.listener.onStopped()
        advanceUntilIdle()

        assertFalse(translator.closed)
        assertEquals(0, translator.translations)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.conversationStarted)
        assertTrue(viewModel.state.value.conversations.isEmpty())
    }

    @Test fun `clearing the conversation empties the transcript and leaves everything else alone`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(
                SessionPhase.Ready,
                conversationStarted = true,
                conversations = listOf(bubble("one"), bubble("two")),
            ),
        )
        val languagesBefore = viewModel.state.value.settings

        assertTrue(viewModel.state.value.canClearConversation)
        viewModel.dispatch(SessionAction.ClearConversation)

        assertTrue(viewModel.state.value.conversations.isEmpty())
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.conversationStarted)
        assertEquals(languagesBefore, viewModel.state.value.settings)
        assertFalse(translator.closed)
        assertEquals(0, translator.unloads)
    }

    @Test fun `the clear row is unavailable with nothing to clear and while an utterance is in flight`() {
        val empty = SessionUiState(SessionPhase.Ready, conversationStarted = true)
        val translating = SessionUiState(SessionPhase.TranslatingA, conversationStarted = true, conversations = listOf(bubble("one")))
        val listening = SessionUiState(SessionPhase.ListeningB, conversationStarted = true, conversations = listOf(bubble("one")))

        assertFalse(empty.canClearConversation)
        assertFalse(translating.canClearConversation)
        assertFalse(listening.canClearConversation)
    }

    @Test fun `a clear that arrives mid-utterance is refused rather than stranding the bubble`() = runTest {
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            initialState = SessionUiState(
                SessionPhase.TranslatingA,
                conversationStarted = true,
                conversations = listOf(bubble("one")),
            ),
        )

        viewModel.dispatch(SessionAction.ClearConversation)

        assertEquals(1, viewModel.state.value.conversations.size)
        assertEquals(SessionPhase.TranslatingA, viewModel.state.value.phase)
    }

    @Test fun `retry recovers an active session from a speech error without reloading`() = runTest {
        val translator = FakeTranslator()
        val transcriber = DelayedTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { transcriber },
            translator = translator,
            audioInterruptionFactory = { FakeRecordingAudio() },
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )

        viewModel.dispatch(SessionAction.PttPress(TestContext(), Speaker.A))
        transcriber.listener.onError(UiText.raw("Speech recognition failed."))
        assertEquals(SessionPhase.Error, viewModel.state.value.phase)
        viewModel.dispatch(SessionAction.Retry)

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.conversationStarted)
        assertEquals(0, translator.loads)
    }

    @Test fun `reading and spoken languages survive a new view model`() = runTest {
        val preferences = MemoryLanguagePreferences()
        val languages = listOf(SpeechLanguage.Automatic, installed("en-US"), installed("fr-FR"), installed("ko-KR"))
        val first = SessionViewModel(
            translator = FakeTranslator(),
            languagePreferences = preferences,
            speechLanguageCatalog = FakeCatalog(languages),
            initialState = SessionUiState(SessionPhase.Ready),
        )
        val french = HyMt2Languages.all.first { it.code == "fr" }

        first.dispatch(SessionAction.ReadingLanguageChanged(Speaker.A, french))
        first.dispatch(SessionAction.InputLanguageChanged(Speaker.A, SpeechLanguage.Automatic))
        first.dispatch(SessionAction.InputLanguageChanged(Speaker.B, installed("en-US")))

        val restored = SessionViewModel(
            translator = FakeTranslator(),
            languagePreferences = preferences,
            speechLanguageCatalog = FakeCatalog(languages),
            initialState = SessionUiState(SessionPhase.Ready),
        )
        restored.dispatch(SessionAction.RestoreLanguagePreferences(TestContext()))
        restored.dispatch(SessionAction.RefreshSpeechLanguages(TestContext()))

        assertEquals("fr", restored.state.value.settingsFor(Speaker.A).readingLanguage.code)
        assertEquals(SpeechLanguage.Automatic, restored.state.value.settingsFor(Speaker.A).inputLanguage)
        assertEquals("en-US", (restored.state.value.settingsFor(Speaker.B).inputLanguage as SpeechLanguage.Installed).languageTag)
    }

    @Test fun `typed input enters the normal translation flow`() = runTest {
        val translator = FakeTranslator()
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )

        viewModel.dispatch(SessionAction.SubmitTyped("  hello from keyboard  ", Speaker.B))
        advanceUntilIdle()

        val item = viewModel.state.value.conversations.single()
        assertEquals(Speaker.B, item.speaker)
        assertEquals("hello from keyboard", item.transcript)
        assertEquals("en", item.targetLanguage.code)
        assertEquals("translated", item.translation)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
    }

    @Test fun `a failed bubble can retry without starting a new conversation turn`() = runTest {
        val translator = FakeTranslator()
        val failed = bubble("failed").copy(translation = null, translationError = UiText.raw("offline"))
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(
                SessionPhase.Ready,
                conversationStarted = true,
                conversations = listOf(failed),
            ),
        )

        viewModel.dispatch(SessionAction.RetryTranslation("failed"))
        advanceUntilIdle()

        assertEquals(1, translator.translations)
        assertEquals("translated", viewModel.state.value.conversations.single().translation)
        assertEquals(null, viewModel.state.value.conversations.single().translationError)
    }

    @Test fun `an old noncooperative translation cannot overwrite a newer request with the same bubble id`() = runTest {
        val translator = NonCooperativeTranslationTranslator()
        val failed = bubble("transcript-1").copy(translation = null, translationError = UiText.raw("offline"))
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(
                SessionPhase.Ready,
                conversationStarted = true,
                conversations = listOf(failed),
            ),
        )

        viewModel.dispatch(SessionAction.RetryTranslation(failed.id))
        runCurrent()
        viewModel.dispatch(SessionAction.EndSession)
        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        runCurrent()
        viewModel.dispatch(SessionAction.SubmitTyped("new request", Speaker.A))
        runCurrent()

        assertEquals(2, translator.pending.size)
        translator.complete(index = 0, translation = "stale translation")
        runCurrent()
        assertEquals(SessionPhase.TranslatingA, viewModel.state.value.phase)
        assertEquals(null, viewModel.state.value.conversations.single().translation)

        translator.complete(index = 1, translation = "new translation")
        runCurrent()
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertEquals("new translation", viewModel.state.value.conversations.single().translation)
    }

    @Test fun `cancelling model preparation returns to idle ready`() {
        val viewModel = SessionViewModel(
            translator = FakeTranslator(),
            initialState = SessionUiState(SessionPhase.LoadingModel, modelLoadProgress = 0.5f),
        )

        viewModel.dispatch(SessionAction.CancelModelPreparation)

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.conversationStarted)
        assertEquals(null, viewModel.state.value.modelLoadProgress)
    }

    @Test fun `cancelled noncooperative model load cannot publish progress or success`() = runTest {
        val translator = NonCooperativeLoadTranslator()
        val viewModel = SessionViewModel(
            translator = translator,
            initialState = SessionUiState(SessionPhase.Ready),
        )

        viewModel.dispatch(SessionAction.StartConversation(TestContext()))
        runCurrent()
        assertEquals(SessionPhase.LoadingModel, viewModel.state.value.phase)

        viewModel.dispatch(SessionAction.CancelModelPreparation)
        translator.progress(0.9f)
        assertEquals(null, viewModel.state.value.modelLoadProgress)
        translator.complete()
        runCurrent()

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.conversationStarted)
        assertEquals(null, viewModel.state.value.modelLoadProgress)
    }

    @Test fun `a newer partial keeps the last provisional translation until its replacement lands`() = runTest {
        val transcriber = DelayedTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { transcriber },
            translator = FakeTranslator(),
            audioInterruptionFactory = { FakeRecordingAudio() },
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )

        viewModel.dispatch(SessionAction.PttPress(TestContext(), Speaker.A))
        transcriber.listener.onPartial("first words")
        advanceTimeBy(350)
        runCurrent()
        assertEquals("translated", viewModel.state.value.conversations.single().provisionalTranslation)

        transcriber.listener.onPartial("first words and more")

        assertEquals("translated", viewModel.state.value.conversations.single().provisionalTranslation)
    }

    @Test fun `audio focus loss abandons the active turn and asks the user to retry`() {
        val audio = FakeRecordingAudio()
        val transcriber = DelayedTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { transcriber },
            translator = FakeTranslator(),
            audioInterruptionFactory = { audio },
            initialState = SessionUiState(SessionPhase.Ready, conversationStarted = true),
        )

        viewModel.dispatch(SessionAction.TogglePtt(TestContext(), Speaker.A))
        transcriber.listener.onPartial("interrupted")
        audio.interrupt()

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.conversations.isEmpty())
        assertEquals(UiText.res(R.string.audio_interrupted_notice), viewModel.state.value.notice)
        assertTrue(transcriber.destroyed)
    }

    @Test fun `unload waits for a running translation before closing the model`() = runTest {
        val enteredTokenWait = CountDownLatch(1)
        val releaseTokenWait = CountDownLatch(1)
        val fakeModel = object : HyMt2ModelSession {
            var closed = false
            override fun run(prompt: String) = LLMRunResult(0)
            override fun waitForNextToken(): LLMNextTokenResult {
                enteredTokenWait.countDown()
                releaseTokenWait.await()
                return LLMNextTokenResult(0, "bonjour", 1, isFinal = true)
            }
            override fun cleanUp() = Unit
            override fun close() { closed = true }
        }
        val translator = MelangeHyMt2Translator("test") { _, _ -> fakeModel }
        translator.load(TestContext()) { }
        val translating = async(Dispatchers.Default) { translator.translate("prompt") }
        withContext(Dispatchers.IO) { enteredTokenWait.await() }
        val unloading = async(Dispatchers.Default) { translator.unload() }

        assertFalse(fakeModel.closed)
        releaseTokenWait.countDown()
        translating.await()
        unloading.await()
        assertTrue(fakeModel.closed)
    }

    private fun bubble(id: String) = ConversationItem(
        id = id,
        speaker = Speaker.A,
        sourceLanguage = SpeechLanguage.Automatic,
        targetLanguage = HyMt2Languages.all.first { it.code == "ko" },
        transcript = "hello",
        isFinal = true,
        translation = "annyeong",
    )

    private fun installed(tag: String) = SpeechLanguage.Installed(tag, tag)

    private fun match(readingCode: String, available: List<SpeechLanguage>) =
        SpokenLanguageMatching.match(HyMt2Languages.all.first { it.code == readingCode }, available)

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class FakeCatalog(
        var languages: List<SpeechLanguage>,
        private val requestResult: SpeechModelDownloadResult = SpeechModelDownloadResult.Scheduled,
    ) : SpeechLanguageCatalog {
        val downloadRequests = mutableListOf<String>()
        var loadRequests = 0
        var onRequest: () -> Unit = {}
        override fun load(context: Context, onResult: (SpeechLanguageCatalogResult) -> Unit) {
            loadRequests += 1
            onResult(SpeechLanguageCatalogResult(languages))
        }
        override fun requestDownload(
            context: Context,
            language: SpeechLanguage.Installed,
            onResult: (SpeechModelDownloadResult) -> Unit,
        ): Boolean {
            downloadRequests += language.languageTag
            onRequest()
            onResult(requestResult)
            return true
        }
    }

    private class FakeTranslator(private val loadError: Throwable? = null) : HyMt2Translator {
        var closed = false
        var loads = 0
        var unloads = 0
        var translations = 0
        override suspend fun load(context: Context, onProgress: (Float) -> Unit) {
            loads += 1
            onProgress(0.5f)
            loadError?.let { throw it }
        }
        override suspend fun translate(prompt: String): String {
            translations += 1
            return "translated"
        }
        override suspend fun unload() {
            unloads += 1
            close()
        }
        override fun close() { closed = true }
    }

    private class NonCooperativeLoadTranslator : HyMt2Translator {
        private var progressCallback: ((Float) -> Unit)? = null
        private var continuation: Continuation<Unit>? = null

        override suspend fun load(context: Context, onProgress: (Float) -> Unit) = suspendCoroutine { continuation ->
            progressCallback = onProgress
            this.continuation = continuation
        }

        fun progress(value: Float) = checkNotNull(progressCallback).invoke(value)
        fun complete() {
            val pending = checkNotNull(continuation)
            continuation = null
            pending.resumeWith(Result.success(Unit))
        }

        override suspend fun translate(prompt: String) = "translated"
        override fun close() = Unit
    }

    private class NonCooperativeTranslationTranslator : HyMt2Translator {
        val pending = mutableListOf<Continuation<String>>()

        override suspend fun load(context: Context, onProgress: (Float) -> Unit) = Unit

        override suspend fun translate(prompt: String): String = suspendCoroutine { continuation ->
            pending += continuation
        }

        fun complete(index: Int, translation: String) =
            pending[index].resumeWith(Result.success(translation))

        override fun close() = Unit
    }

    private class DelayedTranscriber : SpeechTranscriber {
        lateinit var listener: SpeechTranscriptListener
        var startedLanguage: SpeechLanguage? = null
        var destroyed = false
        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            this.listener = listener
            startedLanguage = language
            return SpeechStartResult.Started
        }
        override fun stop() = Unit
        override fun destroy() { destroyed = true }
    }

    private class FakeRecordingAudio : RecordingAudioInterruption {
        private var onInterrupted: (() -> Unit)? = null
        override fun start(onInterrupted: () -> Unit): Boolean {
            this.onInterrupted = onInterrupted
            return true
        }
        override fun stop() { onInterrupted = null }
        fun interrupt() = checkNotNull(onInterrupted).invoke()
    }
}
