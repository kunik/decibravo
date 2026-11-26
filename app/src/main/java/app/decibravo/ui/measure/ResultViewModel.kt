package app.decibravo.ui.measure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import app.decibravo.DeciBravoApp
import app.decibravo.scoring.Entry
import app.decibravo.scoring.Result
import app.decibravo.scoring.rank

data class ResultUi(val name: String, val result: Result, val place: Int, val quietEndSec: Double)

class ResultViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val store = (app as DeciBravoApp).store
    private val competitionId: String = handle.get<String>("id") ?: ""
    private val participantId: String = handle.get<String>("participantId") ?: ""

    val ui: ResultUi? = load()
    var deleted = false
        private set

    private fun load(): ResultUi? {
        val c = store.loadCompetition(competitionId) ?: return null
        val ms = c.participants.mapNotNull { p ->
            p.measurementId?.let { mid -> store.loadMeasurement(competitionId, mid)?.let { p.id to it } }
        }.toMap()
        val mine = ms[participantId] ?: return null
        val ranked = rank(ms.map { (pid, m) -> Entry(pid, m.result.score, m.result.durationSec, m.acceptedAt) })
        val place = ranked.first { it.entry.participantId == participantId }.place
        val name = c.participants.first { it.id == participantId }.name
        return ResultUi(name, mine.result, place, c.quietEndSec)
    }

    fun delete() {
        if (deleted) return
        deleted = true
        store.deleteMeasurement(competitionId, participantId)
    }
}
