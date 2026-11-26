package app.decibravo.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import app.decibravo.scoring.FRAME_SAMPLES
import app.decibravo.scoring.SAMPLE_RATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class Recorder(private val context: Context) : FrameSource {
    private val unprocessed: Boolean = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    override val sourceName: String = if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION"

    @SuppressLint("MissingPermission")
    override fun chunks(): Flow<AudioChunk> = flow {
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBuf, 2 * FRAME_SAMPLES * 2)
        val record = AudioRecord(
            source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
        )
        var agc: AutomaticGainControl? = null
        var ns: NoiseSuppressor? = null
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord не ініціалізовано" }
            if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(record.audioSessionId)?.also { a -> runCatching { a.enabled = false } }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(record.audioSessionId)?.also { n -> runCatching { n.enabled = false } }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Запис не розпочато" }
            val chunker = FrameChunker()
            val silence = ZeroRunDetector()
            val buf = ShortArray(FRAME_SAMPLES)
            while (true) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) throw IllegalStateException("AudioRecord.read повернув $n")
                for (c in chunker.push(buf, n)) {
                    if (silence.push(c.pcm)) throw SilentCaptureException()
                    emit(c)
                }
            }
        } finally {
            try { record.stop() } catch (_: Exception) { }
            agc?.release()
            ns?.release()
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
