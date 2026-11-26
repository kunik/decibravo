package app.decibravo.ui.measure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.decibravo.DeciBravoApp
import app.decibravo.audio.MeasureSession
import app.decibravo.audio.MeasureState
import app.decibravo.data.measureParams
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.Entry
import app.decibravo.scoring.fmtPlace
import app.decibravo.scoring.placeIfAccepted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

private fun secondsText(v: Double): String =
    if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')

fun endReasonText(r: EndReason, quietEndSec: Double): String = when (r) {
    EndReason.LIMIT -> "Закінчено: досягнуто найдовшого заміру"
    EndReason.SILENCE -> "Закінчено: ${secondsText(quietEndSec)} с тиші"
    EndReason.STOP -> "Закінчено: зупинено вручну"
    EndReason.NO_APPLAUSE -> "Оплесків не було"
}

fun readyHint(maxDurationSec: Int, quietEndSec: Double): String =
    "Натисніть «Старт», щойно учасник закінчив. Замір зупиниться сам: через $maxDurationSec с або після ${secondsText(quietEndSec)} с тиші."

fun placeText(place: Int): String = "зараз ${fmtPlace(place)} місце"

data class MeasureUi(
    val participantName: String = "",
    val maxDurationSec: Int = 10,
    val quietEndSec: Double = 2.0,
    val state: MeasureState = MeasureState.Ready,
    val place: Int? = null,
    val accepted: Boolean = false,
)

class MeasureViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val application = app as DeciBravoApp
    private val store = application.store
    private val source = application.frameSource()
    private val competitionId: String = handle.get<String>("id") ?: ""
    private val participantId: String = handle.get<String>("participantId") ?: ""

    private val competition = store.loadCompetition(competitionId)
    private val others: List<Entry> = competition?.participants.orEmpty()
        .filter { it.id != participantId }
        .mapNotNull { p ->
            val m = p.measurementId?.let { store.loadMeasurement(competitionId, it) } ?: return@mapNotNull null
            Entry(p.id, m.result.score, m.result.durationSec, m.acceptedAt)
        }
    private val params = competition?.measureParams(store.loadCalibration())
    private val session: MeasureSession? = params?.let { MeasureSession(it, source.sourceName, store.newTempWav()) }

    private val _ui = MutableStateFlow(
        MeasureUi(
            participantName = competition?.participants?.firstOrNull { it.id == participantId }?.name ?: "",
            maxDurationSec = competition?.maxDurationSec ?: 10,
            quietEndSec = competition?.quietEndSec ?: 2.0,
            state = if (session == null) MeasureState.Failed(IllegalStateException("Фон не виміряно")) else MeasureState.Ready,
        ),
    )
    val ui: StateFlow<MeasureUi> = _ui.asStateFlow()
    private var job: Job? = null
    private var saved = false

    private fun publish(s: MeasureState) {
        val place = (s as? MeasureState.Done)?.let { placeIfAccepted(it.result.score, it.result.durationSec, others) }
        _ui.value = _ui.value.copy(state = s, place = place)
    }

    private fun stopCollecting() { job?.cancel(); job = null }

    fun start() {
        val session = session ?: return
        if (job != null || session.state != MeasureState.Ready) return
        publish(session.start())
        job = viewModelScope.launch {
            try {
                source.chunks()
                    .onEach { publish(session.onChunk(it)) }
                    .takeWhile { session.state is MeasureState.Running }
                    .collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                publish(session.fail(t))
            } finally {
                job = null
            }
        }
    }

    fun stop() {
        val session = session ?: return
        if (session.state !is MeasureState.Running) return
        publish(session.stop())
        stopCollecting()
    }

    /** Скасовує поточний замір або відкидає готовий; сеанс знову Ready. */
    fun cancel() {
        val session = session ?: return
        stopCollecting()
        if (saved) return
        session.cancel()
        publish(session.state)
    }

    fun onActivityStopped() {
        if (session?.state is MeasureState.Running) cancel()
    }

    /** Зберігає один раз; повторні виклики ігноруються. */
    fun accept() {
        val session = session ?: return
        if (saved) return
        val pending = session.pending() ?: return
        saved = true
        _ui.value = _ui.value.copy(accepted = true)
        try {
            store.accept(competitionId, participantId, pending)
        } catch (t: Throwable) {
            saved = false
            pending.wavTemp.delete()
            session.fail(t)
            _ui.value = _ui.value.copy(accepted = false, state = session.state)
        }
    }

    override fun onCleared() {
        stopCollecting()
        if (!saved) session?.cancel()
    }
}
