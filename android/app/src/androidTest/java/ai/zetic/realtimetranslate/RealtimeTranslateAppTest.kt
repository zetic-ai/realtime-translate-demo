package ai.zetic.realtimetranslate

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeTranslateAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun permanentPermissionDenialOffersInjectedAppSettingsAction() {
        var settingsOpened = false
        setApp(
            state = SessionUiState(SessionPhase.PermissionRequired, permissionPermanentlyDenied = true),
            onOpenAppSettings = { settingsOpened = true },
        )

        composeRule.onNodeWithContentDescription("앱 설정 열기").performClick()

        assertTrue(settingsOpened)
    }

    @Test
    fun finishedSessionShowsConversationAndNewSessionControl() {
        var action: UiAction? = null
        setApp(
            state = SessionUiState(
                phase = SessionPhase.Finished,
                conversations = listOf(ConversationItem("1", "화자 1", "안녕하세요", "Hello", isFinal = true)),
            ),
            onAction = { action = it },
        )

        composeRule.onNodeWithText("안녕하세요").assertIsDisplayed()
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("새 세션 시작").performClick()

        assertEquals(UiAction.NewSession, action)
    }

    @Test
    fun translationLanguagePickerExposesHyMt2Languages() {
        var action: UiAction? = null
        setApp(SessionUiState(SessionPhase.Ready), onAction = { action = it })

        composeRule.onNodeWithContentDescription("번역 언어 선택: English").performClick()
        composeRule.onNodeWithText("Cantonese").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(UiAction.SelectOutput(HyMt2Languages.all.last()), action)
    }

    @Test
    fun fakeOnDeviceAdapterInjectsPartialAndFinalTranscriptWithoutFakeTranslation() {
        val adapter = FakeOnDeviceTranscriber()
        val viewModel = SessionViewModel(
            transcriberFactory = { adapter },
            initialState = SessionUiState(SessionPhase.Ready, availableInputLanguages = setOf(SpeechLanguage.Korean)),
        )

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.Start(composeRule.activity))
            adapter.listener.onPartial("hello")
        }
        assertEquals(SessionPhase.Recording, viewModel.state.value.phase)
        assertEquals("hello", viewModel.state.value.conversations.single().transcript)
        assertEquals(null, viewModel.state.value.conversations.single().translation)

        composeRule.runOnUiThread { adapter.listener.onFinal("hello world") }
        assertTrue(viewModel.state.value.conversations.single().isFinal)
        assertEquals("hello world", viewModel.state.value.conversations.single().transcript)
        assertEquals(null, viewModel.state.value.conversations.single().speaker)
    }

    @Test
    fun fakeOnDeviceAdapterSurfacesCapabilityFailure() {
        val viewModel = SessionViewModel(
            transcriberFactory = { FailingOnDeviceTranscriber },
            initialState = SessionUiState(SessionPhase.Ready, availableInputLanguages = setOf(SpeechLanguage.Korean)),
        )

        composeRule.runOnUiThread { viewModel.dispatch(SessionAction.Start(composeRule.activity)) }

        assertEquals(SessionPhase.Error, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.errorMessage?.contains("온디바이스") == true)
    }

    @Test
    fun api33ModelDownloadReturnsToReadyForRestart() {
        val viewModel = SessionViewModel(
            transcriberFactory = { DownloadRequestedTranscriber },
            initialState = SessionUiState(SessionPhase.Ready, availableInputLanguages = setOf(SpeechLanguage.Korean)),
        )

        composeRule.runOnUiThread { viewModel.dispatch(SessionAction.Start(composeRule.activity)) }

        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.modelGateMessage?.contains("다시 시작") == true)
    }

    @Test
    fun unsupportedProbeBlocksSourceLanguageStart() {
        val viewModel = SessionViewModel(
            transcriberFactory = { UnsupportedLanguageTranscriber },
            initialState = SessionUiState(SessionPhase.Ready),
        )

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.ProbeCapabilities(composeRule.activity))
            viewModel.dispatch(SessionAction.Start(composeRule.activity))
        }

        assertEquals(emptySet<SpeechLanguage>(), viewModel.state.value.availableInputLanguages)
        assertEquals(SessionPhase.Error, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.errorMessage?.contains("지원하지") == true)
    }

    @Test
    fun provisionalApi31LanguageCanStartThenSurfacesOnDeviceUnsupportedError() {
        val viewModel = SessionViewModel(
            transcriberFactory = { Api31ProvisionalTranscriber },
            initialState = SessionUiState(SessionPhase.Ready),
        )

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.ProbeCapabilities(composeRule.activity))
            viewModel.dispatch(SessionAction.Start(composeRule.activity))
        }

        assertTrue(SpeechLanguage.Korean in viewModel.state.value.availableInputLanguages)
        assertEquals(SessionPhase.Error, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.errorMessage?.contains("설치할 수 없습니다") == true)
    }

    @Test
    fun supportedButUninstalledLanguageEnablesDownloadAction() {
        val viewModel = SessionViewModel(
            transcriberFactory = { DownloadableLanguageTranscriber },
            initialState = SessionUiState(SessionPhase.Ready),
        )

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.ProbeCapabilities(composeRule.activity))
            viewModel.dispatch(SessionAction.Start(composeRule.activity))
        }

        assertTrue(SpeechLanguage.Korean in viewModel.state.value.downloadableInputLanguages)
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.modelGateMessage?.contains("다시 시작") == true)
    }

    private fun setApp(
        state: SessionUiState,
        onAction: (UiAction) -> Unit = {},
        onOpenAppSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            RealtimeTranslateTheme {
                RealtimeTranslateApp(state, onAction, onOpenAppSettings)
            }
        }
    }

    private class FakeOnDeviceTranscriber : SpeechTranscriber {
        lateinit var listener: SpeechTranscriptListener

        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(languages.toSet()))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            this.listener = listener
            listener.onReady()
            return SpeechStartResult.Started
        }

        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private data object FailingOnDeviceTranscriber : SpeechTranscriber {
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(emptySet(), message = "지원하지 않습니다."))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener) =
            SpeechStartResult.Failed("온디바이스 음성 인식기를 찾을 수 없습니다.")

        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private data object DownloadRequestedTranscriber : SpeechTranscriber {
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(languages.toSet()))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            listener.onModelDownload("한국어 음성 모델 다운로드를 요청했습니다. 완료 후 세션을 다시 시작하세요.", restartRequired = true)
            return SpeechStartResult.Downloading("다운로드 중")
        }

        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private data object UnsupportedLanguageTranscriber : SpeechTranscriber {
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(emptySet(), message = "선택한 언어를 지원하지 않습니다."))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener) = SpeechStartResult.Failed("시작하면 안 됩니다.")
        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private data object Api31ProvisionalTranscriber : SpeechTranscriber {
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(languages.toSet(), message = "언어별 오프라인 지원은 세션 시작 시 확인합니다."))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            listener.onDeviceUnsupported("한국어 온디바이스 음성 모델을 이 기기에서 지원하지 않거나 설치할 수 없습니다.")
            return SpeechStartResult.Started
        }

        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private data object DownloadableLanguageTranscriber : SpeechTranscriber {
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) =
            onComplete(SpeechCapabilityResult(emptySet(), downloadable = setOf(SpeechLanguage.Korean)))

        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            listener.onModelDownload("한국어 음성 모델 다운로드를 요청했습니다. 완료 후 세션을 다시 시작하세요.", restartRequired = true)
            return SpeechStartResult.Downloading("다운로드 중")
        }

        override fun stop() = Unit
        override fun destroy() = Unit
    }
}
