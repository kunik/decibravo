package app.decibravo.audio

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.FRAME_SAMPLES
import app.decibravo.scoring.Frame
import app.decibravo.scoring.MeasureParams
import app.decibravo.scoring.fromDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.sqrt

class MeasureSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    private val FLOOR = 0.001
    private val REF = fromDb(-30.0)
    private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)
    private val LOUD_RMS = LOUD.rms
    private val p = MeasureParams(10, 2.0, FLOOR, Calibration())
    private fun chunk(rms: Double) = AudioChunk(Frame(rms, 0), ShortArray(FRAME_SAMPLES))

    @Test fun fullRun_endsInDone_withPendingAndWav() {
        val wav = tmp.newFile("m.wav"); val s = MeasureSession(p, "UNPROCESSED", wav); s.start()
        repeat(50) { s.onChunk(chunk(LOUD_RMS)) }; repeat(20) { s.onChunk(chunk(FLOOR)) }
        val done = s.state as MeasureState.Done; assertEquals(5.0, done.result.durationSec, 1e-9)
        val pm = s.pending()!!; assertEquals(70, pm.frames.size); assertEquals("UNPROCESSED", pm.audioSource)
        assertEquals(44 + 70 * 9600, wav.length().toInt())
    }
    @Test fun waitingThenRunning() {
        val s = MeasureSession(p, "x", tmp.newFile()); assertTrue((s.start() as MeasureState.Running).waiting)
        s.onChunk(chunk(LOUD_RMS)); s.onChunk(chunk(LOUD_RMS)); val r = s.onChunk(chunk(LOUD_RMS)) as MeasureState.Running
        assertFalse(r.waiting); assertEquals(0.3, r.elapsedSec, 1e-9); assertEquals(BAR_COUNT, r.bars.size)
        assertTrue(r.bars[0] > 0f); assertEquals(-1f, r.bars[49], 0f)
    }
    @Test fun stop_givesDone() {
        val s = MeasureSession(p, "x", tmp.newFile()); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }
        assertEquals(EndReason.STOP, (s.stop() as MeasureState.Done).result.reason)
    }
    @Test fun cancel_leavesNoPending() {
        val wav = tmp.newFile(); val s = MeasureSession(p, "x", wav); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }; s.cancel()
        assertNull(s.pending()); assertFalse(wav.exists()); assertEquals(MeasureState.Ready, s.state)
    }
    @Test fun sourceError_endsInErrorState() {
        val wav = tmp.newFile(); val s = MeasureSession(p, "x", wav); s.start(); s.onChunk(chunk(LOUD_RMS))
        assertTrue(s.fail(IllegalStateException("mic")) is MeasureState.Failed); assertNull(s.pending()); assertFalse(wav.exists())
    }
    @Test fun chunksAfterDone_areIgnored() {
        val s = MeasureSession(p, "x", tmp.newFile()); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }; val d = s.stop()
        assertEquals(d, s.onChunk(chunk(LOUD_RMS))); assertEquals(30, s.pending()!!.frames.size)
    }
    @Test fun wavOpenFailure_endsInFailed_insteadOfThrowing() {
        val s = MeasureSession(p, "UNPROCESSED", java.io.File(tmp.root, "нема/такої/теки.wav"))
        assertTrue(s.start() is MeasureState.Failed)
    }
}
