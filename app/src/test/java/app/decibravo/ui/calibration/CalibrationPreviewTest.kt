package app.decibravo.ui.calibration

import app.decibravo.data.Competition
import app.decibravo.data.Measurement
import app.decibravo.data.Participant
import app.decibravo.scoring.Calibration
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.Frame
import app.decibravo.scoring.Result
import app.decibravo.scoring.fromDb
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class CalibrationPreviewTest {
    private val FLOOR = 0.001
    private val REF = fromDb(-30.0)
    private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)
    private val DOUBLE = Frame(sqrt(4 * REF * REF + FLOOR * FLOOR), 0)
    private fun seq(vararg parts: Pair<Int, Frame>) = parts.flatMap { (n, f) -> List(n) { f } }

    private val sample = Competition(
        "c", "Проба", 0, floorRms = FLOOR,
        participants = listOf(Participant("pa", "A", "ma"), Participant("pb", "B", "mb")),
    )
    private fun m(pid: String, mid: String, frames: List<Frame>, at: Long) =
        Measurement(mid, pid, at, "UNPROCESSED", frames, Result(0.0, 0.0, 0.0, EndReason.STOP, false), "$mid.wav")
    private val ms = mapOf(
        "pa" to m("pa", "ma", seq(50 to LOUD), 1),
        "pb" to m("pb", "mb", seq(30 to DOUBLE), 2),
    )

    @Test fun preview_withDefaults() {
        val rows = preview(sample, ms, Calibration())
        assertEquals(listOf("B", "A"), rows.map { it.name }); assertEquals(listOf("60", "50"), rows.map { it.score }); assertEquals("3,0", rows[0].duration)
    }
    @Test fun preview_gammaChangesOrder() {
        val rows = preview(sample, ms, Calibration(gamma = 0.5))     // B: 30·√2 ≈ 42, A: 50
        assertEquals(listOf("A", "B"), rows.map { it.name }); assertEquals(listOf(1, 2), rows.map { it.place })
    }
    @Test fun preview_ignoresFrozenCalibrationOfSample() {
        assertEquals(preview(sample, ms, Calibration(refDbfs = -20.0)), preview(sample.copy(frozenCalibration = Calibration(refDbfs = -40.0)), ms, Calibration(refDbfs = -20.0)))
    }
}
