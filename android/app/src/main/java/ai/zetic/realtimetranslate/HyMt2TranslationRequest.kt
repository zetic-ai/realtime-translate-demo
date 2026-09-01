package ai.zetic.realtimetranslate

data class HyMt2ChatMessage(val role: String, val content: String)

data class HyMt2TranslationRequest(val messages: List<HyMt2ChatMessage>)

/**
 * Supplies chat messages only. A GGUF runtime applies the model's Jinja chat template and
 * assistant generation marker when inference is connected.
 */
object HyMt2TranslationRequestBuilder {
    fun build(sourceText: String, targetLanguage: TranslationLanguage) = HyMt2TranslationRequest(
        messages = listOf(
            HyMt2ChatMessage(
                role = "user",
                content = "Translate the following text into ${targetLanguage.displayName}. Note that you should only output the translated result without any additional explanation:\n$sourceText",
            ),
        ),
    )
}
