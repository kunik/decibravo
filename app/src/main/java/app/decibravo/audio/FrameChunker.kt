package app.decibravo.audio

import app.decibravo.scoring.FRAME_SAMPLES
import app.decibravo.scoring.frameOf

/** Збирає довільні порції PCM у повні кадри по FRAME_SAMPLES без перекриття. */
class FrameChunker {
    private var buf = ShortArray(FRAME_SAMPLES)
    private var filled = 0

    fun push(buffer: ShortArray, length: Int): List<AudioChunk> {
        val out = ArrayList<AudioChunk>()
        var pos = 0
        while (pos < length) {
            val n = minOf(FRAME_SAMPLES - filled, length - pos)
            System.arraycopy(buffer, pos, buf, filled, n)
            filled += n
            pos += n
            if (filled == FRAME_SAMPLES) {
                out.add(AudioChunk(frameOf(buf), buf))
                buf = ShortArray(FRAME_SAMPLES)
                filled = 0
            }
        }
        return out
    }
}
