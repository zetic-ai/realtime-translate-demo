package ai.zetic.realtimetranslate

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import java.util.Locale

interface SpeechTranscriber {
    fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult
    fun stop()
    fun destroy()
}

interface SpeechTranscriptListener {
    fun onReady()
    fun onPartial(transcript: String)
    fun onFinal(transcript: String)
    fun onStopped()
    fun onError(message: UiText)
}

sealed interface SpeechStartResult {
    data object Started : SpeechStartResult
    data class Failed(val message: UiText) : SpeechStartResult
}

/** Android's platform recognizer is used exclusively; online recognizer fallback is never created. */
class AndroidOnDeviceSpeechTranscriber(
    private val context: Context,
    private val platform: OnDeviceSpeechRecognizerPlatform = AndroidOnDeviceSpeechRecognizerPlatform,
) : SpeechTranscriber {
    private var recognizer: SpeechRecognizer? = null
    private var activeListener: SpeechTranscriptListener? = null
    private var listening = false
    private var stopping = false
    private var destroyed = false

    override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return SpeechStartResult.Failed(UiText.res(R.string.speech_error_main_thread))
        }
        OnDeviceRecognitionEligibility.failureFor(
            sdkInt = Build.VERSION.SDK_INT,
            hasRecordAudioPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            isOnDeviceRecognizerAvailable = platform.isOnDeviceRecognitionAvailable(context),
        )?.let { return SpeechStartResult.Failed(it) }

        destroyed = false
        stopping = false
        activeListener = listener
        val intent = OnDeviceRecognitionIntentFactory.create(language, Build.VERSION.SDK_INT)
        val onDeviceRecognizer = platform.createOnDeviceSpeechRecognizer(context)
        recognizer = onDeviceRecognizer.apply { setRecognitionListener(listener(intent)) }
        beginListening(intent)
        return SpeechStartResult.Started
    }

    override fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        listening = false
        stopping = true
        recognizer?.stopListening()
    }

    override fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        listening = false
        activeListener = null
        recognizer?.destroy()
        recognizer = null
        destroyed = true
    }

    private fun beginListening(intent: Intent) {
        if (!destroyed && recognizer != null) {
            listening = true
            stopping = false
            recognizer?.startListening(intent)
        }
    }

    private fun listener(intent: Intent) = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            activeListener?.onReady()
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            if (stopping) {
                activeListener?.onStopped()
            } else if (listening) {
                activeListener?.onError(UiText.res(R.string.speech_error_recognition_failed, error))
            }
        }
        override fun onResults(results: android.os.Bundle?) {
            results?.transcript()?.let { activeListener?.onFinal(it) }
            if (stopping) activeListener?.onStopped() else if (listening) beginListening(intent)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            partialResults?.transcript()?.let { activeListener?.onPartial(it) }
        }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

}

object OnDeviceRecognitionIntentFactory {
    fun create(language: SpeechLanguage, sdkInt: Int) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (language is SpeechLanguage.Installed) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.languageTag)
        if (language is SpeechLanguage.Automatic && sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
        }
    }
}

object KoreanSpeechModelDownloadRequest {
    data class IntentSpec(val languageTag: String, val preferOffline: Boolean)

    fun shouldTrigger(
        sdkInt: Int,
        installedTags: List<String>,
        supportedTags: List<String>,
        pendingTags: List<String>,
    ): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
        supportedTags.hasKoreanLocale() &&
        !installedTags.hasKoreanLocale() &&
        !pendingTags.hasKoreanLocale()

    fun intentSpec(): IntentSpec = IntentSpec(
        languageTag = SpeechLanguageCatalogMapping.KOREAN_LANGUAGE_TAG,
        preferOffline = true,
    )

    fun intent(sdkInt: Int): Intent = OnDeviceRecognitionIntentFactory.create(
        SpeechLanguage.Installed(intentSpec().languageTag, "Korean"),
        sdkInt,
    )

    private fun List<String>.hasKoreanLocale(): Boolean =
        any { it.equals(SpeechLanguageCatalogMapping.KOREAN_LANGUAGE_TAG, ignoreCase = true) }
}

interface SpeechLanguageCatalog {
    fun load(context: Context, onResult: (SpeechLanguageCatalogResult) -> Unit)
}

