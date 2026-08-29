package ai.zetic.realtimetranslate

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pipeline boundary for: PCM -> pyannote -> Moonshine encoder/decoder -> Hy-MT2. */
class RealtimeTranslationPipeline(
    private val gate: ModelCompatibilityGate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun start(
        context: Context,
        input: SpeechLanguage,
        output: TranslationLanguage,
    ): PipelineResult = withContext(ioDispatcher) {
        when (val result = gate.check(input, output)) {
            GateResult.Ready -> {
                // The four-model runtime contract is not available yet. This branch is intentionally
                // not implemented until model artifacts, tensor contracts, and device evidence are supplied.
                PipelineResult.Failed("모델 실행 계약이 아직 연결되지 않았습니다.")
            }
            is GateResult.Blocked -> PipelineResult.Failed(result.reason)
        }
    }

    fun createAudioRecord(context: Context): AudioRecord? {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val minBuffer = AudioRecord.getMinBufferSize(SampleRate, ChannelConfig, Encoding).coerceAtLeast(SampleRate)
        return AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SampleRate, ChannelConfig, Encoding, minBuffer)
    }

    companion object {
        const val PyannoteModel = "ajayshah/pyannote-segmentation-3.0"
        const val MoonshineEncoderModel = "realtonypark/Moonshine-Streaming-ASR-Encoder"
        const val MoonshineDecoderModel = "realtonypark/Moonshine-Streaming-ASR-Decoder"
        const val HyMt2Model = "SJ_zetic/Hy-MT2-1.8B"
        private const val SampleRate = 16_000
        private const val ChannelConfig = AudioFormat.CHANNEL_IN_MONO
        private const val Encoding = AudioFormat.ENCODING_PCM_16BIT
    }
}

sealed interface PipelineResult {
    data object Started : PipelineResult
    data class Failed(val reason: String) : PipelineResult
}
