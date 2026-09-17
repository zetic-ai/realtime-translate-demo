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
import android.speech.ModelDownloadListener
import java.util.Locale
import java.util.concurrent.Executor

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

object SpeechModelDownloadRequest {
    data class IntentSpec(val languageTag: String, val preferOffline: Boolean)

    fun shouldTrigger(language: SpeechLanguage.Installed): Boolean =
        language.onDeviceStatus == SpeechLanguage.OnDeviceStatus.DownloadRequired

    fun intentSpec(languageTag: String): IntentSpec = IntentSpec(
        languageTag = languageTag,
        preferOffline = true,
    )

    fun intent(languageTag: String, sdkInt: Int): Intent = OnDeviceRecognitionIntentFactory.create(
        SpeechLanguage.Installed(languageTag, languageTag),
        sdkInt,
    )
}

interface SpeechLanguageCatalog {
    fun load(context: Context, onResult: (SpeechLanguageCatalogResult) -> Unit)
    fun requestDownload(
        context: Context,
        language: SpeechLanguage.Installed,
        onResult: (Boolean) -> Unit,
    ): Boolean
}

data class SpeechLanguageCatalogResult(val languages: List<SpeechLanguage>, val message: UiText? = null)

class AndroidSpeechLanguageCatalog(
    private val platform: OnDeviceSpeechRecognizerPlatform = AndroidOnDeviceSpeechRecognizerPlatform,
) : SpeechLanguageCatalog {
    override fun load(context: Context, onResult: (SpeechLanguageCatalogResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(
                SpeechLanguageCatalogResult(
                    listOf(SpeechLanguage.Automatic),
                    UiText.res(R.string.speech_catalog_android_version),
                ),
            )
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

    override fun requestDownload(
        context: Context,
        language: SpeechLanguage.Installed,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !SpeechModelDownloadRequest.shouldTrigger(language) ||
            !platform.isOnDeviceRecognitionAvailable(context)
        ) {
            return false
        }
        val recognizer = runCatching { platform.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return false
        val started = platform.triggerModelDownload(
            recognizer = recognizer,
            intent = SpeechModelDownloadRequest.intent(language.languageTag, Build.VERSION.SDK_INT),
            executor = context.mainExecutor,
        ) { success ->
            recognizer.destroy()
            onResult(success)
        }
        if (!started) recognizer.destroy()
        return started
    }
}

object SpeechLanguageCatalogMapping {
    /**
     * The spoken-language names come from the platform, which already localizes them, so the list
     * is built in the app's current language rather than pinned to English. [displayLocale] is a
     * parameter so the ordering and the names are deterministic in a unit test.
     */
    fun installed(tags: List<String>, displayLocale: Locale = Locale.getDefault()): List<SpeechLanguage.Installed> =
        tags.map { installedLanguage(it, displayLocale) }.distinctBy { it.languageTag }.sortedBy { it.name }

    /**
     * Android 13+ reports installed, supported, and pending on-device languages separately. Merge
     * them without a fixed language whitelist, preserving the exact platform tag from the
     * highest-priority state so a later download request sends that same tag back to Android.
     */
    fun onDevice(
        installedTags: List<String>,
        supportedTags: List<String>,
        pendingTags: List<String>,
        displayLocale: Locale = Locale.getDefault(),
    ): List<SpeechLanguage.Installed> {
        val byTag = linkedMapOf<String, SpeechLanguage.Installed>()
        fun merge(tags: List<String>, status: SpeechLanguage.OnDeviceStatus) {
            tags.filter(String::isNotBlank).forEach { tag ->
                byTag[tag.lowercase(Locale.ROOT)] = installedLanguage(tag, displayLocale).copy(onDeviceStatus = status)
            }
        }
        merge(supportedTags, SpeechLanguage.OnDeviceStatus.DownloadRequired)
        merge(pendingTags, SpeechLanguage.OnDeviceStatus.DownloadPending)
        merge(installedTags, SpeechLanguage.OnDeviceStatus.Ready)
        return byTag.values.sortedBy { it.name }
    }

    private fun installedLanguage(tag: String, displayLocale: Locale): SpeechLanguage.Installed {
        val locale = Locale.forLanguageTag(tag)
        return SpeechLanguage.Installed(tag, locale.getDisplayName(displayLocale).ifBlank { tag })
    }

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
    fun triggerModelDownload(
        recognizer: SpeechRecognizer,
        intent: Intent,
        executor: Executor,
        onResult: (Boolean) -> Unit,
    ): Boolean
}

object AndroidOnDeviceSpeechRecognizerPlatform : OnDeviceSpeechRecognizerPlatform {
    override fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    override fun createOnDeviceSpeechRecognizer(context: Context): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    override fun triggerModelDownload(
        recognizer: SpeechRecognizer,
        intent: Intent,
        executor: Executor,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Looper.myLooper() != Looper.getMainLooper()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                triggerModelDownloadWithListener(recognizer, intent, executor, onResult)
            } else {
                recognizer.triggerModelDownload(intent)
                onResult(true)
            }
        }.isSuccess
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun triggerModelDownloadWithListener(
        recognizer: SpeechRecognizer,
        intent: Intent,
        executor: Executor,
        onResult: (Boolean) -> Unit,
    ) {
        var finished = false
        fun finish(success: Boolean) {
            if (!finished) {
                finished = true
                onResult(success)
            }
        }
        recognizer.triggerModelDownload(
            intent,
            executor,
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = Unit
                override fun onSuccess() = finish(true)
                override fun onScheduled() = finish(true)
                override fun onError(error: Int) = finish(false)
            },
        )
    }
}

object OnDeviceRecognitionEligibility {
    fun failureFor(sdkInt: Int, hasRecordAudioPermission: Boolean, isOnDeviceRecognizerAvailable: Boolean): UiText? = when {
        sdkInt < Build.VERSION_CODES.TIRAMISU -> UiText.res(R.string.speech_error_android_version)
        !hasRecordAudioPermission -> UiText.res(R.string.speech_error_permission)
        !isOnDeviceRecognizerAvailable -> UiText.res(R.string.speech_error_no_recognizer)
        else -> null
    }
}

private fun android.os.Bundle.transcript(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
