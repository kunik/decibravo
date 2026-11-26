package app.decibravo.ui.floor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.decibravo.DeciBravoApp
import app.decibravo.scoring.Frame
import app.decibravo.scoring.dbfs
import app.decibravo.scoring.floorIsNoisy
import app.decibravo.scoring.floorOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

const val FLOOR_MEASURE_FRAMES = 30   // 3 с

sealed interface FloorState {
    data object Idle : FloorState
    data class Counting(val secondsLeft: Int) : FloorState
    data class Done(val rms: Double, val db: Int, val noisy: Boolean) : FloorState
    data object Error : FloorState
}

class FloorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val application = app as DeciBravoApp
    private val store = application.store
    private val source = application.frameSource()
    private val competitionId: String = handle.get<String>("id") ?: ""
    val thenParticipant: String? = handle.get<String>("then")

    private val _state = MutableStateFlow<FloorState>(FloorState.Idle)
    val state: StateFlow<FloorState> = _state.asStateFlow()
    private var job: Job? = null

    fun measure() {
        if (job != null) return
        _state.value = FloorState.Counting(3)
        val frames = ArrayList<Frame>()
        job = viewModelScope.launch {
            try {
                source.chunks().take(FLOOR_MEASURE_FRAMES).collect { chunk ->
                    frames.add(chunk.frame)
                    if (frames.size < FLOOR_MEASURE_FRAMES) _state.value = FloorState.Counting(3 - frames.size / 10)
                }
                val rms = floorOf(frames)
                _state.value = FloorState.Done(rms, Math.round(dbfs(rms)).toInt(), floorIsNoisy(frames))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = FloorState.Error
            } finally {
                job = null
            }
        }
    }

    /** Активність пішла у фон: триваючий відлік фону скасовуємо, щоб у змагання не потрапив нульовий фон. */
    fun onActivityStopped() {
        if (_state.value is FloorState.Counting) {
            job?.cancel()
            job = null
            _state.value = FloorState.Idle
        }
    }

    fun remeasure() {
        _state.value = FloorState.Idle
        measure()
    }

    /** Зберігає фон у змаганні. */
    fun keep() {
        val done = _state.value as? FloorState.Done ?: return
        val c = store.loadCompetition(competitionId) ?: return
        store.saveCompetition(c.copy(floorRms = done.rms, floorMeasuredAt = System.currentTimeMillis()))
    }

    override fun onCleared() { job?.cancel() }
}
