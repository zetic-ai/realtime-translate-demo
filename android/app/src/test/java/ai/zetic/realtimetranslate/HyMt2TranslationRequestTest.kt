package ai.zetic.realtimetranslate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HyMt2TranslationRequestTest {
    @Test
    fun `lists the 38 official target languages with Filipino`() {
        assertEquals(38, HyMt2Languages.all.size)
        assertEquals("Filipino", HyMt2Languages.all.first { it.code == "fil" }.displayName)
        assertTrue(HyMt2Languages.all.none { it.displayName == "Tagalog" })
    }

    @Test
    fun `uses the device recognizer without a fixed source language list`() {
        assertEquals(listOf(SpeechLanguage.Automatic), SpeechLanguage.entries)
    }

    @Test
    fun `builds the official single user message without applying a chat template`() {
        val request = HyMt2TranslationRequestBuilder.build(
            sourceText = "Good morning.",
            targetLanguage = HyMt2Languages.all.first { it.code == "fr" },
        )

        assertEquals(
            listOf(
                HyMt2ChatMessage(
                    role = "user",
                    content = "Translate the following text into French. Note that you should only output the translated result without any additional explanation:\nGood morning.",
                ),
            ),
            request.messages,
        )
    }

    @Test
    fun `requires only an on-device recognizer and never creates an online fallback`() {
        assertTrue(OnDeviceRecognitionEligibility.failureFor(30, true, true)?.contains("API 31") == true)
        assertTrue(OnDeviceRecognitionEligibility.failureFor(31, false, true)?.contains("Microphone permission") == true)
        assertTrue(OnDeviceRecognitionEligibility.failureFor(31, true, false)?.contains("will not fall back to online recognition") == true)
        assertEquals(null, OnDeviceRecognitionEligibility.failureFor(31, true, true))
    }
}