data class SpeechLanguageCatalogResult(val languages: List<SpeechLanguage>, val message: UiText? = null)

class AndroidSpeechLanguageCatalog(
    private val platform: OnDeviceSpeechRecognizerPlatform = AndroidOnDeviceSpeechRecognizerPlatform,
) : SpeechLanguageCatalog {
    override fun load(context: Context, onResult: (SpeechLanguageCatalogResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(SpeechLanguageCatalogResult(SpeechLanguageCatalogMapping.legacy()))
            return
        }
        if (!platform.isOnDeviceRecognitionAvailable(context)) {
            onResult(SpeechLanguageCatalogResult(listOf(SpeechLanguage.Automatic), UiText.res(R.string.speech_catalog_no_recognizer)))
            return
        }
        val recognizer = platform.createOnDeviceSpeechRecognizer(context)
        recognizer.checkRecognitionSupport(
            OnDeviceRecognitionIntentFactory.create(SpeechLanguage.Automatic, Build.VERSION.SDK_INT),
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if (KoreanSpeechModelDownloadRequest.shouldTrigger(
                            sdkInt = Build.VERSION.SDK_INT,
                            installedTags = support.installedOnDeviceLanguages,
                            supportedTags = support.supportedOnDeviceLanguages,
                            pendingTags = support.pendingOnDeviceLanguages,
                        )
                    ) {
                        platform.triggerModelDownload(recognizer, KoreanSpeechModelDownloadRequest.intent(Build.VERSION.SDK_INT))
                    }
                    val languages = SpeechLanguageCatalogMapping.onDevice(
                        installedTags = support.installedOnDeviceLanguages,
                        supportedTags = support.supportedOnDeviceLanguages,
                        pendingTags = support.pendingOnDeviceLanguages,
                    )
                    recognizer.destroy()
                    onResult(SpeechLanguageCatalogResult(listOf(SpeechLanguage.Automatic) + languages))
                }
                override fun onError(error: Int) {
                    recognizer.destroy()
                    onResult(SpeechLanguageCatalogResult(listOf(SpeechLanguage.Automatic), UiText.res(R.string.speech_catalog_unavailable, error)))
                }
            },
        )
    }

}

object SpeechLanguageCatalogMapping {
    const val KOREAN_LANGUAGE_TAG = "ko-KR"

    /**
     * The spoken-language names come from the platform, which already localizes them, so the list
     * is built in the app's current language rather than pinned to English. [displayLocale] is a
     * parameter so the ordering and the names are deterministic in a unit test.
     */
    fun installed(tags: List<String>, displayLocale: Locale = Locale.getDefault()): List<SpeechLanguage.Installed> =
        tags.map { installedLanguage(it, displayLocale) }.distinctBy { it.languageTag }.sortedBy { it.name }

    /** Android 12 cannot report recognition support, so Korean remains selectable but explicit. */
    fun legacy(displayLocale: Locale = Locale.getDefault()): List<SpeechLanguage> = listOf(
        SpeechLanguage.Automatic,
        korean(SpeechLanguage.OnDeviceStatus.Unverified, displayLocale),
    )

    /**
     * Android 13+ reports installed, supported, and pending on-device languages separately. Keep
     * every installed language and add the explicit Korean locale when its model can be prepared.
     */
    fun onDevice(
        installedTags: List<String>,
        supportedTags: List<String>,
        pendingTags: List<String>,
        displayLocale: Locale = Locale.getDefault(),
    ): List<SpeechLanguage.Installed> {
        val installed = installed(installedTags, displayLocale)
            .filterNot { it.languageTag.isKoreanLocale() }
        val koreanStatus = when {
            installedTags.hasKoreanLocale() -> SpeechLanguage.OnDeviceStatus.Ready
            pendingTags.hasKoreanLocale() -> SpeechLanguage.OnDeviceStatus.DownloadPending
            supportedTags.hasKoreanLocale() -> SpeechLanguage.OnDeviceStatus.DownloadRequired
            else -> null
        }
        return (installed + listOfNotNull(koreanStatus?.let { korean(it, displayLocale) })).sortedBy { it.name }
    }

