package app.decibravo.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class CalibrationMathTest {
    private val FLOOR = 0.001
    private val REF = fromDb(-30.0)
    private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)   // рівно 1 бал за кадр
    private val QUIET = Frame(FLOOR, 0)
    private val p = MeasureParams(10, 2.0, FLOOR, Calibration())
    private fun seq(vararg parts: Pair<Int, Frame>) = parts.flatMap { (n, f) -> List(n) { f } }

    @Test fun refForTarget_hitsTarget() {
        val f = seq(50 to LOUD); val ref = refForTargetScore(f, p, 100.0)!!
        assertEquals(100.0, replay(f, p.copy(calibration = p.calibration.copy(refDbfs = ref))).score, 1e-6)
    }
    @Test fun refForTarget_respectsGamma() {
        val pg = p.copy(calibration = Calibration(gamma = 1.6)); val f = seq(50 to LOUD)
        val ref = refForTargetScore(f, pg, 123.0)!!
        assertEquals(123.0, replay(f, pg.copy(calibration = pg.calibration.copy(refDbfs = ref))).score, 1e-6)
    }
    @Test fun refForTarget_zeroScore_isNull() { assertNull(refForTargetScore(seq(50 to QUIET), p, 100.0)) }
    @Test fun refForTarget_outOfRange_isNull() { assertNull(refForTargetScore(seq(50 to LOUD), p, 1e9)) }
}
