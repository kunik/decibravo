package app.decibravo.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameChunkerTest {
    @Test fun oddSizedPushes_makeWholeFrames() {
        val c = FrameChunker(); val buf = ShortArray(1000) { 16384 }; var out = 0
        repeat(12) { out += c.push(buf, 1000).size }            // 12 000 відліків = 2 повні кадри
        assertEquals(2, out); assertEquals(1, c.push(buf, 1000).size + c.push(buf, 1000).size + c.push(buf, 400).size)
    }
    @Test fun chunk_hasFrameAndPcm() {
        val ch = FrameChunker().push(ShortArray(4800) { 16384 }, 4800).single()
        assertEquals(0.5, ch.frame.rms, 1e-9); assertEquals(4800, ch.pcm.size)
    }
    @Test fun lengthParameter_isRespected() { assertTrue(FrameChunker().push(ShortArray(9600), 4799).isEmpty()) }
}
