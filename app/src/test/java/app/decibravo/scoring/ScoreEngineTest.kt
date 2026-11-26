package app.decibravo.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ScoreEngineTest {
    private val FLOOR = 0.001
    private val REF = fromDb(-30.0)
    private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)   // рівно 1 бал за кадр
    private val QUIET = Frame(FLOOR, 0)
    private val p = MeasureParams(10, 2.0, FLOOR, Calibration())
    private fun seq(vararg parts: Pair<Int, Frame>) = parts.flatMap { (n, f) -> List(n) { f } }
    private fun live(frames: List<Frame>, params: MeasureParams = p): Pair<Result?, Int> {
        val e = ScoreEngine(params); var used = 0
        for (f in frames) { used++; if (e.feed(f).phase == Phase.ENDED) break }
        return e.snapshot.result to used
    }
    private fun assertResult(r: Result, score: Double, dur: Double, reason: EndReason) {
        assertEquals(score, r.score, 1e-6); assertEquals(dur, r.durationSec, 1e-9); assertEquals(reason, r.reason)
    }

    @Test fun applauseThenSilence() {
        val (r, used) = live(seq(50 to LOUD, 30 to QUIET))
        assertResult(r!!, 50.0, 5.0, EndReason.SILENCE); assertEquals(10.0, r.strength, 1e-6); assertEquals(70, used)
    }
    @Test fun silenceBeforeApplause_changesNothing() {
        assertResult(live(seq(30 to QUIET, 50 to LOUD, 30 to QUIET)).first!!, 50.0, 5.0, EndReason.SILENCE)
    }
    @Test fun oneOrTwoLoudFramesBeforeApplause_areIgnored() {
        val r = live(seq(5 to QUIET, 2 to LOUD, 5 to QUIET, 50 to LOUD, 30 to QUIET)).first!!
        assertResult(r, 50.0, 5.0, EndReason.SILENCE)
    }
    @Test fun onsetOnThirdLoudFrame_countsAllThree() {
        val e = ScoreEngine(p)
        assertEquals(Phase.WAITING, e.feed(LOUD).phase); assertEquals(Phase.WAITING, e.feed(LOUD).phase)
        val s = e.feed(LOUD)
        assertEquals(Phase.APPLAUSE, s.phase); assertEquals(3, s.applauseFrames); assertEquals(3.0, s.score, 1e-6)
    }
    @Test fun pauseShorterThanQuietEnd_doesNotEnd() {
        assertResult(live(seq(20 to LOUD, 19 to QUIET, 20 to LOUD, 30 to QUIET)).first!!, 40.0, 5.9, EndReason.SILENCE)
    }
    @Test fun singleLoudFrameDuringApplause_counts() {
        assertResult(live(seq(20 to LOUD, 10 to QUIET, 1 to LOUD, 30 to QUIET)).first!!, 21.0, 3.1, EndReason.SILENCE)
    }
    @Test fun limit_endsExactlyAtMaxDuration() {
        val (r, used) = live(seq(150 to LOUD))
        assertResult(r!!, 100.0, 10.0, EndReason.LIMIT); assertEquals(100, used)
    }
    @Test fun limit_countsFromOnsetNotFromStart() {
        val (r, used) = live(seq(40 to QUIET, 150 to LOUD))
        assertResult(r!!, 100.0, 10.0, EndReason.LIMIT); assertEquals(140, used)
    }
    @Test fun noApplause_endsAfterMaxDuration() {
        val (r, used) = live(seq(200 to QUIET))
        assertResult(r!!, 0.0, 0.0, EndReason.NO_APPLAUSE); assertEquals(0.0, r.strength, 0.0); assertEquals(100, used)
    }
    @Test fun noApplause_evenWithPendingOnsetAtTimeout() {
        assertEquals(EndReason.NO_APPLAUSE, live(seq(98 to QUIET, 50 to LOUD)).first!!.reason)
    }
    @Test fun stop_duringApplause() {
        val e = ScoreEngine(p); repeat(30) { e.feed(LOUD) }
        assertResult(e.stop().result!!, 30.0, 3.0, EndReason.STOP)
    }
    @Test fun stop_whileWaiting_isNoApplause() { assertEquals(EndReason.NO_APPLAUSE, ScoreEngine(p).stop().result!!.reason) }
    @Test fun feedAfterEnd_isIgnored() {
        val e = ScoreEngine(p); repeat(30) { e.feed(LOUD) }; val r = e.stop().result
        assertEquals(r, e.feed(LOUD).result)
    }
    @Test fun replay_ofStoppedSeries_isStop() { assertResult(replay(seq(30 to LOUD), p), 30.0, 3.0, EndReason.STOP) }
    @Test fun replay_equalsLive_forSilenceAndLimit() {
        for (frames in listOf(seq(10 to QUIET, 50 to LOUD, 30 to QUIET), seq(150 to LOUD))) {
            val (r, used) = live(frames); assertEquals(r, replay(frames.take(used), p))
        }
    }
    @Test fun sameFrames_sameResult() { val f = seq(7 to QUIET, 33 to LOUD, 25 to QUIET); assertEquals(replay(f, p), replay(f, p)) }
    @Test fun twiceAsLong_twiceTheScore() {
        assertEquals(2 * replay(seq(20 to LOUD), p).score, replay(seq(40 to LOUD), p).score, 1e-6)
    }
    @Test fun louder_scoresMore() {
        val louder = Frame(sqrt(4 * REF * REF + FLOOR * FLOOR), 0)
        assertEquals(60.0, replay(seq(30 to louder), p).score, 1e-6)
    }
    @Test fun scoreEqualsStrengthTimesDuration() {
        val r = replay(seq(20 to LOUD, 5 to QUIET, 20 to LOUD), p); assertEquals(r.score, r.strength * r.durationSec, 1e-9)
    }
    @Test fun refAndGamma_keepDurationAndReason() {
        val f = seq(10 to QUIET, 50 to LOUD, 20 to QUIET); val a = replay(f, p)
        val b = replay(f, p.copy(calibration = Calibration(refDbfs = -20.0, gamma = 1.6)))
        assertEquals(a.durationSec, b.durationSec, 0.0); assertEquals(a.reason, b.reason); assertNotEquals(a.score, b.score, 1e-3)
    }
    @Test fun overloaded_fromTenClippedSamples() {
        assertTrue(replay(seq(30 to LOUD) + Frame(LOUD.rms, 10), p).overloaded)
        assertFalse(replay(seq(30 to LOUD) + Frame(LOUD.rms, 9), p).overloaded)
    }
    @Test fun quietEndHalfSeconds() {
        val (r, used) = live(seq(20 to LOUD, 40 to QUIET), p.copy(quietEndSec = 1.5))
        assertEquals(EndReason.SILENCE, r!!.reason); assertEquals(35, used)
    }
}
