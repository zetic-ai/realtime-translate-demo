package ai.zetic.realtimetranslate

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.ModelDownloadListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.Executor

interface SpeechTranscriber {
    fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit)
    fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult
    fun stop()
    fun destroy()
}

data class SpeechCapabilityResult(
    val available: Set<SpeechLanguage>,
    val downloadable: Set<SpeechLanguage> = emptySet(),
    val message: String? = null,
)

interface SpeechTranscriptListener {
    fun onReady()
    fun onModelDownload(message: String, restartRequired: Boolean)
    fun onPartial(transcript: String)
    fun onFinal(transcript: String)
    fun onStopped()
    fun onDeviceUnsupported(message: String)
    fun onError(message: String)
}

sealed interface SpeechStartResult {
    data object Started : SpeechStartResult
    data class Downloading(val message: String) : SpeechStartResult
    data class Failed(val message: String) : SpeechStartResult
}

/** Android's platform recognizer is used exclusively; online recognizer fallback is never created. */
class AndroidOnDeviceSpeechTranscriber(
    private val context: Context,
    private val platform: OnDeviceSpeechRecognizerPlatform = AndroidOnDeviceSpeechRecognizerPlatform,
    private val mainExecutor: Executor = Executor { runnable -> android.os.Handler(Looper.getMainLooper()).post(runnable) },
) : SpeechTranscriber {
    private var recognizer: SpeechRecognizer? = null
    private var activeListener: SpeechTranscriptListener? = null
    private var listening = false
    private var stopping = false
    private var destroyed = false

    override fun probe(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) {
        val failure = OnDeviceRecognitionEligibility.failureFor(
            Build.VERSION.SDK_INT,
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            platform.isOnDeviceRecognitionAvailable(context),
        )
        if (failure != null) {
            onComplete(SpeechCapabilityResult(emptySet(), message = failure))
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onComplete(SpeechCapabilityResult(languages.toSet(), message = "Offline support for each language will be checked when the session starts (Android 12-13, API 31-32)."))
        } else {
            probeOnApi33(languages, onComplete)
        }
    }

    override fun start(language: SpeechLanguage, listener: SpeechTranscriptListener): SpeechStartResult {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return SpeechStartResult.Failed("On-device speech recognition must start on the Android main thread.")
        }
        OnDeviceRecognitionEligibility.failureFor(
            sdkInt = Build.VERSION.SDK_INT,
            hasRecordAudioPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            isOnDeviceRecognizerAvailable = platform.isOnDeviceRecognitionAvailable(context),
        )?.let { return SpeechStartResult.Failed(it) }

        destroyed = false
        stopping = false
        activeListener = listener
        val intent = recognitionIntent(language)
        val onDeviceRecognizer = platform.createOnDeviceSpeechRecognizer(context)
        recognizer = onDeviceRecognizer.apply { setRecognitionListener(listener(language, intent)) }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSupport(language, intent)
        } else {
            beginListening(intent)
            SpeechStartResult.Started
        }
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

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkSupport(language: SpeechLanguage, intent: Intent): SpeechStartResult {
        recognizer?.checkRecognitionSupport(intent, mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                val tag = language.code
                if (support.installedOnDeviceLanguages.any { it.matchesLanguage(tag) }) {
                    beginListening(intent)
                } else if (support.supportedOnDeviceLanguages.any { it.matchesLanguage(tag) }) {
                    requestModelDownload(language, intent)
                } else {
                    activeListener?.onError("This device does not support the ${language.displayName} on-device speech model.")
                }
            }

            override fun onError(error: Int) {
                activeListener?.onError("Unable to check on-device speech model support (error $error).")
            }
        })
        return SpeechStartResult.Downloading("Checking ${language.displayName} on-device speech model support.")
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestModelDownload(language: SpeechLanguage, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activeListener?.onModelDownload("Downloading the on-device speech model.", restartRequired = false)
            downloadWithEvents(language, intent)
        } else {
            recognizer?.triggerModelDownload(intent)
            activeListener?.onModelDownload("Requested the ${language.displayName} speech model download. Restart the session after it completes.", restartRequired = true)
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun downloadWithEvents(language: SpeechLanguage, intent: Intent) {
        recognizer?.triggerModelDownload(intent, mainExecutor, object : ModelDownloadListener {
            override fun onProgress(completedPercent: Int) = Unit

            override fun onSuccess() {
                checkSupport(language, intent)
            }

            override fun onScheduled() = Unit

            override fun onError(error: Int) {
                activeListener?.onError("The on-device speech model download failed (error $error).")
            }
        })
    }

    private fun beginListening(intent: Intent) {
        if (!destroyed && recognizer != null) {
            listening = true
            stopping = false
            recognizer?.startListening(intent)
        }
    }

    private fun listener(language: SpeechLanguage, intent: Intent) = object : RecognitionListener {
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
            } else if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                activeListener?.onDeviceUnsupported("This device does not support or cannot install the ${language.displayName} on-device speech model.")
            } else if (listening) {
                activeListener?.onError("On-device speech recognition failed (error $error).")
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

    private fun recognitionIntent(language: SpeechLanguage) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun probeOnApi33(languages: List<SpeechLanguage>, onComplete: (SpeechCapabilityResult) -> Unit) {
        val probeRecognizer = platform.createOnDeviceSpeechRecognizer(context)
        val available = mutableSetOf<SpeechLanguage>()
        val downloadable = mutableSetOf<SpeechLanguage>()
        fun next(index: Int) {
            if (index == languages.size) {
                probeRecognizer.destroy()
                onComplete(SpeechCapabilityResult(available, downloadable))
                return
            }
            val language = languages[index]
            probeRecognizer.checkRecognitionSupport(recognitionIntent(language), mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if (support.installedOnDeviceLanguages.any { it.matchesLanguage(language.code) }) available += language
                    else if (support.supportedOnDeviceLanguages.any { it.matchesLanguage(language.code) }) downloadable += language
                    next(index + 1)
                }

                override fun onError(error: Int) = next(index + 1)
            })
        }
        next(0)
    }
}

interface OnDeviceSpeechRecognizerPlatform {
    fun isOnDeviceRecognitionAvailable(context: Context): Boolean
    fun createOnDeviceSpeechRecognizer(context: Context): SpeechRecognizer
}

object AndroidOnDeviceSpeechRecognizerPlatform : OnDeviceSpeechRecognizerPlatform {
    override fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    override fun createOnDeviceSpeechRecognizer(context: Context): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
}

object OnDeviceRecognitionEligibility {
    fun failureFor(sdkInt: Int, hasRecordAudioPermission: Boolean, isOnDeviceRecognizerAvailable: Boolean): String? = when {
        sdkInt < Build.VERSION_CODES.S -> "This device requires Android 12 (API 31) or later for on-device speech recognition."
        !hasRecordAudioPermission -> "Microphone permission is required."
        !isOnDeviceRecognizerAvailable -> "This device has no on-device speech recognizer. The app will not fall back to online recognition."
        else -> null
    }
}

private fun String.matchesLanguage(languageTag: String): Boolean =
    equals(languageTag, ignoreCase = true) || startsWith("$languageTag-", ignoreCase = true)

private fun android.os.Bundle.transcript(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
