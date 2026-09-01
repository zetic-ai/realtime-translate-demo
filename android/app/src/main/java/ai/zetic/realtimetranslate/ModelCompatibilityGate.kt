package ai.zetic.realtimetranslate

/** Remains closed until the Hy-MT2 artifact and device evidence are recorded. */
class ModelCompatibilityGate {
    fun check(input: SpeechLanguage, output: TranslationLanguage): GateResult = GateResult.Blocked(
        "Hy-MT2 실행 검증이 완료되지 않았습니다. ${input.displayName} → ${output.displayName} 번역문을 만들지 않았습니다.",
    )
}

sealed interface GateResult {
    data object Ready : GateResult
    data class Blocked(val reason: String) : GateResult
}
