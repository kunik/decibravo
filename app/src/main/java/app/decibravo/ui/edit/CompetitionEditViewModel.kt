package app.decibravo.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import app.decibravo.DeciBravoApp
import app.decibravo.data.Competition
import app.decibravo.data.MAX_DURATION_MAX
import app.decibravo.data.MAX_DURATION_MIN
import app.decibravo.data.Participant
import app.decibravo.data.QUIET_END_MAX
import app.decibravo.data.QUIET_END_MIN
import app.decibravo.data.QUIET_END_STEP
import app.decibravo.data.acceptedCount
import app.decibravo.data.isLocked
import app.decibravo.data.parseParticipantNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditForm(
    val name: String = "",
    val participantsText: String = "",
    val maxDurationSec: Int = 10,
    val quietEndSec: Double = 2.0,
) {
    fun plusMax() = copy(maxDurationSec = (maxDurationSec + 1).coerceAtMost(MAX_DURATION_MAX))
    fun minusMax() = copy(maxDurationSec = (maxDurationSec - 1).coerceAtLeast(MAX_DURATION_MIN))
    fun plusQuiet() = copy(quietEndSec = (quietEndSec + QUIET_END_STEP).coerceAtMost(QUIET_END_MAX))
    fun minusQuiet() = copy(quietEndSec = (quietEndSec - QUIET_END_STEP).coerceAtLeast(QUIET_END_MIN))
    val canSave: Boolean get() = name.trim().isNotEmpty()
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}

fun participantsCountText(n: Int): String = "$n " + plural(n, "учасник", "учасники", "учасників")

fun summaryLine(c: Competition): String {
    val accepted = c.acceptedCount()
    return if (accepted == 0) "Ще не почато · ${participantsCountText(c.participants.size)}"
    else "$accepted із ${c.participants.size} замірів"
}

data class EditUi(
    val id: String? = null,
    val form: EditForm = EditForm(),
    val locked: Boolean = false,
    val participants: List<Participant> = emptyList(),
    val floorRms: Double? = null,
    val floorMeasuredAt: Long? = null,
)

class CompetitionEditViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val store = (app as DeciBravoApp).store
    private val _state = MutableStateFlow(EditUi(id = handle.get<String>("id")))
    val state: StateFlow<EditUi> = _state.asStateFlow()

    init {
        _state.value.id?.let { id -> store.loadCompetition(id)?.let { applyLoaded(it, fillForm = true) } }
    }

    private fun applyLoaded(c: Competition, fillForm: Boolean) {
        _state.update {
            it.copy(
                id = c.id,
                form = if (fillForm) EditForm(c.name, c.participants.joinToString("\n") { p -> p.name }, c.maxDurationSec, c.quietEndSec) else it.form,
                locked = c.isLocked(),
                participants = c.participants,
                floorRms = c.floorRms,
                floorMeasuredAt = c.floorMeasuredAt,
            )
        }
    }

    /** Перечитує фон і учасників зі сховища (після повернення з екрана виміру фону). */
    fun refresh() {
        val id = _state.value.id ?: return
        store.loadCompetition(id)?.let { applyLoaded(it, fillForm = false) }
    }

    fun update(f: (EditForm) -> EditForm) = _state.update { it.copy(form = f(it.form)) }

    /** Зберігає форму; повертає id змагання. Для нового змагання створює його один раз. */
    fun save(): String? {
        val s = _state.value
        if (!s.form.canSave) return null
        val form = s.form
        val id = s.id
        if (id == null) {
            val c = store.createCompetition(form.name.trim(), parseParticipantNames(form.participantsText), form.maxDurationSec, form.quietEndSec)
            applyLoaded(c, fillForm = false)
            return c.id
        }
        val old = store.loadCompetition(id) ?: return null
        val updated = if (old.isLocked()) {
            old.copy(name = form.name.trim())
        } else {
            val names = parseParticipantNames(form.participantsText)
            old.copy(
                name = form.name.trim(), maxDurationSec = form.maxDurationSec, quietEndSec = form.quietEndSec,
                participants = names.mapIndexed { i, n -> Participant(old.participants.getOrNull(i)?.id ?: java.util.UUID.randomUUID().toString(), n) },
            )
        }
        store.saveCompetition(updated)
        applyLoaded(updated, fillForm = false)
        return id
    }

    fun rename(participantId: String, name: String) {
        val id = _state.value.id ?: return
        val n = name.trim()
        if (n.isEmpty()) return
        applyLoaded(store.renameParticipant(id, participantId, n), fillForm = false)
    }

    fun remove(participantId: String) {
        val id = _state.value.id ?: return
        applyLoaded(store.removeParticipant(id, participantId), fillForm = false)
    }

    fun addParticipants(text: String) {
        val id = _state.value.id ?: return
        val names = parseParticipantNames(text)
        if (names.isEmpty()) return
        applyLoaded(store.addParticipants(id, names), fillForm = false)
    }

    fun recalculate() {
        val id = _state.value.id ?: return
        store.recalculate(id)
        refresh()
    }

    fun deleteCompetition() {
        _state.value.id?.let { store.deleteCompetition(it) }
    }
}
