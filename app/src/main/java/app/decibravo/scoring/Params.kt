package app.decibravo.scoring

import kotlinx.serialization.Serializable

@Serializable
data class Calibration(
    val refDbfs: Double = -30.0,
    val gamma: Double = 1.0,
    val thresholdDb: Double = 6.0,
)

data class MeasureParams(
    val maxDurationSec: Int,
    val quietEndSec: Double,
    val floorRms: Double,
    val calibration: Calibration,
)
