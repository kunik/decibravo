package app.decibravo.scoring

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

const val ONSET_FRAMES = 3
const val CLIP_LIMIT = 10

enum class Phase { WAITING, APPLAUSE, ENDED }
enum class EndReason { LIMIT, SILENCE, STOP, NO_APPLAUSE }

@Serializable
data class Result(
    val score: Double,
    val durationSec: Double,
    val strength: Double,
    val reason: EndReason,
    val overloaded: Boolean,
)

data class Snapshot(
    val phase: Phase,
    val score: Double,
    val applauseFrames: Int,      // кадрів від початку оплесків включно з поточним; 0 в очікуванні
    val result: Result?,          // не null лише у фазі ENDED
)

class ScoreEngine(private val params: MeasureParams) {
    private val maxFrames = params.maxDurationSec * 10
    private val quietFrames = (params.quietEndSec * 10).roundToInt()

    private var phase = Phase.WAITING
    private var frameCount = 0
    private var clipSum = 0
    private var score = 0.0
    private var applauseFrames = 0
    private var onsetContrib = 0.0       // сума внесків поточної серії гучних у WAITING
    private var onsetClip = 0            // перевантажені відліки поточної серії гучних у WAITING
    private var loudRun = 0
    private var quietRun = 0
    private var loudSpanFrames = 0  // кількість кадрів від початку оплесків до останнього гучного включно
    private var result: Result? = null

    val snapshot: Snapshot
        get() = Snapshot(phase, score, applauseFrames, result)

    fun feed(frame: Frame): Snapshot {
        if (phase == Phase.ENDED) return snapshot
        frameCount++
        val loud = isLoud(frame, params)
        when (phase) {
            Phase.WAITING -> {
                if (loud) {
                    loudRun++
                    onsetContrib += contribution(frame, params)
                    onsetClip += frame.clipped
                } else {
                    loudRun = 0
                    onsetContrib = 0.0
                    onsetClip = 0
                }
                if (loudRun >= ONSET_FRAMES) {
                    phase = Phase.APPLAUSE
                    score = onsetContrib
                    clipSum = onsetClip     // перевантаження рахуємо лише від початку оплесків
                    applauseFrames = ONSET_FRAMES
                    loudSpanFrames = ONSET_FRAMES
                    quietRun = 0
                    checkLimit()
                } else if (frameCount >= maxFrames) {
                    end(EndReason.NO_APPLAUSE)
                }
            }
            Phase.APPLAUSE -> {
                applauseFrames++
                clipSum += frame.clipped
                if (loud) {
                    score += contribution(frame, params)
                    loudSpanFrames = applauseFrames
                    quietRun = 0
                } else {
                    quietRun++
                }
                if (!checkLimit() && quietRun >= quietFrames) end(EndReason.SILENCE)
            }
            Phase.ENDED -> Unit
        }
        return snapshot
    }

    fun stop(): Snapshot {
        when (phase) {
            Phase.WAITING -> end(EndReason.NO_APPLAUSE)
            Phase.APPLAUSE -> end(EndReason.STOP)
            Phase.ENDED -> Unit
        }
        return snapshot
    }

    private fun checkLimit(): Boolean {
        if (applauseFrames >= maxFrames) {
            end(EndReason.LIMIT)
            return true
        }
        return false
    }

    private fun end(reason: EndReason) {
        val applauseStarted = reason != EndReason.NO_APPLAUSE
        val duration = if (applauseStarted) loudSpanFrames / 10.0 else 0.0
        val finalScore = if (applauseStarted) score else 0.0
        val strength = if (duration > 0.0) finalScore / duration else 0.0
        phase = Phase.ENDED
        result = Result(finalScore, duration, strength, reason, clipSum >= CLIP_LIMIT)
    }
}

fun replay(frames: List<Frame>, params: MeasureParams): Result {
    val engine = ScoreEngine(params)
    for (f in frames) {
        if (engine.feed(f).phase == Phase.ENDED) break
    }
    return engine.stop().result!!
}
