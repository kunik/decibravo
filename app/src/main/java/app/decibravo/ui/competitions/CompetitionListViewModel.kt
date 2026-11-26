package app.decibravo.ui.competitions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.decibravo.DeciBravoApp
import app.decibravo.data.Competition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompetitionListViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as DeciBravoApp).store
    private val _items = MutableStateFlow<List<Competition>>(emptyList())
    val items: StateFlow<List<Competition>> = _items.asStateFlow()

    /** Скільки змагань не вдалося прочитати (файли лишилися на диску). */
    private val _unreadable = MutableStateFlow(0)
    val unreadable: StateFlow<Int> = _unreadable.asStateFlow()
    private val _calibrationUnreadable = MutableStateFlow(false)
    val calibrationUnreadable: StateFlow<Boolean> = _calibrationUnreadable.asStateFlow()

    init { refresh() }

    fun refresh() {
        _items.value = store.listCompetitions()
        _unreadable.value = store.unreadableCompetitionIds().size
        _calibrationUnreadable.value = store.calibrationUnreadable()
    }
}
