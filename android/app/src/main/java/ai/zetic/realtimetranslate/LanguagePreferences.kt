package ai.zetic.realtimetranslate

import android.content.Context
import java.text.Collator
import java.util.Locale

/** The four per-speaker choices that survive process death and relaunch. */
interface LanguagePreferenceStore {
    fun readingCode(speaker: Speaker): String?
    fun spokenTag(speaker: Speaker): String?
    fun setReadingCode(speaker: Speaker, code: String)
    fun setSpokenTag(speaker: Speaker, tag: String)
}

class AndroidLanguagePreferences(context: Context) : LanguagePreferenceStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(FirstRunPreferences.FILE_NAME, Context.MODE_PRIVATE)

    override fun readingCode(speaker: Speaker): String? =
        preferences.getString(readingKey(speaker), null)

    override fun spokenTag(speaker: Speaker): String? =
        preferences.getString(spokenKey(speaker), null)

    override fun setReadingCode(speaker: Speaker, code: String) {
        preferences.edit().putString(readingKey(speaker), code).apply()
    }

    override fun setSpokenTag(speaker: Speaker, tag: String) {
        preferences.edit().putString(spokenKey(speaker), tag).apply()
    }

    companion object {
        fun readingKey(speaker: Speaker) = "language.reading.${speaker.label}"
        fun spokenKey(speaker: Speaker) = "language.spoken.${speaker.label}"
    }
}

/** In-memory counterpart used by unit tests and previews. */
class MemoryLanguagePreferences : LanguagePreferenceStore {
    private val reading = mutableMapOf<Speaker, String>()
    private val spoken = mutableMapOf<Speaker, String>()

    override fun readingCode(speaker: Speaker) = reading[speaker]
    override fun spokenTag(speaker: Speaker) = spoken[speaker]
    override fun setReadingCode(speaker: Speaker, code: String) { reading[speaker] = code }
    override fun setSpokenTag(speaker: Speaker, tag: String) { spoken[speaker] = tag }
}

val SpeechLanguage.preferenceTag: String
    get() = when (this) {
        SpeechLanguage.Automatic -> "automatic"
        is SpeechLanguage.Installed -> languageTag
    }

fun TranslationLanguage.localizedName(locale: Locale): String {
    val localized = Locale.forLanguageTag(code).getDisplayName(locale).takeIf { it.isNotBlank() } ?: displayName
    return localized.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

object LanguageMenuOrdering {
    fun order(
        candidates: List<TranslationLanguage>,
        pinned: List<TranslationLanguage>,
        deviceLanguageCode: String?,
        locale: Locale,
    ): List<TranslationLanguage> {
        val top = (pinned + candidates.filter { it.code == deviceLanguageCode })
            .distinctBy { it.code }
            .filter { it in candidates }
        val collator = Collator.getInstance(locale)
        return top + candidates.filterNot { it in top }.sortedWith { left, right ->
            collator.compare(left.localizedName(locale), right.localizedName(locale))
        }
    }
}

object SpeechLanguageMenuOrdering {
    fun order(
        candidates: List<SpeechLanguage>,
        pinned: List<SpeechLanguage>,
        deviceLanguageCode: String?,
        automaticName: String,
        locale: Locale,
    ): List<SpeechLanguage> {
        val device = candidates.filterIsInstance<SpeechLanguage.Installed>()
            .filter { Locale.forLanguageTag(it.languageTag).language == deviceLanguageCode }
        val top = (pinned + device).distinct().filter { it in candidates }
        val collator = Collator.getInstance(locale)
        fun name(language: SpeechLanguage) = when (language) {
            SpeechLanguage.Automatic -> automaticName
            is SpeechLanguage.Installed -> language.name
        }
        return top + candidates.filterNot { it in top }.sortedWith { left, right ->
            collator.compare(name(left), name(right))
        }
    }
}
