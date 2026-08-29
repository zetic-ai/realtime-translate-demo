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

    @Test
    fun `requires only API 31 on-device recognition and never online fallback`() {
        assertTrue(OnDeviceRecognitionEligibility.failureFor(30, true, true)?.contains("API 31") == true)
        assertTrue(OnDeviceRecognitionEligibility.failureFor(31, false, true)?.contains("마이크 권한") == true)
        assertTrue(OnDeviceRecognitionEligibility.failureFor(31, true, false)?.contains("온라인 인식으로 전환하지 않습니다") == true)
        assertEquals(null, OnDeviceRecognitionEligibility.failureFor(31, true, true))
    }
}
