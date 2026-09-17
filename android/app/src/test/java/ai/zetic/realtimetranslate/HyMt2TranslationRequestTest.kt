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
    fun `merges every platform language with installed then pending then supported precedence`() {
        val languages = SpeechLanguageCatalogMapping.onDevice(
            installedTags = listOf("en-US", "pt-BR"),
            supportedTags = listOf("en-us", "pt-br", "fr-CA", "es-MX"),
            pendingTags = listOf("FR-ca"),
            displayLocale = Locale.ENGLISH,
        )

        assertEquals("en-US", languages.first { it.languageTag.equals("en-US", true) }.languageTag)
        assertEquals(SpeechLanguage.OnDeviceStatus.Ready, languages.first { it.languageTag == "en-US" }.onDeviceStatus)
        assertEquals("FR-ca", languages.first { it.languageTag.equals("fr-CA", true) }.languageTag)
        assertEquals(SpeechLanguage.OnDeviceStatus.DownloadPending, languages.first { it.languageTag == "FR-ca" }.onDeviceStatus)
        assertEquals(SpeechLanguage.OnDeviceStatus.DownloadRequired, languages.first { it.languageTag == "es-MX" }.onDeviceStatus)
    }

    @Test
    fun `triggers a download only for a download-required language`() {
        assertTrue(SpeechModelDownloadRequest.shouldTrigger(model("fr-CA", SpeechLanguage.OnDeviceStatus.DownloadRequired)))
        assertFalse(SpeechModelDownloadRequest.shouldTrigger(model("fr-CA", SpeechLanguage.OnDeviceStatus.DownloadPending)))
        assertFalse(SpeechModelDownloadRequest.shouldTrigger(model("fr-CA", SpeechLanguage.OnDeviceStatus.Ready)))
    }

    @Test
    fun `speech model download request preserves the platform tag and is offline`() {
        val intent = SpeechModelDownloadRequest.intentSpec("yue-Hant-HK")

        assertEquals("yue-Hant-HK", intent.languageTag)
        assertEquals(true, intent.preferOffline)
    }

    @Test
    fun `reading language alignment ignores a language until its offline model is selectable`() {
        val french = HyMt2Languages.all.first { it.code == "fr" }
        val unavailable = SpeechLanguage.Installed(
            "fr-CA",
            "French (Canada)",
            SpeechLanguage.OnDeviceStatus.DownloadRequired,
        )

        assertEquals(null, SpokenLanguageMatching.match(french, listOf(SpeechLanguage.Automatic, unavailable)))
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
        assertEquals(UiText.res(R.string.speech_error_android_version), OnDeviceRecognitionEligibility.failureFor(32, true, true))
        assertEquals(UiText.res(R.string.speech_error_permission), OnDeviceRecognitionEligibility.failureFor(33, false, true))
        assertEquals(UiText.res(R.string.speech_error_no_recognizer), OnDeviceRecognitionEligibility.failureFor(33, true, false))
        assertEquals(null, OnDeviceRecognitionEligibility.failureFor(33, true, true))
    }

    private fun model(tag: String, status: SpeechLanguage.OnDeviceStatus) =
        SpeechLanguage.Installed(tag, tag, status)
}
