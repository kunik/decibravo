package app.decibravo.scoring

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

const val MIN_FLOOR_RMS = 1e-4          // −80 дБFS

fun frameOf(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Frame {
    var sum = 0.0
    var clipped = 0
    for (i in offset until offset + length) {
        val s = samples[i].toInt()
        val v = s / 32768.0
        sum += v * v
        if (abs(s) >= CLIP_SAMPLE) clipped++
    }
    val rms = if (length > 0) sqrt(sum / length) else 0.0
    return Frame(rms, clipped)
}

fun dbfs(rms: Double): Double = if (rms <= 0.0) -120.0 else 20.0 * log10(rms)

fun fromDb(db: Double): Double = 10.0.pow(db / 20.0)

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
}

fun floorOf(frames: List<Frame>): Double = max(median(frames.map { it.rms }), MIN_FLOOR_RMS)

fun floorIsNoisy(frames: List<Frame>): Boolean {
    if (frames.isEmpty()) return false
    val med = floorOf(frames)
    return dbfs(frames.maxOf { it.rms }) - dbfs(med) > 12.0
}

fun isLoud(frame: Frame, p: MeasureParams): Boolean =
    frame.rms >= p.floorRms * fromDb(p.calibration.thresholdDb)

/** Внесок кадру в бал: (корінь з різниці потужностей над фоном / еталон)^gamma. */
fun contribution(frame: Frame, p: MeasureParams): Double {
    if (!isLoud(frame, p)) return 0.0
    val signalPower = frame.rms * frame.rms - p.floorRms * p.floorRms
    if (signalPower <= 0.0) return 0.0
    val ref = fromDb(p.calibration.refDbfs)
    return (sqrt(signalPower) / ref).pow(p.calibration.gamma)
}
