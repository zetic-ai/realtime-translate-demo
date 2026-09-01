package ai.zetic.realtimetranslate

/** Remains closed until the Hy-MT2 artifact and device evidence are recorded. */
class ModelCompatibilityGate {
    fun check(input: SpeechLanguage, output: TranslationLanguage): GateResult = GateResult.Blocked(
        "Hy-MT2 runtime verification is incomplete. No translation was produced for ${input.displayName} to ${output.displayName}.",
    )
}

sealed interface GateResult {
    data object Ready : GateResult
    data class Blocked(val reason: String) : GateResult
}
