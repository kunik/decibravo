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

    init { refresh() }

    fun refresh() { _items.value = store.listCompetitions() }
}
