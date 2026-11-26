package app.decibravo.data

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.MeasureParams

const val MAX_DURATION_MIN = 3
const val MAX_DURATION_MAX = 60
const val QUIET_END_MIN = 1.0
const val QUIET_END_MAX = 5.0
const val QUIET_END_STEP = 0.5

fun Competition.isLocked(): Boolean = participants.any { it.measurementId != null }

fun Competition.acceptedCount(): Int = participants.count { it.measurementId != null }

fun Competition.effectiveCalibration(device: Calibration): Calibration = frozenCalibration ?: device

fun Competition.measureParams(device: Calibration): MeasureParams? {
    val floor = floorRms ?: return null
    return MeasureParams(maxDurationSec, quietEndSec, floor, effectiveCalibration(device))
}

fun parseParticipantNames(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }
