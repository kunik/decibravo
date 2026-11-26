package app.decibravo.ui.table

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import app.decibravo.DeciBravoApp
import app.decibravo.data.Competition
import app.decibravo.data.Measurement
import app.decibravo.scoring.Entry
import app.decibravo.scoring.TableRow
import app.decibravo.scoring.fmtScore
import app.decibravo.scoring.fmtSeconds
import app.decibravo.scoring.fmtStrength
import app.decibravo.scoring.rank
import app.decibravo.scoring.tableText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TableUi(val title: String, val subtitle: String, val ranked: List<RankedRow>, val waiting: List<WaitingRow>)
data class RankedRow(val participantId: String, val place: Int, val name: String, val detail: String, val score: String, val overloaded: Boolean)
data class WaitingRow(val participantId: String, val name: String)

private fun rankedOf(c: Competition, measurements: Map<String, Measurement>) =
    rank(c.participants.mapNotNull { p ->
        measurements[p.id]?.let { Entry(p.id, it.result.score, it.result.durationSec, it.acceptedAt) }
    })

fun buildTable(c: Competition, measurements: Map<String, Measurement>): TableUi {
    val ranked = rankedOf(c, measurements)
    val names = c.participants.associate { it.id to it.name }
    val rows = ranked.map { r ->
        val m = measurements.getValue(r.entry.participantId)
        RankedRow(
            r.entry.participantId, r.place, names.getValue(r.entry.participantId),
            "${fmtSeconds(m.result.durationSec)} с · сила ${fmtStrength(m.result.strength)}",
            fmtScore(m.result.score), m.result.overloaded,
        )
    }
    val waiting = c.participants.filter { measurements[it.id] == null }.map { WaitingRow(it.id, it.name) }
    return TableUi(
        c.name, "${rows.size} із ${c.participants.size} замірів · найдовший замір ${c.maxDurationSec} с", rows, waiting,
    )
}

fun copyText(c: Competition, measurements: Map<String, Measurement>): String {
    val names = c.participants.associate { it.id to it.name }
    val ranked = rankedOf(c, measurements).map { r ->
        TableRow(r.place, names.getValue(r.entry.participantId), measurements.getValue(r.entry.participantId).result)
    }
    val waiting = c.participants.filter { measurements[it.id] == null }.map { TableRow(null, it.name, null) }
    return tableText(c.name, ranked + waiting)
}

data class TableState(val ui: TableUi? = null, val floorMeasured: Boolean = false, val copy: String = "")

class TableViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val store = (app as DeciBravoApp).store
    private val id: String = handle.get<String>("id") ?: ""
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val c = store.loadCompetition(id) ?: return
        val ms = c.participants.mapNotNull { p ->
            p.measurementId?.let { mid -> store.loadMeasurement(id, mid)?.let { p.id to it } }
        }.toMap()
        _state.value = TableState(buildTable(c, ms), c.floorRms != null, copyText(c, ms))
    }
}
