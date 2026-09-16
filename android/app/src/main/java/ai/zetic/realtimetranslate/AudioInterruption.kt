package ai.zetic.realtimetranslate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** Audio ownership for one recognizer turn. Losing it abandons, rather than resumes, that turn. */
interface RecordingAudioInterruption {
    fun start(onInterrupted: () -> Unit): Boolean
    fun stop()
}

class AndroidRecordingAudioInterruption(context: Context) : RecordingAudioInterruption {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(AudioManager::class.java)
    private val routeLoss = AudioRouteLossMonitor(applicationContext)
    private var request: AudioFocusRequest? = null
    private var interruption: (() -> Unit)? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (RecordingAudioFocusContract.interruptsFocusChange(change)) interruption?.invoke()
    }

    override fun start(onInterrupted: () -> Unit): Boolean {
        stop()
        val audioManager = manager ?: run {
            // Local JVM tests use a Context without system services. A real Android process always
            // has AudioManager, so treating the absent test service as granted keeps this seam fakeable.
            interruption = onInterrupted
            return true
        }
        interruption = onInterrupted
        val focusRequest = AudioFocusRequest.Builder(RecordingAudioFocusContract.FOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(RecordingAudioFocusContract.USAGE)
                    .setContentType(RecordingAudioFocusContract.CONTENT_TYPE)
                    .build(),
            )
            .setOnAudioFocusChangeListener(listener)
            .build()
        request = focusRequest
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            request = null
            interruption = null
            return false
        }
        routeLoss.start { interruption?.invoke() }
        return true
    }

    override fun stop() {
        val focusRequest = request
        interruption = null
        request = null
        routeLoss.stop()
        focusRequest?.let { manager?.abandonAudioFocusRequest(it) }
    }
}

internal object RecordingAudioFocusContract {
    const val FOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    const val USAGE = AudioAttributes.USAGE_ASSISTANT
    const val CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SPEECH

    fun interruptsFocusChange(change: Int): Boolean = change in setOf(
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
    )

    fun interruptsRouteChange(action: String?): Boolean =
        AudioRouteLossContract.interrupts(action)
}

enum class AudioInterruptionResponse { Ignore, AbandonUtterance }

object AudioInterruptionPolicy {
    fun response(state: SessionUiState): AudioInterruptionResponse =
        if (state.isRecognizerLive) AudioInterruptionResponse.AbandonUtterance else AudioInterruptionResponse.Ignore
}
