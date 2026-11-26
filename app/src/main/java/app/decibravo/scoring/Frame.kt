package app.decibravo.scoring

import kotlinx.serialization.Serializable

const val SAMPLE_RATE = 48_000
const val FRAME_SAMPLES = 4_800
const val FRAME_SEC = 0.1
const val CLIP_SAMPLE = 32_700          // |відлік| ≥ цього = перевантажений

@Serializable
data class Frame(val rms: Double, val clipped: Int)
