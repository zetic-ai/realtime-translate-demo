package ai.zetic.realtimetranslate

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The replay-only speech decisions, voice match, and audio-focus handoff are exercised over fakes.
 */
class SpeechOutputTest {

    // region What gets spoken

    @Test fun `only a completed bubble produces a replay request`() {
        assertEquals(
            SpokenTranslation.Speak("annyeong", "ko"),
            SpokenTranslation.decision(translated("1", "annyeong")),
        )
        assertEquals(SpokenTranslation.Silent, SpokenTranslation.decision(pending("2")))
        assertEquals(
            SpokenTranslation.Silent,
            SpokenTranslation.decision(translated("3", "annyeong").copy(translationError = UiText.raw("failed"))),
        )
    }

    @Test fun `the recognizer holding the microphone is exactly listening and finalizing`() {
        val live = setOf(
            SessionPhase.ListeningA,
            SessionPhase.ListeningB,
            SessionPhase.FinalizingA,
            SessionPhase.FinalizingB,
        )
        SessionPhase.entries.forEach { phase ->
            assertEquals(phase.name, phase in live, SessionUiState(phase).isRecognizerLive)
        }
    }

    // endregion

    // region The replay control

    @Test fun `the replay control is absent on a bubble with nothing to play`() {
        assertFalse(ReplayControl.isPresent(pending("1")))
        assertFalse(ReplayControl.isPresent(translated("1", "annyeong").copy(translationError = UiText.raw("no"))))
        assertTrue(ReplayControl.isPresent(translated("1", "annyeong")))
    }

    @Test fun `the replay control is present but disabled while the microphone is open`() {
        val bubble = translated("1", "annyeong")
        assertTrue(ReplayControl.isEnabled(bubble, isRecognizerLive = false))
        assertFalse(ReplayControl.isEnabled(bubble, isRecognizerLive = true))
    }

    // endregion

    // region Voice matching

    @Test fun `an exact match for the reading code wins`() {
        assertEquals(
            Locale.forLanguageTag("zh-TW"),
            SpeechVoiceMatching.match("zh-TW", locales("zh-CN", "zh-TW", "en-US")),
        )
    }

    @Test fun `a bare code takes the variant it implies before any other of the same language`() {
        assertEquals(
            Locale.forLanguageTag("zh-TW"),
            SpeechVoiceMatching.match("zh-Hant", locales("zh-CN", "zh-TW")),
        )
        assertEquals(
            Locale.forLanguageTag("en-US"),
            SpeechVoiceMatching.match("en", locales("en-GB", "en-US")),
        )
        assertEquals(
            Locale.forLanguageTag("pt-BR"),
            SpeechVoiceMatching.match("pt", locales("pt-PT", "pt-BR")),
        )
    }

    @Test fun `a code with no implied variant falls back to any voice for the same language`() {
        assertEquals(
            Locale.forLanguageTag("th-TH"),
            SpeechVoiceMatching.match("th", locales("th-TH", "en-US")),
        )
    }

    @Test fun `a language with no installed voice stays silent rather than borrowing another`() {
        assertNull(SpeechVoiceMatching.match("bo", locales("en-US", "ko-KR")))
        assertNull(SpeechVoiceMatching.match("ko", emptyList()))
    }

    // endregion

    // region Audio focus

    @Test fun `focus is claimed once and held across a replacement`() {
        val focus = FakeAudioFocus()
        val coordinator = SpeechAudioCoordinator(focus)

        assertTrue(coordinator.claim())
        assertTrue(coordinator.claim())

        assertEquals(1, focus.requests)
        assertTrue(coordinator.isHoldingFocus)
    }

    @Test fun `focus is handed back once, and a second release does nothing`() {
        val focus = FakeAudioFocus()
        val coordinator = SpeechAudioCoordinator(focus)

        coordinator.claim()
        coordinator.release()
        coordinator.release()

        assertEquals(1, focus.abandons)
        assertFalse(coordinator.isHoldingFocus)
    }

    @Test fun `a refused focus request leaves nothing claimed and is retried next time`() {
        val focus = FakeAudioFocus(granted = false)
        val coordinator = SpeechAudioCoordinator(focus)

        assertFalse(coordinator.claim())
        assertFalse(coordinator.isHoldingFocus)
        coordinator.release()
        assertEquals(0, focus.abandons)

        focus.granted = true
        assertTrue(coordinator.claim())
        assertEquals(2, focus.requests)
    }

    @Test fun `playback focus loss stops the owner and hands focus back`() {
        val focus = FakeAudioFocus()
        val coordinator = SpeechAudioCoordinator(focus)
        var stops = 0

        assertTrue(coordinator.claim {
            stops += 1
            coordinator.release()
        })
        focus.interrupt()

        assertEquals(1, stops)
        assertEquals(1, focus.abandons)
        assertFalse(coordinator.isHoldingFocus)
        assertTrue(SpeechPlaybackFocusContract.interrupts(AudioManager.AUDIOFOCUS_LOSS))
        assertTrue(SpeechPlaybackFocusContract.interrupts(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertTrue(SpeechPlaybackFocusContract.interrupts(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertFalse(SpeechPlaybackFocusContract.interrupts(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test fun `playback route loss stops the owner and hands focus back`() {
        val focus = FakeAudioFocus()
        val coordinator = SpeechAudioCoordinator(focus)
        var stops = 0

        assertTrue(coordinator.claim {
            stops += 1
            coordinator.release()
        })
        focus.routeLoss(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        assertEquals(1, stops)
        assertEquals(1, focus.abandons)
        assertFalse(coordinator.isHoldingFocus)
        assertTrue(SpeechPlaybackFocusContract.interruptsRouteChange(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        assertFalse(SpeechPlaybackFocusContract.interruptsRouteChange(null))
    }

    // endregion

    private fun locales(vararg tags: String) = tags.map(Locale::forLanguageTag)

    private fun pending(id: String) = ConversationItem(
        id = id,
        speaker = Speaker.A,
        sourceLanguage = SpeechLanguage.Automatic,
        targetLanguage = HyMt2Languages.all.first { it.code == "ko" },
        transcript = "hello",
        isFinal = true,
    )

    private fun translated(id: String, translation: String) = pending(id).copy(translation = translation)

    private class FakeAudioFocus(var granted: Boolean = true) : SpeechAudioFocus {
        var requests = 0
        var abandons = 0
        private var interruption: (() -> Unit)? = null

        override fun request(onInterrupted: () -> Unit): Boolean {
            requests += 1
            interruption = onInterrupted.takeIf { granted }
            return granted
        }

        override fun abandon() {
            interruption = null
            abandons += 1
        }

        fun interrupt() = checkNotNull(interruption).invoke()

        fun routeLoss(action: String?) {
            if (SpeechPlaybackFocusContract.interruptsRouteChange(action)) checkNotNull(interruption).invoke()
        }
    }
}
