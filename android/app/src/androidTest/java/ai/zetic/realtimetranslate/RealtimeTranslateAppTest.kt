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
}
