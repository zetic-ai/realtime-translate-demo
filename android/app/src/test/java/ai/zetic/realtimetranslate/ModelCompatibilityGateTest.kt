package ai.zetic.realtimetranslate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCompatibilityGateTest {
    @Test
    fun `lists all official Hy-MT2 target languages`() {
        assertEquals(38, HyMt2Languages.all.size)
        assertEquals(
            listOf("zh", "en", "fr", "pt", "es", "ja", "tr", "ru", "ar", "ko", "th", "it", "de", "vi", "ms", "id", "tl", "hi", "zh-Hant", "pl", "cs", "nl", "km", "my", "fa", "gu", "ur", "te", "mr", "he", "bn", "ta", "uk", "bo", "kk", "mn", "ug", "yue"),
            HyMt2Languages.all.map { it.code },
        )
    }

    @Test
    fun `blocks model execution until device compatibility evidence exists`() {
        val result = ModelCompatibilityGate().check(SpeechLanguage.Korean, HyMt2Languages.all.first())

        assertTrue(result is GateResult.Blocked)
    }
}
