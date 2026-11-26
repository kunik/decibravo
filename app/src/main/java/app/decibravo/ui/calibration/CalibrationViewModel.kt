package app.decibravo.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.decibravo.DeciBravoApp
import app.decibravo.audio.BAR_COUNT
import app.decibravo.data.Competition
import app.decibravo.data.Measurement
import app.decibravo.data.acceptedCount
import app.decibravo.scoring.Calibration
import app.decibravo.scoring.Entry
import app.decibravo.scoring.MeasureParams
import app.decibravo.scoring.fmtScore
import app.decibravo.scoring.fmtSeconds
import app.decibravo.scoring.fmtStrength
import app.decibravo.scoring.rank
import app.decibravo.scoring.refForTargetScore
import app.decibravo.scoring.replay
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

data class PreviewRow(val participantId: String, val name: String, val place: Int, val duration: String, val strength: String, val score: String)

/** Перераховує зараховані заміри зразка з калібруванням draft; фон, найдовший замір і тишу бере зі зразка. */
fun preview(sample: Competition, measurements: Map<String, Measurement>, draft: Calibration): List<PreviewRow> {
    val floor = sample.floorRms ?: return emptyList()
    val params = MeasureParams(sample.maxDurationSec, sample.quietEndSec, floor, draft)
    val names = sample.participants.associate { it.id to it.name }
    val results = sample.participants.mapNotNull { p ->
        measurements[p.id]?.let { m -> p.id to (m.acceptedAt to replay(m.frames, params)) }
    }.toMap()
    return rank(results.map { (id, v) -> Entry(id, v.second.score, v.second.durationSec, v.first) }).map { r ->
        val res = results.getValue(r.entry.participantId).second
        PreviewRow(
            r.entry.participantId, names.getValue(r.entry.participantId), r.place,
            fmtSeconds(res.durationSec), fmtStrength(res.strength), fmtScore(res.score),
        )
    }
}

data class CalUi(
    val draft: Calibration = Calibration(),
    val samples: List<Competition> = emptyList(),
    val sampleId: String? = null,
    val rows: List<PreviewRow> = emptyList(),
    val saved: Boolean = false,
)

class CalibrationViewModel(app: Application) : AndroidViewModel(app) {
    private val source = (app as DeciBravoApp).frameSource()
    private val _state = MutableStateFlow(MicUiState(unprocessed = source.sourceName == "UNPROCESSED"))
    val state: StateFlow<MicUiState> = _state.asStateFlow()

    private val store = (app as DeciBravoApp).store
    private val _cal = MutableStateFlow(CalUi())
    val cal: StateFlow<CalUi> = _cal.asStateFlow()
    private var sample: Competition? = null
    private var sampleMeasurements: Map<String, Measurement> = emptyMap()

    init { loadCalibration() }

    private fun loadCalibration() {
        val samples = store.listCompetitions().filter { it.acceptedCount() > 0 }
        _cal.update { it.copy(draft = store.loadCalibration(), samples = samples) }
        select(samples.firstOrNull()?.id)
    }

    fun select(id: String?) {
        val c = id?.let { store.loadCompetition(it) }
        sample = c
        sampleMeasurements = c?.participants?.mapNotNull { p ->
            p.measurementId?.let { mid -> store.loadMeasurement(c.id, mid)?.let { p.id to it } }
        }?.toMap() ?: emptyMap()
        _cal.update { it.copy(sampleId = c?.id) }
        recompute()
    }

    private fun recompute() {
        val c = sample
        _cal.update { it.copy(rows = if (c == null) emptyList() else preview(c, sampleMeasurements, it.draft)) }
    }

    fun setDraft(f: (Calibration) -> Calibration) {
        _cal.update { it.copy(draft = f(it.draft), saved = false) }
        recompute()
    }

    fun reset() = setDraft { Calibration() }

    fun save() {
        store.saveCalibration(_cal.value.draft)
        _cal.update { it.copy(saved = true) }
    }

    /** Підбирає опорний рівень, щоб замір учасника дав targetScore; false, якщо це неможливо. */
    fun fitScale(participantId: String, targetScore: Double): Boolean {
        val c = sample ?: return false
        val m = sampleMeasurements[participantId] ?: return false
        val floor = c.floorRms ?: return false
        val params = MeasureParams(c.maxDurationSec, c.quietEndSec, floor, _cal.value.draft)
        val ref = refForTargetScore(m.frames, params, targetScore) ?: return false
        setDraft { it.copy(refDbfs = ref) }
        return true
    }

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

    fun onActivityStopped() = stopAll()

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
