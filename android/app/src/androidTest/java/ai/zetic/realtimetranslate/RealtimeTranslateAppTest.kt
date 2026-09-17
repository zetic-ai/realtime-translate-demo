package ai.zetic.realtimetranslate

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeticai.mlange.core.background.BackgroundDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeTranslateAppTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun permanentPermissionDenialOffersAppSettings() {
        var opened = false
        setApp(SessionUiState(SessionPhase.PermissionRequired, permissionPermanentlyDenied = true), onOpenAppSettings = { opened = true })
        composeRule.onNodeWithContentDescription("Open app settings").performClick()
        assertEquals(true, opened)
    }

    @Test fun aListeningDisablesBAndShowsPartialCard() {
        setApp(readyConversationState().copy(phase = SessionPhase.ListeningA, conversations = listOf(item(Speaker.A, "Hello", false))))
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop speaker A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Speaker B cannot start while speaker A is active").assertIsNotEnabled()
    }

    @Test fun tapAlternativeDispatchesToggle() {
        var action: UiAction? = null
        setApp(readyConversationState(), onAction = { action = it })
        composeRule.onNodeWithContentDescription("Start speaker A").performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(UiAction.TogglePtt(Speaker.A), action)
    }

    @Test fun finalBubbleDisplaysSpeakerTargetAndTranslationError() {
        val state = readyConversationState().copy(conversations = listOf(item(Speaker.B, "hello", true).copy(translationError = UiText.raw("Hy-MT2 runtime verification is incomplete."))))
        setApp(state)
        composeRule.onNodeWithText("Speaker B").assertIsDisplayed()
        composeRule.onNodeWithText("To A - English").assertIsDisplayed()
        composeRule.onNodeWithText("Hy-MT2 runtime verification is incomplete.").assertIsDisplayed()
    }

    @Test fun chatBubblesAlignLeftForAAndRightForB() {
        setApp(
            readyConversationState().copy(
                conversations = listOf(
                    item(Speaker.A, "left side", true).copy(id = "a"),
                    item(Speaker.B, "right side", true).copy(id = "b"),
                ),
            ),
        )

        val a = composeRule.onNodeWithContentDescription("Speaker A utterance").fetchSemanticsNode().boundsInRoot
        val b = composeRule.onNodeWithContentDescription("Speaker B utterance").fetchSemanticsNode().boundsInRoot

        assertTrue(a.left < b.left)
        assertTrue(a.right < b.right)
    }

    @Test fun queuedBubbleAnnouncesTranslationPending() {
        setApp(readyConversationState().copy(conversations = listOf(item(Speaker.A, "hello", true))))
        composeRule.onNodeWithText("Translation pending").assertIsDisplayed()
    }

    @Test fun conversationCardsScrollWithoutMovingSessionControls() {
        val cards = (1..6).map { index ->
            item(Speaker.A, "Conversation card $index: " + "long transcript ".repeat(24), true).copy(id = "card-$index")
        }
        setApp(readyConversationState().copy(conversations = cards))

        val history = composeRule.onNode(hasScrollAction())
        repeat(12) { history.performTouchInput { swipeDown() } }
        composeRule.onNodeWithText("Conversation card 1:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start speaker A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start speaker B").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End session").assertIsDisplayed()
        repeat(12) { history.performTouchInput { swipeUp() } }
        composeRule.onNodeWithText("Conversation card 6:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End session").assertIsDisplayed()
    }

    @Test fun appendedConversationCardAutoScrollsToNewest() {
        val conversations = (1..4).map { index ->
            item(Speaker.A, "Existing card $index: " + "long transcript ".repeat(24), true).copy(id = "existing-$index")
        }
        val state = mutableStateOf(readyConversationState().copy(conversations = conversations))
        composeRule.setContent { RealtimeTranslateTheme { RealtimeTranslateApp(state.value, {}) } }

        composeRule.runOnUiThread {
            state.value = state.value.copy(conversations = state.value.conversations + item(Speaker.B, "Newest appended card: " + "long transcript ".repeat(24), true).copy(id = "newest"))
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Newest appended card:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(CONVERSATION_BOTTOM_TAG).assertIsDisplayed()
    }

    @Test fun headerCarriesTheTitleAndTheZeticWordmark() {
        setApp(SessionUiState(SessionPhase.Ready))
        composeRule.onNodeWithText("Turn Translate").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()

        val title = composeRule.onNodeWithText("Turn Translate").fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
        assertTrue(title.right < settings.left)
        assertTrue(settings.width >= 44 * composeRule.activity.resources.displayMetrics.density)
    }

    @Test fun topLanguageBarShowsOneChipPerSpeakerMirroringBubbleSides() {
        setApp(SessionUiState(SessionPhase.Ready))
        composeRule.onNodeWithContentDescription(CHIP_A).assertIsEnabled()
        composeRule.onNodeWithContentDescription(CHIP_B).assertIsEnabled()
        composeRule.onNodeWithText("A · English").assertIsDisplayed()
        composeRule.onNodeWithText("B · Korean").assertIsDisplayed()

        val a = composeRule.onNodeWithContentDescription(CHIP_A).fetchSemanticsNode().boundsInRoot
        val b = composeRule.onNodeWithContentDescription(CHIP_B).fetchSemanticsNode().boundsInRoot
        assertTrue(a.left < b.left)
    }

    @Test fun speakerChipMenuOffersReadingAndSpokenSections() {
        var action: UiAction? = null
        setApp(SessionUiState(SessionPhase.Ready), onAction = { action = it })

        composeRule.onNodeWithContentDescription(CHIP_A).performClick()
        composeRule.onNodeWithText("Reading language").assertExists()
        composeRule.onNodeWithText("Spoken language").assertExists()
        composeRule.onNodeWithText("Automatic (device recognizer)").assertExists()
        composeRule.onNodeWithText("French").performScrollTo().performClick()

        assertEquals(UiAction.SelectReading(Speaker.A, HyMt2Languages.all.first { it.code == "fr" }), action)
    }

    @Test fun downloadableSpeechPackRequestsOnlyTheChosenLanguage() {
        var action: UiAction? = null
        val french = SpeechLanguage.Installed(
            "fr-CA",
            "French (Canada)",
            SpeechLanguage.OnDeviceStatus.DownloadRequired,
        )
        setApp(
            SessionUiState(SessionPhase.Ready, speechLanguages = listOf(SpeechLanguage.Automatic, french)),
            onAction = { action = it },
        )

        composeRule.onNodeWithContentDescription(CHIP_A).performClick()
        composeRule.onNodeWithText("Available to download").assertIsDisplayed()
        composeRule.onNodeWithText("Download speech pack").assertIsDisplayed()
        composeRule.onNodeWithText("French (Canada)").performClick()

        assertEquals(UiAction.RequestSpeechModelDownload(french), action)
    }

    @Test fun readySpeechPackIsVisibleAndSelectable() {
        var action: UiAction? = null
        val spanish = SpeechLanguage.Installed("es-MX", "Spanish (Mexico)")
        setApp(
            SessionUiState(SessionPhase.Ready, speechLanguages = listOf(SpeechLanguage.Automatic, spanish)),
            onAction = { action = it },
        )

        composeRule.onNodeWithContentDescription(CHIP_A).performClick()
        composeRule.onNodeWithText("Spanish (Mexico)").assertIsDisplayed().performClick()

        assertEquals(UiAction.SelectInput(Speaker.A, spanish), action)
    }

    @Test fun pendingSpeechPackIsGroupedAndCannotRequestAgain() {
        var actions = 0
        val japanese = SpeechLanguage.Installed(
            "ja-JP",
            "Japanese (Japan)",
            SpeechLanguage.OnDeviceStatus.DownloadPending,
        )
        setApp(
            SessionUiState(SessionPhase.Ready, speechLanguages = listOf(SpeechLanguage.Automatic, japanese)),
            onAction = { actions += 1 },
        )

        composeRule.onNodeWithContentDescription(CHIP_A).performClick()
        composeRule.onNodeWithText("Downloading").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading speech pack").assertIsDisplayed()
        composeRule.onNodeWithText("Japanese (Japan)").assertIsNotEnabled()

        assertEquals(0, actions)
    }

    @Test fun pendingSpeechPackRechecksStopWhenThePackBecomesReady() {
        val pending = SpeechLanguage.Installed(
            "ja-JP",
            "Japanese (Japan)",
            SpeechLanguage.OnDeviceStatus.DownloadPending,
        )
        val state = mutableStateOf(
            SessionUiState(SessionPhase.Ready, speechLanguages = listOf(SpeechLanguage.Automatic, pending)),
        )
        val actions = mutableListOf<UiAction>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RefreshPendingSpeechPacks(state.value, actions::add)
        }

        composeRule.mainClock.advanceTimeBy(SPEECH_PACK_RECHECK_INTERVAL_MILLIS + 1)
        composeRule.waitForIdle()
        assertEquals(listOf(UiAction.RefreshSpeechLanguages), actions)

        composeRule.runOnUiThread {
            state.value = state.value.copy(
                speechLanguages = listOf(
                    SpeechLanguage.Automatic,
                    pending.copy(onDeviceStatus = SpeechLanguage.OnDeviceStatus.Ready),
                ),
            )
        }
        composeRule.waitForIdle()
        val stoppedAt = actions.size
        composeRule.mainClock.advanceTimeBy(SPEECH_PACK_RECHECK_INTERVAL_MILLIS * 2)
        composeRule.waitForIdle()

        assertEquals(stoppedAt, actions.size)
    }

    @Test fun pendingSpeechPackRechecksAreBounded() {
        val pending = SpeechLanguage.Installed(
            "ja-JP",
            "Japanese (Japan)",
            SpeechLanguage.OnDeviceStatus.DownloadPending,
        )
        val actions = mutableListOf<UiAction>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RefreshPendingSpeechPacks(
                SessionUiState(SessionPhase.Ready, speechLanguages = listOf(SpeechLanguage.Automatic, pending)),
                actions::add,
            )
        }

        composeRule.mainClock.advanceTimeBy(
            SPEECH_PACK_RECHECK_INTERVAL_MILLIS * (SPEECH_PACK_RECHECK_ATTEMPTS + 2),
        )
        composeRule.waitForIdle()

        assertEquals(SPEECH_PACK_RECHECK_ATTEMPTS, actions.size)
    }

    @Test fun failedSpeechPackRequestOffersVoiceInputSettingsFallback() {
        var settingsOpened = false
        setApp(
            SessionUiState(
                SessionPhase.Ready,
                speechModelDownloadError = UiText.res(R.string.speech_model_download_failed),
            ),
            onOpenVoiceInputSettings = { settingsOpened = true },
        )

        composeRule.onNodeWithContentDescription(CHIP_A).performClick()
        composeRule.onNodeWithText("Android could not start this speech pack download.").assertIsDisplayed()
        composeRule.onNodeWithText("Open voice input settings").performClick()

        assertTrue(settingsOpened)
    }

    @Test fun bottomBarHoldsOnlyThePushToTalkControlsAndSessionAction() {
        setApp(readyConversationState())
        composeRule.onNodeWithContentDescription("Start speaker A").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Start speaker B").assertIsEnabled()
        composeRule.onNodeWithContentDescription("End session").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Type a message").assertIsEnabled()
        composeRule.onNodeWithText("Speaks").assertDoesNotExist()
        composeRule.onNodeWithText("Reads").assertDoesNotExist()
    }

    @Test fun typedInputSelectsSpeakerAndDispatchesTrimmedText() {
        var action: UiAction? = null
        setApp(readyConversationState(), onAction = { action = it })

        composeRule.onNodeWithContentDescription("Type a message").performClick()
        composeRule.onNodeWithText("Who is speaking?").assertIsDisplayed()
        composeRule.onNodeWithText("B", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Message").performTextInput("  typed hello  ")
        composeRule.onNodeWithText("Send").performClick()

        assertEquals(UiAction.SubmitTyped("typed hello", Speaker.B), action)
    }

    @Test fun typedInputRemembersSpeakerFocusesFieldAndNamesTheTypingLanguage() {
        var action: UiAction? = null
        setApp(readyConversationState(), onAction = { action = it })

        composeRule.onNodeWithContentDescription("Type a message").performClick()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNodeWithText("B", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Type a message").performClick()
        composeRule.onNodeWithText("What speaker B wants to say in Korean").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().performTextInput("again")
        composeRule.onNodeWithText("Send").performClick()

        assertEquals(UiAction.SubmitTyped("again", Speaker.B), action)
    }

    @Test fun failedBubbleOffersPerBubbleRetry() {
        var action: UiAction? = null
        val failed = item(Speaker.A, "hello", true).copy(translationError = UiText.raw("offline"))
        setApp(readyConversationState().copy(conversations = listOf(failed)), onAction = { action = it })

        composeRule.onNodeWithText("Retry translation").performClick()

        assertEquals(UiAction.RetryTranslation(failed.id), action)
    }

    @Test fun modelPreparationCanBeCancelled() {
        var action: UiAction? = null
        setApp(SessionUiState(SessionPhase.LoadingModel, modelLoadProgress = 0.5f), onAction = { action = it })

        composeRule.onAllNodesWithContentDescription("Cancel")[0].performClick()

        assertEquals(UiAction.CancelModelPreparation, action)
    }

    @Test fun idleMainScreenStartsInOneTapAndKeepsPushToTalkLocked() {
        setApp(SessionUiState(SessionPhase.Ready))
        composeRule.onNodeWithContentDescription("Start conversation").assertIsEnabled()
        composeRule.onNodeWithText("Tap Start conversation to load the translation model.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Speaker A push-to-talk unlocks when the translation model is ready").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Speaker B push-to-talk unlocks when the translation model is ready").assertIsNotEnabled()
    }

    @Test fun errorStateDefersTheBottomHintToTheBanner() {
        setApp(readyConversationState().copy(phase = SessionPhase.Error, errorMessage = UiText.raw("Speech recognition failed.")))
        composeRule.onNodeWithText("Speech recognition failed.").assertIsDisplayed()
        composeRule.onNodeWithText("Resolve the error above to continue.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Try again").assertIsEnabled()
    }

    @Test fun modelLoadingRendersInlineAndLocksLanguageChips() {
        setApp(SessionUiState(SessionPhase.LoadingModel, modelLoadProgress = 0.5f))
        composeRule.onNodeWithText("Model download in progress 50%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(CHIP_A).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(CHIP_B).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Speaker A push-to-talk unlocks when the translation model is ready").assertIsNotEnabled()
    }

    @Test fun cachedModelLoadShowsIndeterminateLoadingWithoutDownloadPercentage() {
        setApp(SessionUiState(SessionPhase.LoadingModel))

        composeRule.onNodeWithText("Loading translation model").assertIsDisplayed()
        composeRule.onNodeWithText("Loading translation model 0%").assertDoesNotExist()
        composeRule.onNodeWithText("Model download in progress 0%").assertDoesNotExist()
        composeRule.onNodeWithText("Downloading translation model 0%").assertDoesNotExist()
    }

    @Test fun unknownDownloadProgressDoesNotMisrepresentItAsZeroPercent() {
        setApp(
            SessionUiState(
                SessionPhase.Ready,
                backgroundDownload = ModelDownloadUiState(BackgroundDownloadState.DOWNLOADING),
            ),
        )

        composeRule.onAllNodesWithText("Model download in progress")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Model download in progress 0%").assertDoesNotExist()
    }

    @Test fun knownDownloadProgressKeepsItsNumericPercentage() {
        setApp(
            SessionUiState(
                SessionPhase.Ready,
                backgroundDownload = ModelDownloadUiState(BackgroundDownloadState.DOWNLOADING, progress = 0.42f),
            ),
        )

        composeRule.onNodeWithText("Model download in progress 42%").assertIsDisplayed()
    }

    @Test fun modelLoadFailureOffersInlineRetryOnTheMainScreen() {
        var action: UiAction? = null
        setApp(SessionUiState(SessionPhase.ModelLoadFailed, errorMessage = UiText.raw("offline")), onAction = { action = it })
        composeRule.onNodeWithText("offline").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry model load").performClick()
        assertEquals(UiAction.Retry, action)
    }

    @Test fun languageChipsAreLockedWhileAnUtteranceIsActive() {
        setApp(readyConversationState().copy(phase = SessionPhase.TranslatingA))
        composeRule.onNodeWithContentDescription(CHIP_A).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(CHIP_B).assertIsNotEnabled()
    }

    @Test fun liveSessionKeepsLanguageChipsEditable() {
        setApp(readyConversationState())
        composeRule.onNodeWithContentDescription(CHIP_A).assertIsEnabled()
        composeRule.onNodeWithContentDescription(CHIP_B).assertIsEnabled()
    }

    @Test fun recognitionIntentUsesOfflineSettingsAndExplicitLanguage() {
        val explicit = OnDeviceRecognitionIntentFactory.create(SpeechLanguage.Installed("fr-FR", "French (France)"), 35)
        val automatic = OnDeviceRecognitionIntentFactory.create(SpeechLanguage.Automatic, 34)
        assertTrue(explicit.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("fr-FR", explicit.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(automatic.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false))

        val korean = OnDeviceRecognitionIntentFactory.create(
            SpeechLanguage.Installed("ko-KR", "Korean (South Korea)"),
            35,
        )
        assertEquals("ko-KR", korean.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
    }

    @Test fun viewModelEnforcesMutualExclusionRoutesTargetsAndRecoversAfterTranslationGate() {
        val adapter = FakeTranscriber()
        val state = readyConversationState().copy(
            settings = mapOf(
                Speaker.A to SpeakerSettings(readingLanguage = HyMt2Languages.all.first { it.code == "ko" }),
                Speaker.B to SpeakerSettings(readingLanguage = HyMt2Languages.all.first { it.code == "en" }),
            ),
        )
        val viewModel = SessionViewModel(transcriberFactory = { adapter }, translator = FakeTranslator(), initialState = state)

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.PttPress(composeRule.activity, Speaker.A))
            adapter.listener.onPartial("hello")
            viewModel.dispatch(SessionAction.PttPress(composeRule.activity, Speaker.B))
        }
        assertEquals(SessionPhase.ListeningA, viewModel.state.value.phase)
        assertEquals(1, adapter.starts)
        assertEquals(Speaker.A, viewModel.state.value.conversations.single().speaker)
        assertEquals("en", viewModel.state.value.conversations.single().targetLanguage.code)

        composeRule.runOnUiThread {
            adapter.listener.onFinal("hello world")
            viewModel.dispatch(SessionAction.PttRelease(Speaker.A))
            adapter.listener.onStopped()
        }
        assertEquals(SessionPhase.Ready, viewModel.state.value.phase)
        composeRule.waitForIdle()
        assertEquals("translated", viewModel.state.value.conversations.single().translation)

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.PttPress(composeRule.activity, Speaker.B))
            adapter.listener.onPartial("Hello")
        }
        assertEquals(SessionPhase.ListeningB, viewModel.state.value.phase)
        assertEquals(2, adapter.starts)
        assertEquals("ko", viewModel.state.value.conversations.last().targetLanguage.code)
    }

    private fun setApp(
        state: SessionUiState,
        onAction: (UiAction) -> Unit = {},
        onOpenAppSettings: () -> Unit = {},
        onOpenVoiceInputSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            RealtimeTranslateTheme {
                RealtimeTranslateApp(state, onAction, onOpenAppSettings, onOpenVoiceInputSettings)
            }
        }
    }

    private companion object {
        const val CHIP_A = "Speaker A languages: reads English, speaks Automatic"
        const val CHIP_B = "Speaker B languages: reads Korean, speaks Automatic"
    }

    private fun readyConversationState() = SessionUiState(SessionPhase.Ready, conversationStarted = true)
    private fun item(speaker: Speaker, transcript: String, final: Boolean) = ConversationItem("1", speaker, SpeechLanguage.Automatic, HyMt2Languages.all.first { it.code == "en" }, transcript, final)

    private class FakeTranscriber : SpeechTranscriber {
        lateinit var listener: SpeechTranscriptListener
        var starts = 0
        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            starts += 1
            this.listener = listener
            listener.onReady()
            return SpeechStartResult.Started
        }
        override fun stop() = Unit
        override fun destroy() = Unit
    }

    private class FakeTranslator : HyMt2Translator {
        override suspend fun load(context: android.content.Context, onProgress: (Float) -> Unit) = Unit
        override suspend fun translate(prompt: String) = "translated"
        override fun close() = Unit
    }
}
