package ai.zetic.realtimetranslate

enum class SessionPhase { PermissionRequired, Ready, ListeningA, ListeningB, FinalizingA, FinalizingB, TranslatingA, TranslatingB, Error }

enum class Speaker(val label: String) {
    A("A"), B("B");

    fun other() = if (this == A) B else A
}

enum class SpeechLanguage(val displayName: String, val code: String) {
    Korean("한국어", "ko"), Chinese("中文", "zh"), Japanese("日本語", "ja"),
    English("English", "en"), French("Français", "fr"), Spanish("Español", "es"),
}

data class TranslationLanguage(val code: String, val displayName: String)

object HyMt2Languages {
    val all = listOf(
        TranslationLanguage("zh", "Chinese"), TranslationLanguage("en", "English"), TranslationLanguage("fr", "French"), TranslationLanguage("pt", "Portuguese"),
        TranslationLanguage("es", "Spanish"), TranslationLanguage("ja", "Japanese"), TranslationLanguage("tr", "Turkish"), TranslationLanguage("ru", "Russian"),
        TranslationLanguage("ar", "Arabic"), TranslationLanguage("ko", "Korean"), TranslationLanguage("th", "Thai"), TranslationLanguage("it", "Italian"),
        TranslationLanguage("de", "German"), TranslationLanguage("vi", "Vietnamese"), TranslationLanguage("ms", "Malay"), TranslationLanguage("id", "Indonesian"),
        TranslationLanguage("tl", "Tagalog"), TranslationLanguage("hi", "Hindi"), TranslationLanguage("zh-Hant", "Traditional Chinese"), TranslationLanguage("pl", "Polish"),
        TranslationLanguage("cs", "Czech"), TranslationLanguage("nl", "Dutch"), TranslationLanguage("km", "Khmer"), TranslationLanguage("my", "Burmese"),
        TranslationLanguage("fa", "Persian"), TranslationLanguage("gu", "Gujarati"), TranslationLanguage("ur", "Urdu"), TranslationLanguage("te", "Telugu"),
        TranslationLanguage("mr", "Marathi"), TranslationLanguage("he", "Hebrew"), TranslationLanguage("bn", "Bengali"), TranslationLanguage("ta", "Tamil"),
        TranslationLanguage("uk", "Ukrainian"), TranslationLanguage("bo", "Tibetan"), TranslationLanguage("kk", "Kazakh"), TranslationLanguage("mn", "Mongolian"),
        TranslationLanguage("ug", "Uyghur"), TranslationLanguage("yue", "Cantonese"),
    )
}

data class SpeakerSettings(
    val inputLanguage: SpeechLanguage = SpeechLanguage.Korean,
    val readingLanguage: TranslationLanguage = HyMt2Languages.all.first { it.code == "en" },
)

data class ConversationItem(
    val id: String,
    val speaker: Speaker,
    val sourceLanguage: SpeechLanguage,
    val targetLanguage: TranslationLanguage,
    val transcript: String,
    val isFinal: Boolean,
    val translation: String? = null,
    val translationError: String? = null,
)

data class SessionUiState(
    val phase: SessionPhase,
    val permissionPermanentlyDenied: Boolean = false,
    val settings: Map<Speaker, SpeakerSettings> = Speaker.entries.associateWith { SpeakerSettings() },
    val conversations: List<ConversationItem> = emptyList(),
    val conversationStarted: Boolean = false,
    val errorMessage: String? = null,
    val modelGateMessage: String? = null,
    val availableInputLanguages: Set<SpeechLanguage> = emptySet(),
    val downloadableInputLanguages: Set<SpeechLanguage> = emptySet(),
    val sourceCapabilityMessage: String? = null,
) {
    fun settingsFor(speaker: Speaker) = settings.getValue(speaker)
    fun activeSpeaker(): Speaker? = when (phase) {
        SessionPhase.ListeningA, SessionPhase.FinalizingA, SessionPhase.TranslatingA -> Speaker.A
        SessionPhase.ListeningB, SessionPhase.FinalizingB, SessionPhase.TranslatingB -> Speaker.B
        else -> null
    }
}
