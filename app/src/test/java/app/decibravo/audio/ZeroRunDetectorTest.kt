package app.decibravo.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroRunDetectorTest {
    private val zeros = ShortArray(4800)
    private fun quiet() = ShortArray(4800) { if (it % 7 == 0) 1 else 0 }   // найтихіший справжній шум: окремі відліки ±1

    @Test fun runOfZeroFrames_triggersAtLimit() {
        val d = ZeroRunDetector(); repeat(SILENT_RUN_FRAMES - 1) { assertFalse(d.push(zeros)) }
        assertTrue(d.push(zeros))
    }
    @Test fun oneNonZeroSample_resetsTheRun() {
        val d = ZeroRunDetector(); repeat(SILENT_RUN_FRAMES - 1) { d.push(zeros) }
        assertFalse(d.push(ShortArray(4800).also { it[4799] = 1 })); repeat(SILENT_RUN_FRAMES - 1) { assertFalse(d.push(zeros)) }
    }
    @Test fun quietRoomNoise_neverTriggers() {
        val d = ZeroRunDetector(); repeat(1000) { assertFalse(d.push(quiet())) }
    }
    @Test fun startupZeros_shorterThanOneSecond_areTolerated() {
        val d = ZeroRunDetector(); repeat(3) { assertFalse(d.push(zeros)) }; assertFalse(d.push(quiet()))
    }
}
