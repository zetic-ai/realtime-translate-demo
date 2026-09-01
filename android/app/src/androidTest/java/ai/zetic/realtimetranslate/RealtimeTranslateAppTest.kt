package ai.zetic.realtimetranslate

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun finalCardDisplaysSpeakerTargetAndTranslationError() {
        val state = readyConversationState().copy(conversations = listOf(item(Speaker.B, "hello", true).copy(translationError = "Hy-MT2 runtime verification is incomplete.")))
        setApp(state)
        composeRule.onNodeWithText("B - Korean").assertIsDisplayed()
        composeRule.onNodeWithText("For English").assertIsDisplayed()
        composeRule.onNodeWithText("Hy-MT2 runtime verification is incomplete.").assertIsDisplayed()
    }

    @Test fun settingsProvideSeparateLanguagePickersForBothSpeakers() {
        setApp(SessionUiState(SessionPhase.Ready, availableInputLanguages = SpeechLanguage.entries.toSet()))
        composeRule.onNodeWithContentDescription("Speaker A input language selector: Korean").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Speaker B translation language selector: English").assertIsDisplayed()
    }

    @Test fun viewModelEnforcesMutualExclusionRoutesTargetsAndRecoversAfterTranslationGate() {
        val adapter = FakeTranscriber()
        val state = readyConversationState().copy(
            settings = mapOf(
                Speaker.A to SpeakerSettings(readingLanguage = HyMt2Languages.all.first { it.code == "ko" }),
                Speaker.B to SpeakerSettings(readingLanguage = HyMt2Languages.all.first { it.code == "en" }),
            ),
        )
        val viewModel = SessionViewModel(transcriberFactory = { adapter }, initialState = state)

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
        assertTrue(viewModel.state.value.conversations.single().translationError?.contains("Hy-MT2") == true)

        composeRule.runOnUiThread {
            viewModel.dispatch(SessionAction.PttPress(composeRule.activity, Speaker.B))
            adapter.listener.onPartial("Hello")
        }
        assertEquals(SessionPhase.ListeningB, viewModel.state.value.phase)
        assertEquals(2, adapter.starts)
        assertEquals("ko", viewModel.state.value.conversations.last().targetLanguage.code)
    }

    private fun setApp(state: SessionUiState, onAction: (UiAction) -> Unit = {}, onOpenAppSettings: () -> Unit = {}) {
        composeRule.setContent { RealtimeTranslateTheme { RealtimeTranslateApp(state, onAction, onOpenAppSettings) } }
    }

    private fun readyConversationState() = SessionUiState(SessionPhase.Ready, conversationStarted = true, availableInputLanguages = SpeechLanguage.entries.toSet())
    private fun item(speaker: Speaker, transcript: String, final: Boolean) = ConversationItem("1", speaker, SpeechLanguage.Korean, HyMt2Languages.all.first { it.code == "en" }, transcript, final)

    private class FakeTranscriber : SpeechTranscriber {
        lateinit var listener: SpeechTranscriptListener
        var starts = 0
        override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) = onComplete(SpeechCapabilityResult(languages.toSet()))
        override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
            starts += 1
            this.listener = listener
            listener.onReady()
            return SpeechStartResult.Started
        }
        override fun stop() = Unit
        override fun destroy() = Unit
    }
}
