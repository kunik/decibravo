package app.decibravo.audio

import app.decibravo.data.PendingMeasurement
import app.decibravo.data.WavWriter
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.Frame
import app.decibravo.scoring.MeasureParams
import app.decibravo.scoring.Phase
import app.decibravo.scoring.Result
import app.decibravo.scoring.ScoreEngine
import app.decibravo.scoring.Snapshot
import app.decibravo.scoring.dbfs
import java.io.File

const val BAR_COUNT = 50
const val BAR_RANGE_DB = 40.0

sealed interface MeasureState {
    data object Ready : MeasureState
    data class Running(val waiting: Boolean, val score: Double, val elapsedSec: Double, val bars: List<Float>) : MeasureState
    data class Done(val result: Result) : MeasureState
    data class Failed(val cause: Throwable) : MeasureState
}

/** Чиста логіка одного заміру, без потоків. */
class MeasureSession(
    private val params: MeasureParams,
    private val sourceName: String,
    private val wavTemp: File,
) {
    private var engine: ScoreEngine? = null
    private var wav: WavWriter? = null
    private val frames = ArrayList<Frame>()
    private val barsPerFrame = params.maxDurationSec * 10 / BAR_COUNT.toDouble()   // k: кадрів на стовпчик
    private val floorDb = dbfs(params.floorRms)

    var state: MeasureState = MeasureState.Ready
        private set

    fun start(): MeasureState {
        if (state != MeasureState.Ready) return state
        engine = ScoreEngine(params)
        frames.clear()
        wav = WavWriter(wavTemp)
        state = MeasureState.Running(waiting = true, score = 0.0, elapsedSec = 0.0, bars = emptyList())
        return state
    }

    fun onChunk(chunk: AudioChunk): MeasureState {
        if (state !is MeasureState.Running) return state
        val e = engine!!
        try {
            wav!!.write(chunk.pcm)
        } catch (t: Throwable) {
            return fail(t)
        }
        frames.add(chunk.frame)
        return finishOrRun(e.feed(chunk.frame))
    }

    fun stop(): MeasureState {
        if (state !is MeasureState.Running) return state
        return finishOrRun(engine!!.stop())
    }

    fun fail(cause: Throwable): MeasureState {
        closeWav()
        wavTemp.delete()
        state = MeasureState.Failed(cause)
        return state
    }

    fun cancel() {
        closeWav()
        wavTemp.delete()
        engine = null
        frames.clear()
        state = MeasureState.Ready
    }

    fun pending(): PendingMeasurement? {
        val done = state as? MeasureState.Done ?: return null
        return PendingMeasurement(frames.toList(), done.result, sourceName, wavTemp)
    }

    private fun finishOrRun(s: Snapshot): MeasureState {
        if (s.phase == Phase.ENDED) {
            closeWav()
            state = MeasureState.Done(s.result!!)
        } else {
            state = running(s)
        }
        return state
    }

    private fun running(s: Snapshot): MeasureState.Running {
        if (s.phase == Phase.WAITING) return MeasureState.Running(true, 0.0, 0.0, emptyList())
        val bars = FloatArray(BAR_COUNT) { -1f }
        val start = frames.size - s.applauseFrames
        for (n in 0 until s.applauseFrames) {
            val i = (n / barsPerFrame).toInt().coerceAtMost(BAR_COUNT - 1)
            val v = ((dbfs(frames[start + n].rms) - floorDb) / BAR_RANGE_DB).coerceIn(0.0, 1.0).toFloat()
            if (v > bars[i]) bars[i] = v
        }
        return MeasureState.Running(false, s.score, s.applauseFrames / 10.0, bars.toList())
    }

    private fun closeWav() {
        try { wav?.close() } catch (_: Exception) { }
        wav = null
    }
}
