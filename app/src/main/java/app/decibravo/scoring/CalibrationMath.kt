package app.decibravo.scoring

import kotlin.math.log10

/** Нове refDbfs, за якого ці кадри дали б targetScore; null, якщо це неможливо. */
fun refForTargetScore(frames: List<Frame>, params: MeasureParams, targetScore: Double): Double? {
    val score = replay(frames, params).score
    if (score <= 0.0 || targetScore <= 0.0) return null
    val c = params.calibration
    val ref = c.refDbfs + (20.0 / c.gamma) * log10(score / targetScore)
    return if (ref.isNaN() || ref < -60.0 || ref > 0.0) null else ref
}
