package app.decibravo.data

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.Frame
import app.decibravo.scoring.Result
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Participant(val id: String, val name: String, val measurementId: String? = null)

@Serializable
data class Competition(
    val id: String,
    val name: String,
    val createdAt: Long,
    val maxDurationSec: Int = 10,
    val quietEndSec: Double = 2.0,
    val floorRms: Double? = null,
    val floorMeasuredAt: Long? = null,
    val frozenCalibration: Calibration? = null,
    val participants: List<Participant> = emptyList(),
)

@Serializable
data class Measurement(
    val id: String,
    val participantId: String,
    val acceptedAt: Long,
    val audioSource: String,
    val frames: List<Frame>,
    val result: Result,
    val wavFile: String,
)

class PendingMeasurement(
    val frames: List<Frame>,
    val result: Result,
    val audioSource: String,
    val wavTemp: File,
)
