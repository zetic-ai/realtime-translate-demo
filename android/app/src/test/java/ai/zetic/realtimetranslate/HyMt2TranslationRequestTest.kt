package ai.zetic.realtimetranslate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HyMt2TranslationRequestTest {
    @Test
    fun `lists the 38 official target languages with Filipino`() {
        assertEquals(38, HyMt2Languages.all.size)
        assertEquals("Filipino", HyMt2Languages.all.first { it.code == "fil" }.displayName)
        assertTrue(HyMt2Languages.all.none { it.displayName == "Tagalog" })
    }

    @Test
    fun `uses the device recognizer without a fixed source language list`() {
        assertEquals(UiText.res(R.string.speech_language_automatic), SpeechLanguage.Automatic.displayName)
    }

    @Test
    fun `maps installed language tags without a fixed whitelist`() {
        val languages = SpeechLanguageCatalogMapping.installed(listOf("fr-FR", "ko-KR", "fr-FR"), Locale.ENGLISH)
        assertEquals(listOf("French (France)", "Korean (South Korea)"), languages.map { it.name })
    }

    @Test
    fun `maps Korean on-device model readiness on Android 13 and later`() {
        val required = SpeechLanguageCatalogMapping.onDevice(
            installedTags = listOf("en-US"),
            supportedTags = listOf("en-US", "ko-KR"),
            pendingTags = emptyList(),
            displayLocale = Locale.ENGLISH,
        ).first { it.languageTag == "ko-KR" }
        val pending = SpeechLanguageCatalogMapping.onDevice(
            installedTags = listOf("en-US"),
            supportedTags = listOf("ko-KR"),
            pendingTags = listOf("ko-KR"),
            displayLocale = Locale.ENGLISH,
        ).first { it.languageTag == "ko-KR" }
        val ready = SpeechLanguageCatalogMapping.onDevice(
            installedTags = listOf("en-US", "ko-KR"),
            supportedTags = listOf("ko-KR"),
            pendingTags = listOf("ko-KR"),
            displayLocale = Locale.ENGLISH,
        ).first { it.languageTag == "ko-KR" }

        assertEquals(SpeechLanguage.OnDeviceStatus.DownloadRequired, required.onDeviceStatus)
        assertEquals(SpeechLanguage.OnDeviceStatus.DownloadPending, pending.onDeviceStatus)
        assertEquals(SpeechLanguage.OnDeviceStatus.Ready, ready.onDeviceStatus)
        assertEquals("ko-KR", ready.languageTag)
    }

    @Test
    fun `preserves installed Korean variants instead of rewriting them as ko-KR`() {
        val languages = SpeechLanguageCatalogMapping.onDevice(
            installedTags = listOf("ko-KP"),
            supportedTags = emptyList(),
            pendingTags = emptyList(),
            displayLocale = Locale.ENGLISH,
        )

        assertEquals(listOf("ko-KP"), languages.map { it.languageTag })
        assertEquals(SpeechLanguage.OnDeviceStatus.Ready, languages.single().onDeviceStatus)
    }

    @Test
    fun `Android 12 offers explicit Korean with unverified offline model status`() {
        val korean = SpeechLanguageCatalogMapping.legacy(Locale.ENGLISH)
            .filterIsInstance<SpeechLanguage.Installed>()
            .single()

        assertEquals("ko-KR", korean.languageTag)
        assertEquals(SpeechLanguage.OnDeviceStatus.Unverified, korean.onDeviceStatus)
        assertTrue(korean.onDeviceStatus.isSelectable)
    }

    @Test
    fun `triggers Korean model download only for supported not-installed not-pending models`() {
        assertTrue(
            KoreanSpeechModelDownloadRequest.shouldTrigger(
                sdkInt = 33,
                installedTags = listOf("en-US"),
                supportedTags = listOf("en-US", "ko-KR"),
                pendingTags = emptyList(),
            ),
        )
        assertFalse(
            KoreanSpeechModelDownloadRequest.shouldTrigger(
                sdkInt = 33,
                installedTags = listOf("ko-KR"),
                supportedTags = listOf("ko-KR"),
                pendingTags = emptyList(),
            ),
        )
        assertFalse(
            KoreanSpeechModelDownloadRequest.shouldTrigger(
                sdkInt = 33,
                installedTags = listOf("en-US"),
                supportedTags = listOf("ko-KR"),
                pendingTags = listOf("ko-KR"),
            ),
        )
        assertFalse(
            KoreanSpeechModelDownloadRequest.shouldTrigger(
                sdkInt = 32,
                installedTags = listOf("en-US"),
                supportedTags = listOf("ko-KR"),
                pendingTags = emptyList(),
            ),
        )
    }

    @Test
    fun `Korean model download request is explicit and offline`() {
        val intent = KoreanSpeechModelDownloadRequest.intentSpec()

        assertEquals("ko-KR", intent.languageTag)
        assertEquals(true, intent.preferOffline)
    }

    @Test
    fun `reading language alignment ignores Korean until its offline model is selectable`() {
        val korean = HyMt2Languages.all.first { it.code == "ko" }
        val unavailable = SpeechLanguage.Installed(
            "ko-KR",
            "Korean (South Korea)",
            SpeechLanguage.OnDeviceStatus.DownloadRequired,
        )

        assertEquals(null, SpokenLanguageMatching.match(korean, listOf(SpeechLanguage.Automatic, unavailable)))
    }

    @Test
    fun `renders the official one turn chat template`() {
        val prompt = HyMt2TranslationRequestBuilder.build(
            sourceText = "Good morning.",
            targetLanguage = HyMt2Languages.all.first { it.code == "fr" },
        )

        assertEquals("<\uFF5Chy_begin\u2581of\u2581sentence\uFF5C><\uFF5Chy_User\uFF5C>Translate the following text into French. Note that you should only output the translated result without any additional explanation:\n\nGood morning.<\uFF5Chy_Assistant\uFF5C>", prompt)
    }

    @Test
    fun `requires only an on-device recognizer and never creates an online fallback`() {
        assertEquals(UiText.res(R.string.speech_error_android_version), OnDeviceRecognitionEligibility.failureFor(30, true, true))
        assertEquals(UiText.res(R.string.speech_error_permission), OnDeviceRecognitionEligibility.failureFor(31, false, true))
        assertEquals(UiText.res(R.string.speech_error_no_recognizer), OnDeviceRecognitionEligibility.failureFor(31, true, false))
        assertEquals(null, OnDeviceRecognitionEligibility.failureFor(31, true, true))
    }
}
