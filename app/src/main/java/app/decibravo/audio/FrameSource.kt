package app.decibravo.audio

import app.decibravo.scoring.Frame
import kotlinx.coroutines.flow.Flow

class AudioChunk(val frame: Frame, val pcm: ShortArray)      // pcm.size == FRAME_SAMPLES

interface FrameSource {
    val sourceName: String                 // "UNPROCESSED" або "VOICE_RECOGNITION"
    fun chunks(): Flow<AudioChunk>         // холодний потік; запис іде, поки його збирають; помилка = виняток у потоці
}
