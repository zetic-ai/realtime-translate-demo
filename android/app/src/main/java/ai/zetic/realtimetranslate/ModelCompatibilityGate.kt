package ai.zetic.realtimetranslate

/**
 * The app must not claim model execution until its two model artifacts and their device evidence exist.
 * This deliberately remains closed until the evidence recorded in docs/model-compatibility-gate.md exists.
 */
class ModelCompatibilityGate {
    fun check(input: SpeechLanguage, output: TranslationLanguage): GateResult {
        return GateResult.Blocked(
                "모델 호환성 검증이 완료되지 않았습니다. ${input.displayName} → ${output.displayName} 조합은 실제 기기에서 " +
                "pyannote, Hy-MT2 순서로 검증되어야 합니다. Android STT가 마이크를 직접 사용하므로 pyannote PCM fan-out도 별도 검증이 필요합니다.",
        )
    }
}

sealed interface GateResult {
    data object Ready : GateResult
    data class Blocked(val reason: String) : GateResult
}
