package ai.zetic.realtimetranslate

import android.media.AudioAttributes
import android.media.AudioManager
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFollowTest {
    @Test fun `language menu pins system device language independently of app display locale`() {
        val english = HyMt2Languages.all.first { it.code == "en" }
        val korean = HyMt2Languages.all.first { it.code == "ko" }
        val french = HyMt2Languages.all.first { it.code == "fr" }

        val ordered = LanguageMenuOrdering.order(
            candidates = HyMt2Languages.all,
            pinned = listOf(english, korean),
            deviceLanguageCode = french.code,
            locale = Locale.ENGLISH,
        )

        assertEquals(listOf("en", "ko", "fr"), ordered.take(3).map { it.code })
        assertEquals(
            ordered.drop(3).map { it.localizedName(Locale.ENGLISH) }.sorted(),
            ordered.drop(3).map { it.localizedName(Locale.ENGLISH) },
        )
        assertEquals("French", french.localizedName(Locale.ENGLISH))
    }

    @Test fun `new content follows while near bottom and becomes unseen while reading history`() {
        val nearBottom = ConversationFollow().observeDistanceFromBottom(ConversationFollow.NEAR_BOTTOM_THRESHOLD_DP)
        val (following, followEffect) = nearBottom.contentChanged(isEmpty = false)
        assertTrue(following.isFollowing)
        assertEquals(ConversationFollow.Effect.ScrollToLatest, followEffect)

        val reading = following.observeDistanceFromBottom(121f)
        val (detached, detachedEffect) = reading.contentChanged(isEmpty = false)
        assertFalse(detached.isFollowing)
        assertTrue(detached.showsJumpControl)
        assertEquals(ConversationFollow.Effect.None, detachedEffect)
    }

    @Test fun `jump restores following and clears unseen content`() {
        val detached = ConversationFollow(isFollowing = false, hasUnseenContent = true)
        val (following, effect) = detached.snapToLatest()

        assertEquals(ConversationFollow(), following)
        assertEquals(ConversationFollow.Effect.ScrollToLatest, effect)
    }

    @Test fun `touch exploration suppresses automatic follow but never an explicit jump`() {
        val (following, automatic) = ConversationFollow().contentChanged(
            isEmpty = false,
            isTouchExplorationEnabled = true,
        )
        val (_, explicit) = following.snapToLatest()

        assertEquals(ConversationFollow.Effect.None, automatic)
        assertEquals(ConversationFollow.Effect.ScrollToLatest, explicit)
    }

    @Test fun `partial result is rejected after its transcript changes or a newer pass lands`() {
        val item = item(transcript = "new words")
        val staleText = PartialTranslationPass(item.id, revision = 1, sourceText = "old words")
        val staleRevision = PartialTranslationPass(item.id, revision = 1, sourceText = "new words")
        val current = PartialTranslationPass(item.id, revision = 3, sourceText = "new words")

        assertFalse(LivePartialTranslationGuard.shouldApply(staleText, item.id, item, appliedRevision = 0))
        assertFalse(LivePartialTranslationGuard.shouldApply(staleRevision, item.id, item, appliedRevision = 2))
        assertTrue(LivePartialTranslationGuard.shouldApply(current, item.id, item, appliedRevision = 2))
        assertFalse(LivePartialTranslationGuard.shouldApply(current, "another", item, appliedRevision = 2))
    }

    @Test fun `audio interruption policy only abandons a live recognizer`() {
        assertEquals(
            AudioInterruptionResponse.AbandonUtterance,
            AudioInterruptionPolicy.response(SessionUiState(SessionPhase.ListeningA)),
        )
        assertEquals(
            AudioInterruptionResponse.Ignore,
            AudioInterruptionPolicy.response(SessionUiState(SessionPhase.TranslatingA)),
        )
    }

    @Test fun `recording focus and route contract matches assistant speech and abandons every loss`() {
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, RecordingAudioFocusContract.FOCUS_GAIN)
        assertEquals(AudioAttributes.USAGE_ASSISTANT, RecordingAudioFocusContract.USAGE)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, RecordingAudioFocusContract.CONTENT_TYPE)
        assertTrue(RecordingAudioFocusContract.interruptsFocusChange(AudioManager.AUDIOFOCUS_LOSS))
        assertTrue(RecordingAudioFocusContract.interruptsFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertTrue(RecordingAudioFocusContract.interruptsFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertFalse(RecordingAudioFocusContract.interruptsFocusChange(AudioManager.AUDIOFOCUS_GAIN))
        assertTrue(RecordingAudioFocusContract.interruptsRouteChange(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        assertFalse(RecordingAudioFocusContract.interruptsRouteChange(null))
    }

    private fun item(transcript: String) = ConversationItem(
        id = "active",
        speaker = Speaker.A,
        sourceLanguage = SpeechLanguage.Automatic,
        targetLanguage = HyMt2Languages.all.first { it.code == "ko" },
        transcript = transcript,
        isFinal = false,
    )
}
