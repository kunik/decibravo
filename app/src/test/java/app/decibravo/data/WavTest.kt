package app.decibravo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    @Test fun header_andData() {
        val f = File.createTempFile("wavtest", ".wav"); WavWriter(f).use { it.write(ShortArray(4800) { 1 }); it.write(ShortArray(4800) { -1 }) }
        val b = f.readBytes(); assertEquals(44 + 19200, b.size); assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4))
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 19200, bb.getInt(4)); assertEquals(1, bb.getShort(22).toInt()); assertEquals(48000, bb.getInt(24))
        assertEquals(16, bb.getShort(34).toInt()); assertEquals(19200, bb.getInt(40)); assertEquals(1, bb.getShort(44).toInt())
    }
}
