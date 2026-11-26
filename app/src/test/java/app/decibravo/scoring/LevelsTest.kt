package app.decibravo.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class LevelsTest {
    private val cal = Calibration()                 // −30 дБFS, gamma 1, поріг 6 дБ
    private val p = MeasureParams(10, 2.0, floorRms = 0.001, calibration = cal)
    private val REF = fromDb(-30.0)

    @Test fun frameOf_fullScaleSquare() {
        val f = frameOf(ShortArray(4800) { if (it % 2 == 0) 32767 else -32767 })
        assertEquals(32767 / 32768.0, f.rms, 1e-9); assertEquals(4800, f.clipped)
    }
    @Test fun frameOf_silence() { val f = frameOf(ShortArray(4800)); assertEquals(0.0, f.rms, 0.0); assertEquals(0, f.clipped) }
    @Test fun frameOf_countsOnlyNearFullScale() {
        val s = ShortArray(4800) { 1000 }; s[0] = 32700; s[1] = -32700; s[2] = 32699
        assertEquals(2, frameOf(s).clipped)
    }
    @Test fun dbfs_values() { assertEquals(-20.0, dbfs(0.1), 1e-9); assertEquals(-120.0, dbfs(0.0), 0.0) }
    @Test fun floorOf_isMedian() { assertEquals(0.002, floorOf(listOf(0.001, 0.002, 0.9).map { Frame(it, 0) }), 1e-12) }
    @Test fun floorOf_clampsToMinimum() { assertEquals(1e-4, floorOf(List(30) { Frame(0.0, 0) }), 0.0) }
    @Test fun floorIsNoisy_detectsSpike() {
        assertFalse(floorIsNoisy(List(30) { Frame(0.001, 0) }))
        assertTrue(floorIsNoisy(List(29) { Frame(0.001, 0) } + Frame(0.005, 0)))   // +14 дБ
    }
    @Test fun isLoud_atThreshold() {
        assertTrue(isLoud(Frame(0.001 * fromDb(6.0), 0), p)); assertFalse(isLoud(Frame(0.0019, 0), p))
    }
    @Test fun contribution_atReference_isOnePointPerFrame() {
        val rms = sqrt(REF * REF + 0.001 * 0.001)
        assertEquals(1.0, contribution(Frame(rms, 0), p), 1e-9)
    }
    @Test fun contribution_gammaTwo_doubleLevel_isFourPoints() {
        val rms = sqrt(4 * REF * REF + 0.001 * 0.001)
        assertEquals(4.0, contribution(Frame(rms, 0), p.copy(calibration = cal.copy(gamma = 2.0))), 1e-9)
    }
    @Test fun contribution_quietFrame_isZero() { assertEquals(0.0, contribution(Frame(0.0015, 0), p), 0.0) }
}
