package app.decibravo.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.decibravo.DeciBravoApp
import app.decibravo.audio.BAR_COUNT
import app.decibravo.scoring.Frame
import app.decibravo.scoring.dbfs
import app.decibravo.scoring.floorIsNoisy
import app.decibravo.scoring.floorOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MIC_MIN_DB = -80.0
const val MIC_PEAK_FRAMES = 50      // 5 с
const val FLOOR_FRAMES = 30         // 3 с

sealed interface FloorCheck {
    data object Idle : FloorCheck
    data class Counting(val secondsLeft: Int) : FloorCheck
    data class Done(val db: Int, val noisy: Boolean) : FloorCheck
}

data class MicUiState(
    val unprocessed: Boolean = true,
    val listening: Boolean = false,
    val levelDb: Double = MIC_MIN_DB,
    val peakDb: Double = MIC_MIN_DB,
    val bars: List<Float> = emptyList(),
    val floor: FloorCheck = FloorCheck.Idle,
    val error: Boolean = false,
)

class CalibrationViewModel(app: Application) : AndroidViewModel(app) {
    private val source = (app as DeciBravoApp).frameSource()
    private val _state = MutableStateFlow(MicUiState(unprocessed = source.sourceName == "UNPROCESSED"))
    val state: StateFlow<MicUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val recent = ArrayDeque<Double>()
    private var floorFrames: MutableList<Frame>? = null

    fun toggleListening() {
        if (_state.value.listening) stopAll() else start(listening = true)
    }

    fun measureFloor() {
        if (floorFrames != null) return
        floorFrames = ArrayList()
        _state.update { it.copy(floor = FloorCheck.Counting(3)) }
        if (job == null) start(listening = false)
    }

    private fun start(listening: Boolean) {
        if (job != null) {
            _state.update { it.copy(listening = true) }
            return
        }
        recent.clear()
        _state.update { it.copy(listening = listening, error = false, levelDb = MIC_MIN_DB, peakDb = MIC_MIN_DB, bars = emptyList()) }
        job = viewModelScope.launch {
            try {
                source.chunks().collect { chunk ->
                    val db = dbfs(chunk.frame.rms).coerceIn(MIC_MIN_DB, 0.0)
                    recent.addLast(db)
                    while (recent.size > MIC_PEAK_FRAMES) recent.removeFirst()
                    val bars = recent.takeLast(BAR_COUNT).map { ((it - MIC_MIN_DB) / -MIC_MIN_DB).toFloat() }
                    _state.update { it.copy(levelDb = db, peakDb = recent.max(), bars = bars) }
                    collectFloor(chunk.frame)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                floorFrames = null
                job = null
                _state.update { it.copy(listening = false, error = true, floor = FloorCheck.Idle) }
            }
        }
    }

    private fun collectFloor(frame: Frame) {
        val list = floorFrames ?: return
        list.add(frame)
        if (list.size < FLOOR_FRAMES) {
            _state.update { it.copy(floor = FloorCheck.Counting(3 - list.size / 10)) }
            return
        }
        floorFrames = null
        val db = Math.round(dbfs(floorOf(list))).toInt()
        _state.update { it.copy(floor = FloorCheck.Done(db, floorIsNoisy(list))) }
        if (!_state.value.listening) stopAll(keepFloor = true)
    }

    private fun stopAll(keepFloor: Boolean = false) {
        job?.cancel()
        job = null
        floorFrames = null
        _state.update {
            it.copy(
                listening = false, levelDb = MIC_MIN_DB, bars = emptyList(),
                floor = if (keepFloor) it.floor else FloorCheck.Idle,
            )
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