    private fun installedLanguage(tag: String, displayLocale: Locale): SpeechLanguage.Installed {
        val locale = Locale.forLanguageTag(tag)
        return SpeechLanguage.Installed(tag, locale.getDisplayName(displayLocale).ifBlank { tag })
    }

    private fun korean(status: SpeechLanguage.OnDeviceStatus, displayLocale: Locale): SpeechLanguage.Installed =
        installedLanguage(KOREAN_LANGUAGE_TAG, displayLocale).copy(onDeviceStatus = status)

    private fun List<String>.hasKoreanLocale(): Boolean =
        any { it.isKoreanLocale() }

    private fun String.isKoreanLocale(): Boolean =
        equals(KOREAN_LANGUAGE_TAG, ignoreCase = true)
}

/**
 * The installed recognizer language matching a reading language: same primary language subtag,
 * preferring the variant that code most likely implies (`fr` picks `fr-FR` over `fr-BE`, `zh-Hant`
 * picks `zh-TW`). A language with no installed recognizer has no match, and the caller leaves the
 * spoken language alone.
 */
object SpokenLanguageMatching {
    fun match(reading: TranslationLanguage, available: List<SpeechLanguage>): SpeechLanguage.Installed? {
        val primary = primarySubtag(reading.code) ?: return null
        val matches = available.filterIsInstance<SpeechLanguage.Installed>()
            .filter { it.onDeviceStatus.isSelectable }
            .filter { primarySubtag(it.languageTag) == primary }
        if (matches.size <= 1) return matches.firstOrNull()
        val implied = variantSubtags(reading.code) + likelyVariants.getOrElse(reading.code) { emptySet() }
        return matches.firstOrNull { candidate ->
            variantSubtags(candidate.languageTag).any(implied::contains)
        } ?: matches.first()
    }

    private fun primarySubtag(tag: String): String? =
        Locale.forLanguageTag(tag).language.takeIf { it.isNotEmpty() }

    /** Everything after the primary subtag, upper-cased so `Hant` and `HANT` compare equal. */
    private fun variantSubtags(tag: String): Set<String> =
        tag.split('-', '_').drop(1).filter(String::isNotEmpty).map { it.uppercase(Locale.ROOT) }.toSet()

    /**
     * The variant a bare reading code implies, for the Hy-MT2 languages a device plausibly carries
     * more than one recognizer for. Everything else resolves on the primary subtag alone.
     */
    private val likelyVariants = mapOf(
        "ar" to setOf("SA"),
        "bn" to setOf("BD"),
        "de" to setOf("DE"),
        "en" to setOf("US"),
        "es" to setOf("ES"),
        "fr" to setOf("FR"),
        "it" to setOf("IT"),
        "ms" to setOf("MY"),
        "nl" to setOf("NL"),
        "pt" to setOf("BR"),
        "ta" to setOf("IN"),
        "ur" to setOf("PK"),
        "zh" to setOf("CN", "HANS"),
        "zh-Hant" to setOf("TW", "HANT"),
    )
}

interface OnDeviceSpeechRecognizerPlatform {
    fun isOnDeviceRecognitionAvailable(context: Context): Boolean
    fun createOnDeviceSpeechRecognizer(context: Context): SpeechRecognizer
    fun triggerModelDownload(recognizer: SpeechRecognizer, intent: Intent)
}

object AndroidOnDeviceSpeechRecognizerPlatform : OnDeviceSpeechRecognizerPlatform {
    override fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    override fun createOnDeviceSpeechRecognizer(context: Context): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    override fun triggerModelDownload(recognizer: SpeechRecognizer, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Looper.myLooper() != Looper.getMainLooper()) return
        runCatching { recognizer.triggerModelDownload(intent) }
    }
}

object OnDeviceRecognitionEligibility {
    fun failureFor(sdkInt: Int, hasRecordAudioPermission: Boolean, isOnDeviceRecognizerAvailable: Boolean): UiText? = when {
        sdkInt < Build.VERSION_CODES.S -> UiText.res(R.string.speech_error_android_version)
        !hasRecordAudioPermission -> UiText.res(R.string.speech_error_permission)
        !isOnDeviceRecognizerAvailable -> UiText.res(R.string.speech_error_no_recognizer)
        else -> null
    }
}

private fun android.os.Bundle.transcript(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
