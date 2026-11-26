package app.decibravo.data

import app.decibravo.scoring.SAMPLE_RATE
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** WAV 48 кГц, моно, 16 біт, little-endian, заголовок 44 байти. */
class WavWriter(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    fun write(pcm: ShortArray, length: Int = pcm.size) {
        val bb = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) bb.putShort(pcm[i])
        raf.write(bb.array())
        dataBytes += length * 2L
    }

    override fun close() {
        if (closed) return
        closed = true
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt())
        h.put("WAVE".toByteArray()).put("fmt ".toByteArray())
        h.putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
        h.putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(h.array())
        raf.close()
    }
}
